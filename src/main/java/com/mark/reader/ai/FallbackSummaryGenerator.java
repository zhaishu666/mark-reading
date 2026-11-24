package com.mark.reader.ai;

import com.mark.reader.exception.SummaryUnavailableException;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 带超时降级的摘要生成器（装饰器）。
 * <p>
 * 它自己也是 {@link SummaryGenerator}，所以对调用方完全透明：调用方拿到的就是「一个能生成摘要的东西」，
 * 既不知道背后有一次远程调用，也不知道失败时发生过降级。
 * <p>
 * 降级边界刻意收窄：只有超时和 {@link SummaryUnavailableException} 才转 fallback。
 * 其余异常（NPE、ClassCastException 等）一律原样抛出 —— 那些是代码缺陷，
 * 一旦被降级吞掉，应用的表现为「跑得好好的但结果是错的」，而且日志一片安静。
 * <p>
 * 超时的真实语义：{@code Future.get(timeout)} 到期后只是「不再等待」并走降级，
 * {@code cancel(true)} 只发出中断信号，底层 HTTP 请求未必真的被取消。
 * 要真正中断请求，需要在 HTTP 客户端上配置 connect / read timeout。
 */
public final class FallbackSummaryGenerator implements SummaryGenerator {

    /** 主生成器：正常情况下应由它出结果，通常是一次远程模型调用 */
    private final SummaryGenerator primary;

    /** 兜底生成器：primary 超时或不可用时使用，必须自己不能再失败 */
    private final SummaryGenerator fallback;

    /** 单次主调用的最长等待时间，到期即降级 */
    private final Duration timeout;

    /**
     * 执行主调用的线程池。
     * <p>
     * 必须是字段而不是局部变量：局部变量会导致每次调用都新建一个池，
     * 而池里的核心线程默认不回收，形成线程泄漏，最终 JVM 无法退出。
     * <p>
     * 单线程足够：本类只负责「限时等一个结果」，不做并发执行。
     * 线程设为 daemon，避免阻塞 JVM 退出。
     */
    private final ExecutorService executor;

    /**
     * @param primary  主生成器，通常是远程模型调用
     * @param fallback 兜底生成器，通常是纯本地实现
     * @param timeout  单次主调用的最长等待时间，必须为正数
     */
    public FallbackSummaryGenerator(SummaryGenerator primary, SummaryGenerator fallback, Duration timeout) {
        this.primary = Objects.requireNonNull(primary, "primary 不能为 null");
        this.fallback = Objects.requireNonNull(fallback, "fallback 不能为 null");
        Objects.requireNonNull(timeout, "timeout 不能为 null");
        // 零或负数的超时会让每次调用都立刻降级，属于配置错误，尽早报出来
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout 必须为正数: " + timeout);
        }
        this.timeout = timeout;
        // 线程命名便于排查：jstack 里一眼能看出这是摘要的主调用线程
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "summary-primary");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 生成摘要：限时调用 primary，失败或超时则降级到 fallback。
     *
     * @param text 待摘要的文本，由调用方负责拼装
     * @return 摘要结果，永不为 null（最坏情况返回 fallback 的内容）
     */
    @Override
    public String summarize(String text) {
        // submit 放在 try 之外：它只可能抛 RejectedExecutionException（线程池已关闭），
        // 那是生命周期错误，不是「服务不可用」，不该被降级掩盖
        Future<String> future = executor.submit(() -> primary.summarize(text));
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // 不再等待，走降级；cancel 只是发中断信号，不保证底层请求真的停下
            future.cancel(true);
            return fallback.summarize(text);
        } catch (ExecutionException e) {
            // ExecutionException 是包装层，真实异常必须拆包才能判断类型
            Throwable cause = e.getCause();
            if (cause instanceof SummaryUnavailableException) {
                // primary 明确报告「我不可用」，这是唯一允许降级的业务异常
                return fallback.summarize(text);
            }
            if (cause instanceof RuntimeException runtimeException) {
                // 空指针、类型转换等属于代码缺陷，原样抛出，不能降级
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                // Error 代表虚拟机层面的严重问题，同样不降级
                throw error;
            }
            // 兜住理论上不会出现的其他受检异常，标记为不可用但保留根因
            throw new SummaryUnavailableException("primary 抛出未预期异常", cause);
        } catch (InterruptedException e) {
            // 恢复中断标记，否则调用方之后再也感知不到「该停了」
            Thread.currentThread().interrupt();
            return fallback.summarize(text);
        }
    }
}
