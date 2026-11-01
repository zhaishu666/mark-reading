package com.mark.reader.repository;

import com.mark.reader.exception.ValidationException;
import com.mark.reader.repository.file.TextCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TextCodec} 的单元测试。
 * <p>
 * 覆盖重点：转义与反转义必须互为逆运算（往返不丢信息）、
 * 特殊分支（null、末尾反斜杠、非法转义序列）必须有明确行为。
 * <p>
 * escape 的字符映射采用参数化写法：五种特殊字符与两类普通文本本质上是
 * 同一段断言逻辑，只是输入与期望值不同，因此用一张数据表统一表达。
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
     * 校验 escape 对各类字符的映射结果是否符合约定。
     * <p>
     * 特殊字符的转义与普通文本的「原样保留」共用同一段断言逻辑，
     * 差别只体现在数据行的输入与期望值上。
     *
     * @param desc     用例描述，断言失败时作为提示信息输出
     * @param raw      待转义的原始字符串
     * @param expected 期望的转义结果
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("escapeCases")
    void escapeShouldProduceExpected(String desc, String raw, String expected) {
        // 实际转义结果必须等于期望值；desc 用于定位是哪一组数据出错
        assertEquals(expected, TextCodec.escape(raw), desc);
    }

    /**
     * 参数化测试的数据源，每行是一组「原始文本 → 期望的转义结果」。
     * <p>
     * 前五行逐一覆盖五种特殊字符的映射规则，后两行验证普通文本不会被过度转义。
     * 五种映射是独立的 switch 分支，必须逐条精确断言，不能用往返测试替代
     * ——往返只能证明前后自洽，证明不了映射结果本身就是对的。
     * <p>
     * 注意：本方法必须是 static，JUnit 在创建测试实例之前就要取数据。
     *
     * @return 数据行流，每行依次为：用例描述、原始文本、期望结果
     */
    static Stream<Arguments> escapeCases() {
        return Stream.of(
                // 制表符是字段分隔符，必须转义，否则会撑破一行
                Arguments.of("制表符转义", "\t", "\\t"),
                // 换行是行分隔符，转义为 \n
                Arguments.of("换行转义", "\n", "\\n"),
                // 回车单独出现时同样会破坏行结构，转义为 \r
                Arguments.of("回车转义", "\r", "\\r"),
                // 反斜杠是引导符，自身必须最先转义，避免与后续转义序列冲突
                Arguments.of("反斜杠转义", "\\", "\\\\"),
                // 竖线是标签分隔符，转义为 \p 以保护标签内部的竖线
                Arguments.of("竖线转义", "|", "\\p"),
                // 普通中文不应被改动，验证没有过度转义
                Arguments.of("中文原样保留", "人是为活着本身而活着", "人是为活着本身而活着"),
                // 普通英文、连字符（不含特殊字符）同样原样保留
                Arguments.of("英文原样保留", "book-2026", "book-2026")
        );
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
