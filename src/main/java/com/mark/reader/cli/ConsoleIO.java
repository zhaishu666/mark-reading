package com.mark.reader.cli;

/**
 * 命令行输入输出接口。
 * <p>
 * 把「往哪里打印、从哪里读取」这件事抽象成接口，是为了让交互层的菜单逻辑可以被自动化测试覆盖：
 * 生产环境注入真实的控制台实现，测试时换成预设好输入、并记录输出的假实现，
 * 这样连「输入非法选项会怎样提示」这种分支也能断言，无需人工敲键盘。
 * <p>
 * 约定：所有读取方法在输入流已结束（例如管道关闭或测试数据用完）时返回 null，
 * 调用方靠 null 判断「没有更多输入了」并优雅退出，避免死循环。
 */
public interface ConsoleIO {

    /**
     * 输出一行文本。
     *
     * @param message 要输出的内容
     */
    void printLine(String message);

    /**
     * 先输出提示语，再读取一行输入。
     *
     * @param prompt 提示语，可为 null 表示不打印提示
     * @return 用户输入的一行文本（已去掉首尾空白），输入流结束返回 null
     */
    String readLine(String prompt);

    /**
     * 先输出提示语，再读取一个落在指定范围内的整数。
     * 输入不是数字或超出范围时会给出提示并要求重新输入，直到拿到合法值或输入流结束。
     *
     * @param prompt 提示语
     * @param min    允许的最小值（含）
     * @param max    允许的最大值（含）
     * @return 合法的整数值，输入流结束返回 null
     */
    Integer readInt(String prompt, int min, int max);
}
