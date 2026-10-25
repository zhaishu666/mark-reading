package com.mark.reader.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link Utf8} 的单元测试。
 * <p>
 * 这是整个编码修复的第一道防线：只有它判得准，「合法 UTF-8 就按 UTF-8 解」这条规则才成立。
 * 用例覆盖四类非法序列（位模式错误、字节截断、过长编码、超出范围与代理区），
 * 以及中文、emoji 等真实场景的合法序列。
 * <p>
 * 本类采用参数化写法：所有用例本质都是「给一组字节，判断是否合法」，
 * 因此用一张数据表统一表达，而不是为每组数据复制一个测试方法。
 */
@DisplayName("Utf8 字节序列校验")
class Utf8Test {

    /**
     * 校验给定字节序列的合法性是否符合预期。
     * <p>
     * 这是本类唯一的测试方法：合法与非法两类用例共用它，
     * 差别只体现在数据行的第三个参数（期望值）上。
     *
     * @param desc     用例描述，会出现在测试报告中，失败时据此定位是哪组数据
     * @param bytes    待校验的字节序列，null 表示输入为 null
     * @param expected 期望的判定结果，true 表示应判为合法 UTF-8
     */
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("utf8Case")
    void isValidShouldMatchExpectation(String desc, byte[] bytes, boolean expected) {
        // 实际结果必须等于期望结果；第三个参数是断言失败时输出的提示信息
        assertEquals(expected, Utf8.isValid(bytes), desc);
    }

