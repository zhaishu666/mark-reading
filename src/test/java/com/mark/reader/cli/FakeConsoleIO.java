package com.mark.reader.cli;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * {@link ConsoleIO} 的测试替身。
 * <p>
 * 它把预设好的输入按顺序「喂」给被测代码，同时把全部输出记录到一个字符串里，
 * 于是命令行界面里那些原本需要人工敲键盘才能走到的分支（比如输入非法选项、输入超范围），
 * 都可以被自动化断言。行为严格遵循 {@link ConsoleIO} 的约定：输入耗尽时返回 null。
 * <p>
 * 该类位于测试源码目录，不会被编译进最终产物。
 */
public class FakeConsoleIO implements ConsoleIO {

    /** 预设的输入队列，按先进先出顺序消费 */
    private final Deque<String> pendingInputs = new ArrayDeque<>();

    /** 累积全部输出的缓冲区，供断言检查 */
    private final StringBuilder outputBuffer = new StringBuilder();

    /**
     * 使用预设输入构造。
     *
     * @param inputs 依次提供给被测代码的输入行
     */
    public FakeConsoleIO(String... inputs) {
        // 保存输入副本，避免外部数组被修改影响测试
        this.pendingInputs.addAll(Arrays.asList(inputs));
    }

    /**
     * 记录一行输出。
     *
     * @param message 输出内容
     */
    @Override
    public void printLine(String message) {
        // 追加内容并补一个换行，模拟真实的 println
        outputBuffer.append(message).append('\n');
    }

    /**
     * 记录提示语并取出下一条预设输入。
     *
     * @param prompt 提示语，可为 null
     * @return 下一条输入，已去首尾空白；没有更多输入时返回 null
     */
    @Override
    public String readLine(String prompt) {
        // 提示语同样计入输出，方便断言界面是否给出了正确的引导
        if (prompt != null) {
            outputBuffer.append(prompt);
        }
        // 取出并移除队首元素
        String line = pendingInputs.poll();
        // 输入耗尽时返回 null，与真实实现保持一致
        return (line == null) ? null : line.trim();
    }

    /**
     * 按与真实实现相同的规则解析整数：非法或越界时会输出提示并继续取下一个输入。
     *
     * @param prompt 提示语
     * @param min    允许的最小值（含）
     * @param max    允许的最大值（含）
     * @return 合法的整数值，输入耗尽时返回 null
     */
    @Override
    public Integer readInt(String prompt, int min, int max) {
        // 循环直到拿到合法输入或输入结束
        while (true) {
            // 读取一行输入
            String line = readLine(prompt);
            // 输入结束
            if (line == null) {
                return null;
            }
            // 尝试解析
            try {
                int value = Integer.parseInt(line);
                // 越界时提示并继续
                if (value < min || value > max) {
                    printLine("输入超出范围，请输入 " + min + " 到 " + max + " 之间的整数。");
                    continue;
                }
                // 合法值直接返回
                return value;
            } catch (NumberFormatException e) {
                // 非数字时提示并继续
                printLine("输入不是合法整数，请重新输入。");
            }
        }
    }

    /**
     * 取出累积的全部输出文本，供断言使用。
     *
     * @return 界面输出的完整内容
     */
    public String output() {
        // 返回缓冲区内容
        return outputBuffer.toString();
    }
}
