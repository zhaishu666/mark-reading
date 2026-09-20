package com.mark.reader.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ConsoleIOImpl} 的单元测试。
 * <p>
 * 覆盖两块内容：一是字符集解析的优先级（这是中文乱码问题的关键防线），
 * 二是读取方法的行为约定（去空白、重试、输入结束返回 null）。
 * 读写行为通过把内存中的字符串当作输入、把输出写进 StringWriter 来验证，
 * 不需要真的碰控制台。
 */
@DisplayName("ConsoleIOImpl 控制台实现")
class ConsoleIOImplTest {

    /** 测试专用的兜底属性名，避免干扰真实运行环境里的 stdin.encoding */
    private static final String TEST_PROPERTY = "mark.reader.test.encoding";

    /**
     * 每个用例结束后清理系统属性，防止污染同一 JVM 中的其他测试。
     */
    @AfterEach
    void clearProperties() {
        // 清理编码覆盖属性
        System.clearProperty(ConsoleIOImpl.ENCODING_PROPERTY);
        // 清理测试专用的兜底属性
        System.clearProperty(TEST_PROPERTY);
    }

    /**
     * 用给定的输入内容构造一个写入到内存的实现。
     *
     * @param input      预先准备好的输入文本
     * @param outputSink 承载输出的缓冲区
     * @return 控制了输入输出的控制台实现
     */
    private ConsoleIOImpl newIo(String input, StringWriter outputSink) {
        // 输入来自字符串，输出写进 StringWriter，二者都不碰真实控制台
        return new ConsoleIOImpl(new BufferedReader(new StringReader(input)),
                new PrintWriter(outputSink, true));
    }

    /**
     * 校验显式覆盖属性优先级最高，且输入输出两端都会采用它。
     */
    @Test
    @DisplayName("resolveCharset：显式覆盖属性优先于控制台属性")
    void overridePropertyWins() {
        // 同时设置覆盖属性与兜底属性，二者不同，用于验证优先级
        System.setProperty(ConsoleIOImpl.ENCODING_PROPERTY, "UTF-8");
        System.setProperty(TEST_PROPERTY, "GBK");
        // 输入端应使用覆盖属性指定的 UTF-8
        assertEquals(StandardCharsets.UTF_8, ConsoleIOImpl.resolveCharset("stdin.encoding"));
        // 输出端同样使用覆盖属性
        assertEquals(StandardCharsets.UTF_8, ConsoleIOImpl.resolveCharset("stdout.encoding"));
    }

    /**
     * 校验覆盖属性存在但编码名非法时立刻抛错，而不是悄悄退化成乱码。
     */
    @Test
    @DisplayName("resolveCharset：覆盖属性非法时抛出 IllegalArgumentException")
    void invalidOverrideThrows() {
        // 写一个不存在的编码名
        System.setProperty(ConsoleIOImpl.ENCODING_PROPERTY, "不存在的编码");
        // 必须显式报错，让用户马上发现配置写错了
        assertThrows(IllegalArgumentException.class,
                () -> ConsoleIOImpl.resolveCharset("stdin.encoding"));
    }

    /**
     * 校验没有覆盖属性时按传入的属性名解析控制台编码。
     */
    @Test
    @DisplayName("resolveCharset：无覆盖属性时按控制台属性解析")
    void fallsBackToConsoleProperty() {
        // 只设置兜底属性
        System.setProperty(TEST_PROPERTY, "GBK");
        // 应当解析出 GBK
        assertEquals(Charset.forName("GBK"), ConsoleIOImpl.resolveCharset(TEST_PROPERTY));
    }

    /**
     * 校验覆盖属性为空白时视为未指定，继续走兜底逻辑。
     */
    @Test
    @DisplayName("resolveCharset：覆盖属性为空白时回退到控制台属性")
    void blankOverrideFallsBack() {
        // 覆盖属性只填空格，等效于没填
        System.setProperty(ConsoleIOImpl.ENCODING_PROPERTY, "   ");
        // 兜底属性提供 GBK
        System.setProperty(TEST_PROPERTY, "GBK");
        // 结果应当是兜底属性提供的编码
        assertEquals(Charset.forName("GBK"), ConsoleIOImpl.resolveCharset(TEST_PROPERTY));
    }

    /**
     * 校验两个属性都不存在时兜底返回 JVM 默认字符集，绝不返回 null。
     */
    @Test
    @DisplayName("resolveCharset：属性均缺失时返回默认字符集")
    void fallsBackToDefaultCharset() {
        // 属性名不存在于系统属性中
        Charset resolved = ConsoleIOImpl.resolveCharset("mark.reader.absent.property");
        // 必须返回一个可用的字符集
        assertEquals(Charset.defaultCharset(), resolved);
    }

    /**
     * 校验 printLine 会输出内容并补上换行。
     */
    @Test
    @DisplayName("printLine：输出内容并换行")
    void printLineWritesLine() {
        // 准备输出缓冲区
        StringWriter sink = new StringWriter();
        // 构造实现并输出两行
        ConsoleIOImpl io = newIo("", sink);
        io.printLine("第一行");
        io.printLine("第二行");
        // 换行符由平台决定，这里按行切分后再比对内容
        String[] lines = sink.toString().split("\\R");
        // 应当得到两行内容
        assertEquals("第一行", lines[0]);
        assertEquals("第二行", lines[1]);
    }

    /**
     * 校验 readLine 会打印提示语并返回去掉首尾空白的输入。
     */
    @Test
    @DisplayName("readLine：打印提示语并裁剪输入空白")
    void readLineTrimsInput() {
        // 输入两侧带空格
        StringWriter sink = new StringWriter();
        // 构造实现并读取一行
        ConsoleIOImpl io = newIo("  活着  \n", sink);
        String line = io.readLine("请输入书名：");
        // 结果应当已去掉首尾空白
        assertEquals("活着", line);
        // 提示语应当出现在输出中
        assertTrue(sink.toString().contains("请输入书名："));
    }

    /**
     * 校验输入流结束时 readLine 返回 null，让调用方能够优雅退出。
     */
    @Test
    @DisplayName("readLine：输入流结束返回 null")
    void readLineReturnsNullOnEof() {
        // 没有任何输入内容
        ConsoleIOImpl io = newIo("", new StringWriter());
        // 应当返回 null 而不是空字符串
        assertNull(io.readLine("提示："));
    }

    /**
     * 校验 readInt 遇到非法输入会提示并继续读取，直到拿到合法值。
     */
    @Test
    @DisplayName("readInt：非法输入后重试直到合法")
    void readIntRetriesOnInvalidInput() {
        // 依次提供：非数字、越界数字、合法数字
        StringWriter sink = new StringWriter();
        ConsoleIOImpl io = newIo("abc\n99\n2\n", sink);
        // 读取 1 到 4 之间的整数
        Integer value = io.readInt("请选择操作：", 1, 4);
        // 应当拿到最后一次合法输入
        assertEquals(2, value);
        // 两次错误都应当给出提示
        assertTrue(sink.toString().contains("不是合法整数"));
        assertTrue(sink.toString().contains("输入超出范围"));
    }

    /**
     * 校验输入流在读取整数前就结束时会返回 null，不会陷入死循环。
     */
    @Test
    @DisplayName("readInt：输入流提前结束返回 null")
    void readIntReturnsNullOnEof() {
        // 输入为空
        ConsoleIOImpl io = newIo("", new StringWriter());
        // 应当返回 null
        assertNull(io.readInt("请选择操作：", 0, 4));
    }
}
