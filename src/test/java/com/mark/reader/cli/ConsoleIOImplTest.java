package com.mark.reader.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ConsoleIOImpl} 的单元测试。
 * <p>
 * 除了读写方法的基本约定（去空白、重试、输入结束返回 null），
 * 重点关注本次修复的核心：<b>输入解码的自动判定</b>。
 * 测试喂进去的是原始字节，因此可以精确模拟「终端送 UTF-8 但 JDK 报 GBK」这种真实故障场景。
 */
@DisplayName("ConsoleIOImpl 控制台实现")
class ConsoleIOImplTest {

    /** 测试专用的兜底属性名，避免干扰真实运行环境里的 stdin.encoding */
    private static final String TEST_PROPERTY = "mark.reader.test.encoding";

    /** 默认兜底编码，选 Latin-1 是为了在任何 JVM 上行为都确定 */
    private static final Charset FALLBACK = StandardCharsets.ISO_8859_1;

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
     * 用 UTF-8 文本作为输入构造一个写入到内存的实现。
     *
     * @param input      输入文本，按 UTF-8 转成字节
     * @param outputSink 承载输出的缓冲区
     * @return 控制了输入输出的实现
     */
    private ConsoleIOImpl newIo(String input, StringWriter outputSink) {
        // 把文本按 UTF-8 编码成字节，模拟现代终端送来的数据
        return newIo(input.getBytes(StandardCharsets.UTF_8), outputSink, FALLBACK);
    }

    /**
     * 用原始字节构造实现，便于精确控制输入内容。
     *
     * @param input      原始输入字节
     * @param outputSink 承载输出的缓冲区
     * @param fallback   非 UTF-8 字节所使用的兜底编码
     * @return 控制了输入输出的实现
     */
    private ConsoleIOImpl newIo(byte[] input, StringWriter outputSink, Charset fallback) {
        // 输入来自内存字节数组，输出写进 StringWriter，二者都不碰真实控制台
        return new ConsoleIOImpl(new ByteArrayInputStream(input), new PrintWriter(outputSink, true), fallback);
    }

    /**
     * 校验 printLine 会输出内容并换行。
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
     * 回归测试：终端送来 UTF-8 字节，即使兜底编码是别的编码，中文也必须被正确解码。
     * 这正是「三体变成涓変綋」这个缺陷的对应用例。
     */
    @Test
    @DisplayName("readLine：UTF-8 字节自动按 UTF-8 解码，不受兜底编码影响")
    void readLineAutoDetectsUtf8() {
        // 兜底编码故意设成一个会把 UTF-8 中文解坏的单字节编码
        StringWriter sink = new StringWriter();
        ConsoleIOImpl io = newIo("三体\n刘慈欣\n", sink);
        // 两行中文都必须完整还原
        assertEquals("三体", io.readLine("书名："));
        assertEquals("刘慈欣", io.readLine("作者："));
    }

    /**
     * 校验非 UTF-8 的字节序列会回退到兜底编码，保证 GBK 控制台依然可用。
     */
    @Test
    @DisplayName("readLine：非 UTF-8 字节回退到兜底编码")
    void readLineFallsBackForNonUtf8Bytes() {
        // 取 GBK 编码的字节，这类字节不是合法 UTF-8
        byte[] gbkBytes = "三体\n".getBytes(Charset.forName("GBK"));
        // 兜底编码也设成 GBK，模拟真实的 GBK 控制台
        StringWriter sink = new StringWriter();
        ConsoleIOImpl io = newIo(gbkBytes, sink, Charset.forName("GBK"));
        // 应当按兜底编码正确还原
        assertEquals("三体", io.readLine("书名："));
    }

    /**
     * 校验 GBK 字节在兜底编码不匹配时会如实走兜底分支，而不是被误当成 UTF-8。
     */
    @Test
    @DisplayName("readLine：GBK 字节不会被误判为 UTF-8")
    void gbkBytesAreNotTreatedAsUtf8() {
        // 取 GBK 字节，但兜底编码声明为 Latin-1
        byte[] gbkBytes = "三体\n".getBytes(Charset.forName("GBK"));
        ConsoleIOImpl io = newIo(gbkBytes, new StringWriter(), StandardCharsets.ISO_8859_1);
        // 得到的应当是按 Latin-1 解出的结果，而不是「三体」
        String decoded = io.readLine("书名：");
        assertNotEquals("三体", decoded);
        assertEquals(new String(gbkBytes, 0, gbkBytes.length - 1, StandardCharsets.ISO_8859_1), decoded);
    }

