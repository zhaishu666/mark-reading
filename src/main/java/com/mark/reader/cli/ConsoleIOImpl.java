package com.mark.reader.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;

/**
 * {@link ConsoleIO} 的标准控制台实现，基于 {@code System.in} 与 {@code System.out}。
 * <p>
 * 这里特意处理了字符集问题：中文书摘一旦被用错误编码读写就会变成乱码。
 * 默认策略是「跟随控制台」：JDK 19 之后提供了 {@code stdin.encoding} 与 {@code stdout.encoding}
 * 两个系统属性，它们描述的是控制台当前实际使用的编码（Windows 中文环境下通常是 GBK）。
 * 按它构造读写器，手动敲入的中文就能正确解析，输出也能被终端正确显示。
 * <p>
 * 但「跟随控制台」并非在所有场景下都对：例如把一份 UTF-8 文本文件重定向进程序
 * （{@code java -jar app.jar < input.txt}），控制台编码仍是 GBK，输入却是 UTF-8，就会解析错。
 * 因此额外提供了覆盖开关：启动时加上
 * {@code -Dmark.reader.encoding=UTF-8} 即可强制输入输出统一使用 UTF-8。
 * 若在 cmd 里先执行 {@code chcp 65001} 切换代码页，也可以达到同样效果。
 */
public class ConsoleIOImpl implements ConsoleIO {

    /** 用于强制指定控制台编码的系统属性名 */
    public static final String ENCODING_PROPERTY = "mark.reader.encoding";

    /** 输入读取器 */
    private final BufferedReader reader;

    /** 输出写入器，构造时开启自动刷新，保证提示语立即出现 */
    private final PrintWriter writer;

    /**
     * 使用标准输入输出构造，并自动探测控制台编码。
     */
    public ConsoleIOImpl() {
        // 解析输入端的字符集
        Charset stdinCharset = resolveCharset("stdin.encoding");
        // 解析输出端的字符集
        Charset stdoutCharset = resolveCharset("stdout.encoding");
        // 用探测到的编码包装标准输入
        this.reader = new BufferedReader(new InputStreamReader(System.in, stdinCharset));
        // 用探测到的编码包装标准输出，第二个参数 true 表示自动刷新
        this.writer = new PrintWriter(new OutputStreamWriter(System.out, stdoutCharset), true);
    }

    /**
     * 使用指定的读写器构造，便于测试或嵌入到其他输入输出环境。
     *
     * @param reader 输入读取器
     * @param writer 输出写入器
     */
    public ConsoleIOImpl(BufferedReader reader, PrintWriter writer) {
        // 直接使用调用方提供的读取器
        this.reader = reader;
        // 直接使用调用方提供的写入器
        this.writer = writer;
    }

    /**
     * 输出一行文本。
     *
     * @param message 要输出的内容
     */
    @Override
    public void printLine(String message) {
        // println 会触发自动刷新，内容立即送达控制台
        writer.println(message);
    }

    /**
     * 打印提示语后读取一行输入。
     *
     * @param prompt 提示语，可为 null
     * @return 去掉首尾空白的一行输入，输入流结束返回 null
     */
    @Override
    public String readLine(String prompt) {
        // 有提示语就先输出，且不换行，让用户在同一行输入
        if (prompt != null) {
            writer.print(prompt);
            // 立即刷新，否则在部分终端里提示语会迟迟不出现
            writer.flush();
        }
        try {
            // 读取一行；readLine 在流结束时返回 null
            String line = reader.readLine();
            // 空输入或流结束都原样返回，由调用方决定怎么处理
            return (line == null) ? null : line.trim();
        } catch (IOException e) {
            // 控制台读取失败属于环境问题，抛出并说明原因
            throw new UncheckedIOException("读取控制台输入失败", e);
        }
    }

    /**
     * 读取一个指定范围内的整数，非法输入会重新询问。
     *
     * @param prompt 提示语
     * @param min    允许的最小值（含）
     * @param max    允许的最大值（含）
     * @return 合法的整数值，输入流结束返回 null
     */
    @Override
    public Integer readInt(String prompt, int min, int max) {
        // 循环直到拿到合法输入或输入结束
        while (true) {
            // 读取一行原始输入
            String line = readLine(prompt);
            // 输入结束，向上返回 null 让调用方退出
            if (line == null) {
                return null;
            }
            // 尝试把输入解析成整数
            try {
                int value = Integer.parseInt(line);
                // 超出范围时给出提示并重新询问
                if (value < min || value > max) {
                    printLine("输入超出范围，请输入 " + min + " 到 " + max + " 之间的整数。");
                    continue;
                }
                // 合法输入直接返回
                return value;
            } catch (NumberFormatException e) {
                // 不是数字时给出提示并重新询问
                printLine("输入不是合法整数，请重新输入。");
            }
        }
    }

    /**
     * 解析控制台应使用的字符集。
     * 优先级为：显式覆盖属性 &gt; JDK 提供的控制台编码属性 &gt; JVM 默认字符集。
     * 声明为包级可见是为了让单元测试能够直接验证这段优先级逻辑。
     *
     * @param propertyName 兜底使用的属性名，如 stdin.encoding
     * @return 解析出的字符集
     * @throws IllegalArgumentException 显式覆盖属性存在但指定的编码不受支持时抛出
     */
    static Charset resolveCharset(String propertyName) {
        // 第一优先级：用户显式指定的编码，用于管道、重定向等控制台编码不匹配的场景
        String override = System.getProperty(ENCODING_PROPERTY);
        // 属性存在时按它强制统一输入输出编码
        if (override != null && !override.isBlank()) {
            try {
                // 按名称查找字符集
                return Charset.forName(override.trim());
            } catch (Exception e) {
                // 这是用户显式写错的配置，必须立刻报错而不是悄悄回退，否则会变成难查的乱码
                throw new IllegalArgumentException("系统属性 " + ENCODING_PROPERTY
                        + " 指定的编码不受支持：" + override, e);
            }
        }
        // 第二优先级：JDK 记录的控制台编码，低版本 JDK 上该属性可能不存在
        String name = System.getProperty(propertyName);
        // 属性存在时尝试解析
        if (name != null && !name.isBlank()) {
            try {
                // 按名称查找字符集
                return Charset.forName(name.trim());
            } catch (Exception ignored) {
                // 名称非法时静默回退，避免因为编码探测失败而无法启动程序
            }
        }
        // 兜底使用 JVM 默认字符集
        return Charset.defaultCharset();
    }
}
