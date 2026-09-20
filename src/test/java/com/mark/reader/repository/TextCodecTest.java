package com.mark.reader.repository;

import com.mark.reader.exception.ValidationException;
import com.mark.reader.repository.file.TextCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TextCodec} 的单元测试。
 * <p>
 * 覆盖重点：转义与反转义必须互为逆运算（往返不丢信息）、
 * 特殊分支（null、末尾反斜杠、非法转义序列）必须有明确行为。
 */
@DisplayName("TextCodec 转义编解码")
class TextCodecTest {

    /**
     * 校验 escape 对 null 的处理：统一返回空字符串，而不是字面量 "null"。
     */
    @Test
    @DisplayName("escape：null 输入返回空字符串")
    void escapeNullReturnsEmptyString() {
        // null 应被归一成空字符串
        assertEquals("", TextCodec.escape(null));
    }

    /**
     * 校验 escape 对五种特殊字符的映射规则是否与设计文档一致。
     */
    @Test
    @DisplayName("escape：制表符/换行/回车/反斜杠/竖线分别转义")
    void escapeMapsSpecialCharacters() {
        // 制表符 -> \t
        assertEquals("\\t", TextCodec.escape("\t"));
        // 换行 -> \n
        assertEquals("\\n", TextCodec.escape("\n"));
        // 回车 -> \r
        assertEquals("\\r", TextCodec.escape("\r"));
        // 单个反斜杠 -> 两个反斜杠
        assertEquals("\\\\", TextCodec.escape("\\"));
        // 竖线 -> \p，以保护标签分隔符
        assertEquals("\\p", TextCodec.escape("|"));
    }

    /**
     * 校验普通中英文内容经过 escape 后保持不变，避免过度转义。
     */
    @Test
    @DisplayName("escape：普通文本原样保留")
    void escapeKeepsPlainTextUntouched() {
        // 一句不含特殊字符的中文不应被改动
        assertEquals("人是为活着本身而活着", TextCodec.escape("人是为活着本身而活着"));
        // 纯英文字母与数字同样保持原样
        assertEquals("book-2026", TextCodec.escape("book-2026"));
    }

    /**
     * 校验往返性质：对一段包含全部特殊字符的文本，先转义再反转义应回到原值。
     */
    @Test
    @DisplayName("escape + unescape：含全部特殊字符的文本往返一致")
    void escapeThenUnescapeRoundTrip() {
        // 构造一段把五种特殊字符都包含进去的文本
        String raw = "第一行\t带制表符\n第二行\\带反斜杠\r标签|竖线";
        // 先转义再反转义
        String restored = TextCodec.unescape(TextCodec.escape(raw));
        // 结果必须与原文完全一致
        assertEquals(raw, restored);
    }

    /**
     * 校验 unescape 对 null 的处理：返回空字符串。
     */
    @Test
    @DisplayName("unescape：null 输入返回空字符串")
    void unescapeNullReturnsEmptyString() {
        // null 应被归一成空字符串
        assertEquals("", TextCodec.unescape(null));
    }

    /**
     * 校验字段以单个反斜杠结尾时抛出校验异常，而不是静默返回错误内容。
     */
    @Test
    @DisplayName("unescape：末尾单个反斜杠抛 ValidationException")
    void unescapeTrailingBackslashThrows() {
        // 尾部悬挂的引导符属于非法数据，必须报错
        assertThrows(ValidationException.class, () -> TextCodec.unescape("abc\\"));
    }

    /**
     * 校验无法识别的转义序列会抛出校验异常，防止脏数据被悄悄吞掉。
     */
    @Test
    @DisplayName("unescape：非法转义序列抛 ValidationException")
    void unescapeUnknownEscapeThrows() {
        // \x 不在约定的五种转义之内
        assertThrows(ValidationException.class, () -> TextCodec.unescape("a\\x b"));
    }

    /**
     * 校验 joinLine 与 splitLine 的往返一致性，包含空字段与字段内的制表符。
     */
    @Test
    @DisplayName("joinLine + splitLine：字段往返一致")
    void joinThenSplitRoundTrip() {
        // 构造包含空字段、字段内含制表符的字段列表（Arrays.asList 允许 null 元素）
        List<String> fields = Arrays.asList("1", "活着", "余华", "", "含\t制表符");
        // 先拼接成一行再切分回来
        List<String> restored = TextCodec.splitLine(TextCodec.joinLine(fields));
        // 切分结果必须与原始字段列表完全相同
        assertEquals(fields, restored);
    }

    /**
     * 校验拼接后的行内不含真实的制表符，确保一行的完整性。
     */
    @Test
    @DisplayName("joinLine：字段内的制表符被替换，不会撑破一行")
    void joinLineNeverEmitsRawTab() {
        // 用只含一个字段的列表，隔离出「字段内容含制表符」这一种情况
        String line = TextCodec.joinLine(Collections.singletonList("a\tb"));
        // 拼接结果里不应出现真实制表符
        assertEquals(-1, line.indexOf(TextCodec.FIELD_SEPARATOR));
        // 但反转义之后制表符应当被还原
        assertEquals("a\tb", TextCodec.splitLine(line).get(0));
    }

    /**
     * 校验 splitLine 会保留行尾的空字段，这是「字段个数固定」的前提。
     */
    @Test
    @DisplayName("splitLine：保留空字段与行尾空字段")
    void splitLineKeepsTrailingEmptyField() {
        // 一行四个字段，最后一个为空
        List<String> fields = TextCodec.splitLine("a\tb\tc\t");
        // 字段个数必须是 4 而不是 3
        assertEquals(4, fields.size());
        // 最后一个字段应为空字符串
        assertEquals("", fields.get(3));
    }

    /**
     * 校验 splitLine 对 null 的处理：返回空列表。
     */
    @Test
    @DisplayName("splitLine：null 返回空列表")
    void splitLineNullReturnsEmptyList() {
        // null 输入不应抛异常，而是返回空列表
        assertTrue(TextCodec.splitLine(null).isEmpty());
    }

    /**
     * 校验标签编码解码的往返一致性，重点覆盖标签内部含竖线与空格的情况。
     */
    @Test
    @DisplayName("encodeTags + decodeTags：含竖线的标签往返一致")
    void tagsRoundTripWithPipeInside() {
        // 构造三个标签，其中第二个故意包含标签分隔符竖线
        List<String> tags = Arrays.asList("文学", "人生|感悟", " 带空格 ");
        // 编码后再解码
        List<String> restored = TextCodec.decodeTags(TextCodec.encodeTags(tags));
        // 结果必须与原始标签列表一致（含空格也不应被裁剪）
        assertEquals(tags, restored);
    }

    /**
     * 校验空标签列表编码成空字符串，方便写进 TSV 的空字段。
     */
    @Test
    @DisplayName("encodeTags：空列表与 null 编码为空字符串")
    void encodeTagsForEmptyInput() {
        // 空列表编码结果为空字符串
        assertEquals("", TextCodec.encodeTags(new ArrayList<>()));
        // null 同样编码为空字符串
        assertEquals("", TextCodec.encodeTags(null));
    }

    /**
     * 校验空字符串解码成空标签列表。
     */
    @Test
    @DisplayName("decodeTags：空字符串与 null 解码为空列表")
    void decodeTagsForEmptyInput() {
        // 空字符串解码后应得到空列表
        assertTrue(TextCodec.decodeTags("").isEmpty());
        // null 同样解码为空列表
        assertTrue(TextCodec.decodeTags(null).isEmpty());
    }
}