    /**
     * 校验显式覆盖属性优先级最高：即使字节是合法 UTF-8，也强制按指定编码解码。
     */
    @Test
    @DisplayName("readLine：显式覆盖属性强制指定编码")
    void overridePropertyForcesCharset() {
        // 强制按 GBK 解码
        Charset gbk = Charset.forName("GBK");
        System.setProperty(ConsoleIOImpl.ENCODING_PROPERTY, "GBK");
        // 输入是 UTF-8 字节
        byte[] utf8Bytes = "三体\n".getBytes(StandardCharsets.UTF_8);
        ConsoleIOImpl io = newIo(utf8Bytes, new StringWriter(), FALLBACK);
        // 结果应当等于「把这串 UTF-8 字节按 GBK 解读」的结果，即强制覆盖生效
        assertEquals(new String(utf8Bytes, 0, utf8Bytes.length - 1, gbk), io.readLine("书名："));
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
     * 校验空行被识别为空字符串，而不是被当成输入结束。
     */
    @Test
    @DisplayName("readLine：空行返回空字符串而非 null")
    void readLineHandlesEmptyLine() {
        // 只有一个换行符
        ConsoleIOImpl io = newIo("\n后续\n", new StringWriter());
        // 第一行应当是空字符串
        assertEquals("", io.readLine("提示："));
        // 第二行应当正常读到，说明空行没有被误判成流结束
        assertEquals("后续", io.readLine("提示："));
    }

    /**
     * 校验 CRLF 换行不会在行尾残留回车符。
     */
    @Test
    @DisplayName("readLine：CRLF 换行不残留回车符")
    void readLineHandlesCrlf() {
        // 模拟 Windows 文本文件的 CRLF 换行
        StringWriter sink = new StringWriter();
        ConsoleIOImpl io = newIo("活着\r\n余华\r\n", sink);
        // 两行都应当干净，不带 \r
        assertEquals("活着", io.readLine("书名："));
        assertEquals("余华", io.readLine("作者："));
    }

    /**
     * 校验单独 CR 也能作为行分隔符，兼容老式换行约定。
     */
    @Test
    @DisplayName("readLine：单独 CR 也能分行")
    void readLineHandlesLoneCr() {
        // 只用 CR 分隔
        ConsoleIOImpl io = newIo("甲\r乙\r", new StringWriter());
        // 两行应当分别读出
        assertEquals("甲", io.readLine("提示："));
        assertEquals("乙", io.readLine("提示："));
    }

    /**
     * 校验最后一行没有换行符收尾时依然能被读出。
     */
    @Test
    @DisplayName("readLine：末行无换行符也能读出")
    void readLineReadsLastLineWithoutNewline() {
        // 结尾没有换行
        ConsoleIOImpl io = newIo("活着", new StringWriter());
        // 应当正常返回，随后再到流结束
        assertEquals("活着", io.readLine("提示："));
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

    /**
     * 校验显式覆盖属性存在且合法时被正确解析。
     */
    @Test
    @DisplayName("overrideCharset：配置合法时返回对应字符集")
    void overrideCharsetParsesValidValue() {
        // 设置 UTF-8
        System.setProperty(ConsoleIOImpl.ENCODING_PROPERTY, "UTF-8");
        // 应当解析成功
        assertEquals(StandardCharsets.UTF_8, ConsoleIOImpl.overrideCharset());
    }

    /**
     * 校验覆盖属性为空白时视为未配置，返回 null。
     */
    @Test
    @DisplayName("overrideCharset：空白配置视为未配置")
    void overrideCharsetIgnoresBlank() {
        // 只填空格
        System.setProperty(ConsoleIOImpl.ENCODING_PROPERTY, "   ");
        // 应当视为没有覆盖
        assertNull(ConsoleIOImpl.overrideCharset());
    }

    /**
     * 校验覆盖属性存在但编码名非法时立刻抛错，而不是悄悄退化成乱码。
     */
    @Test
    @DisplayName("overrideCharset：编码名非法时抛出 IllegalArgumentException")
    void overrideCharsetRejectsInvalidValue() {
        // 写一个不存在的编码名
        System.setProperty(ConsoleIOImpl.ENCODING_PROPERTY, "不存在的编码");
        // 必须显式报错，让用户马上发现配置写错了
        assertThrows(IllegalArgumentException.class, ConsoleIOImpl::overrideCharset);
    }

    /**
     * 校验没有覆盖属性时按传入的属性名解析控制台编码。
     */
    @Test
    @DisplayName("consoleCharset：按属性名解析，缺失时返回默认字符集")
    void consoleCharsetFallsBackToDefault() {
        // 设置测试专用属性
        System.setProperty(TEST_PROPERTY, "GBK");
        // 应当解析出 GBK
        assertEquals(Charset.forName("GBK"), ConsoleIOImpl.consoleCharset(TEST_PROPERTY));
        // 属性不存在时返回 JVM 默认字符集
        assertEquals(Charset.defaultCharset(), ConsoleIOImpl.consoleCharset("mark.reader.absent.property"));
    }
}