    /**
     * 参数化测试的数据源，每行是一组「输入字节 → 期望是否合法」。
     * <p>
     * 用一张表代替原先的十余个 @Test 方法，好处是扩展只需加一行数据，
     * 不必再复制整个方法体。
     * <p>
     * 注意：本方法必须是 static，因为 JUnit 在创建测试实例之前就要调用它。
     *
     * @return 数据行流，每行依次为：用例描述、待校验字节、期望结果
     */
    static Stream<Arguments> utf8Case() {
        return Stream.of(
                // ===== 边界 =====
                // null 不可能是合法编码
                Arguments.of("null 判为非法", null, false),
                // 空数组对应空字符串，属于合法输入
                Arguments.of("空数组对应空串，合法", new byte[0], true),

                // ===== 合法：真实文本 =====
                // 普通英文与数字
                Arguments.of("纯 ASCII", "hello 2026".getBytes(StandardCharsets.US_ASCII), true),
                // 只含换行、制表符、回车等控制字符
                Arguments.of("仅含控制字符", new byte[]{'\t', '\n', '\r'}, true),
                // 「三体」是三个字节一个字的典型汉字
                Arguments.of("中文「三体」", "三体".getBytes(StandardCharsets.UTF_8), true),
                Arguments.of("中文「刘慈欣」", "刘慈欣".getBytes(StandardCharsets.UTF_8), true),
                // emoji 属于四字节序列，必须能通过
                Arguments.of("emoji 四字节", "📚".getBytes(StandardCharsets.UTF_8), true),
                // 中英混排
                Arguments.of("中英混排", "马克阅读 Mark Reading".getBytes(StandardCharsets.UTF_8), true),

                // ===== 合法：码点边界 =====
                // ED 9F BF 对应码点 0xD7FF，仍在代理区下界之前
                Arguments.of("U+D7FF 代理区下界之前",
                        new byte[]{(byte) 0xED, (byte) 0x9F, (byte) 0xBF}, true),
                // EE 80 80 对应码点 0xE000，位于代理区上界之后
                Arguments.of("U+E000 代理区上界之后",
                        new byte[]{(byte) 0xEE, (byte) 0x80, (byte) 0x80}, true),
                // F4 8F BF BF 对应码点 0x10FFFF，正好是 Unicode 上限
                Arguments.of("U+10FFFF 恰好是上限",
                        new byte[]{(byte) 0xF4, (byte) 0x8F, (byte) 0xBF, (byte) 0xBF}, true),

                // ===== 非法：GBK 字节 =====
                // 「三体」的 GBK 字节为 C8 FD CC E5，其中 FD 不是合法的延续字节
                Arguments.of("GBK「三体」", "三体".getBytes(Charset.forName("GBK")), false),
                // 「刘慈欣」的 GBK 字节同样非法
                Arguments.of("GBK「刘慈欣」", "刘慈欣".getBytes(Charset.forName("GBK")), false),

                // ===== 非法：字节截断 =====
                // 「三」的 UTF-8 编码是 E4 B8 89，这里只给前两个字节
                Arguments.of("三字节序列只给 2 字节",
                        new byte[]{(byte) 0xE4, (byte) 0xB8}, false),
                // 只给首字节同样非法
                Arguments.of("三字节序列只给 1 字节",
                        new byte[]{(byte) 0xE4}, false),
                // 四字节序列只给三个字节也非法
                Arguments.of("四字节序列只给 3 字节",
                        new byte[]{(byte) 0xF0, (byte) 0x9F, (byte) 0x93}, false),

                // ===== 非法：首字节 =====
                // 0x80 是延续字节，不能作为首字节
                Arguments.of("孤立延续字节 0x80", new byte[]{(byte) 0x80}, false),
                // 0xBF 同理，也是延续字节
                Arguments.of("孤立延续字节 0xBF", new byte[]{(byte) 0xBF}, false),
                // 0xC0 与 0xC1 属于过长编码的首字节，永远不该出现
                Arguments.of("过长编码首字节 C0 80",
                        new byte[]{(byte) 0xC0, (byte) 0x80}, false),
                Arguments.of("过长编码首字节 C1 BF",
                        new byte[]{(byte) 0xC1, (byte) 0xBF}, false),
                // 0xF5 及以上超出 Unicode 上限
                Arguments.of("0xF5 超出上限",
                        new byte[]{(byte) 0xF5, (byte) 0x80, (byte) 0x80, (byte) 0x80}, false),
                // 0xFF 完全非法
                Arguments.of("0xFF 完全非法", new byte[]{(byte) 0xFF}, false),

                // ===== 非法：延续字节位模式错误 =====
                // 第二个字节是 0x20（00100000），不满足 10xxxxxx
                Arguments.of("延续位写成 0x20",
                        new byte[]{(byte) 0xE4, (byte) 0x20, (byte) 0x89}, false),
                // 第三个字节是 0x7F，同样不满足
                Arguments.of("延续位写成 0x7F",
                        new byte[]{(byte) 0xE4, (byte) 0xB8, (byte) 0x7F}, false),

                // ===== 非法：过长编码 =====
                // E0 80 80 表示码点 0，本应用单字节 0x00 表示
                Arguments.of("三字节过长编码 E0 80 80",
                        new byte[]{(byte) 0xE0, (byte) 0x80, (byte) 0x80}, false),
                // F0 80 80 80 表示码点 0，属于四字节形式的过长编码
                Arguments.of("四字节过长编码 F0 80 80 80",
                        new byte[]{(byte) 0xF0, (byte) 0x80, (byte) 0x80, (byte) 0x80}, false),

                // ===== 非法：超范围与代理区 =====
                // F4 90 80 80 对应码点 0x110000，比上限 0x10FFFF 大 1
                Arguments.of("超上限 U+110000",
                        new byte[]{(byte) 0xF4, (byte) 0x90, (byte) 0x80, (byte) 0x80}, false),
                // ED A0 80 对应代理区码点 0xD800，标准 UTF-8 不允许
                Arguments.of("代理区 U+D800",
                        new byte[]{(byte) 0xED, (byte) 0xA0, (byte) 0x80}, false),
                // ED BF BF 对应 0xDFFF，同样落在代理区内
                Arguments.of("代理区 U+DFFF",
                        new byte[]{(byte) 0xED, (byte) 0xBF, (byte) 0xBF}, false),

                // ===== 非法：中段出现非法序列 =====
                // 前面是合法的 ASCII 与「三」，末尾插一个孤立的高位字节
                Arguments.of("中段出现 0xFF 整行非法",
                        new byte[]{'a', (byte) 0xE4, (byte) 0xB8, (byte) 0x89, (byte) 0xFF}, false)
        );
    }
}
