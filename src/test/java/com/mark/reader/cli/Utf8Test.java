package com.mark.reader.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Utf8} 的单元测试。
 * <p>
 * 这是整个编码修复的第一道防线：只有它判得准，「合法 UTF-8 就按 UTF-8 解」这条规则才成立。
 * 用例覆盖四类非法序列（位模式错误、字节截断、过长编码、超出范围与代理区），
 * 以及中文、emoji 等真实场景的合法序列。
 */
@DisplayName("Utf8 字节序列校验")
class Utf8Test {

    /**
     * 校验 null 与空数组的边界行为：null 判为非法，空数组判为合法（对应空字符串）。
     */
    @Test
    @DisplayName("isValid：null 非法，空数组合法")
    void nullAndEmpty() {
        // null 不可能是合法编码
        assertFalse(Utf8.isValid(null));
        // 空数组对应空字符串，属于合法输入
        assertTrue(Utf8.isValid(new byte[0]));
    }

    /**
     * 校验纯 ASCII 内容是合法 UTF-8，这是最常见的情况。
     */
    @Test
    @DisplayName("isValid：纯 ASCII 合法")
    void asciiIsValid() {
        // 普通英文与数字
        assertTrue(Utf8.isValid("hello 2026".getBytes(StandardCharsets.US_ASCII)));
        // 只含换行与制表符
        assertTrue(Utf8.isValid(new byte[]{'\t', '\n', '\r'}));
    }

    /**
     * 校验常见中文、emoji 的 UTF-8 编码被正确识别为合法。
     */
    @Test
    @DisplayName("isValid：中文与 emoji 的 UTF-8 编码合法")
    void realUtf8IsValid() {
        // 「三体」是三个字节一个字的典型汉字
        assertTrue(Utf8.isValid("三体".getBytes(StandardCharsets.UTF_8)));
        // 「刘慈欣」同样
        assertTrue(Utf8.isValid("刘慈欣".getBytes(StandardCharsets.UTF_8)));
        // emoji 属于四字节序列，必须能通过
        assertTrue(Utf8.isValid("📚".getBytes(StandardCharsets.UTF_8)));
        // 中英混排
        assertTrue(Utf8.isValid("马克阅读 Mark Reading".getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 校验 GBK 编码的中文字节会被判为非法，这是整个修复能生效的关键前提。
     */
    @Test
    @DisplayName("isValid：GBK 编码的中文判为非法")
    void gbkBytesAreInvalid() {
        // 「三体」的 GBK 字节为 C8 FD CC E5，其中 FD 不是合法的延续字节
        byte[] gbkBytes = "三体".getBytes(Charset.forName("GBK"));
        // 必须识别为非法，否则就被误当成 UTF-8 解开
        assertFalse(Utf8.isValid(gbkBytes));
        // 「刘慈欣」的 GBK 字节同样非法
        assertFalse(Utf8.isValid("刘慈欣".getBytes(Charset.forName("GBK"))));
    }

    /**
     * 校验被截断的多字节序列会被判为非法，避免半个汉字被错误接受。
     */
    @Test
    @DisplayName("isValid：三字节序列被截断时非法")
    void truncatedSequenceIsInvalid() {
        // 「三」的 UTF-8 编码是 E4 B8 89，这里只给前两个字节
        assertFalse(Utf8.isValid(new byte[]{(byte) 0xE4, (byte) 0xB8}));
        // 只给首字节同样非法
        assertFalse(Utf8.isValid(new byte[]{(byte) 0xE4}));
        // 四字节序列只给三个字节也非法
        assertFalse(Utf8.isValid(new byte[]{(byte) 0xF0, (byte) 0x9F, (byte) 0x93}));
    }

    /**
     * 校验非法的首字节被拒绝，包括单独的延续字节与过长编码的首字节。
     */
    @Test
    @DisplayName("isValid：非法首字节被拒绝")
    void invalidLeadBytes() {
        // 0x80 是延续字节，不能作为首字节
        assertFalse(Utf8.isValid(new byte[]{(byte) 0x80}));
        // 0xBF 同理
        assertFalse(Utf8.isValid(new byte[]{(byte) 0xBF}));
        // 0xC0 与 0xC1 属于过长编码，永远不该出现
        assertFalse(Utf8.isValid(new byte[]{(byte) 0xC0, (byte) 0x80}));
        assertFalse(Utf8.isValid(new byte[]{(byte) 0xC1, (byte) 0xBF}));
        // 0xF5 及以上超出 Unicode 上限
        assertFalse(Utf8.isValid(new byte[]{(byte) 0xF5, (byte) 0x80, (byte) 0x80, (byte) 0x80}));
        // 0xFF 完全非法
        assertFalse(Utf8.isValid(new byte[]{(byte) 0xFF}));
    }

    /**
     * 校验延续字节位置出现非 10xxxxxx 的字节时被拒绝。
     */
    @Test
    @DisplayName("isValid：延续字节位模式错误时非法")
    void invalidContinuationByte() {
        // 第二个字节是 0x20（00100000），不满足 10xxxxxx
        assertFalse(Utf8.isValid(new byte[]{(byte) 0xE4, (byte) 0x20, (byte) 0x89}));
        // 第三个字节是 0x7F，同样不满足
        assertFalse(Utf8.isValid(new byte[]{(byte) 0xE4, (byte) 0xB8, (byte) 0x7F}));
    }

    /**
     * 校验过长编码被拒绝：用一个更长的形式表示本可以用短形式表示的码点。
     */
    @Test
    @DisplayName("isValid：过长编码被拒绝")
    void overlongEncodingIsInvalid() {
        // E0 80 80 表示码点 0，本应用单字节 0x00 表示
        assertFalse(Utf8.isValid(new byte[]{(byte) 0xE0, (byte) 0x80, (byte) 0x80}));
        // F0 80 80 80 表示码点 0，属于四字节形式的过长编码
        assertFalse(Utf8.isValid(new byte[]{(byte) 0xF0, (byte) 0x80, (byte) 0x80, (byte) 0x80}));
    }

    /**
     * 校验超出 Unicode 上限与落在代理区的码点被拒绝。
     */
    @Test
    @DisplayName("isValid：超范围与代理区码点被拒绝")
    void outOfRangeAndSurrogate() {
        // F4 90 80 80 对应码点 0x110000，比上限 0x10FFFF 大 1
        assertFalse(Utf8.isValid(new byte[]{(byte) 0xF4, (byte) 0x90, (byte) 0x80, (byte) 0x80}));
        // ED A0 80 对应代理区码点 0xD800，标准 UTF-8 不允许
        assertFalse(Utf8.isValid(new byte[]{(byte) 0xED, (byte) 0xA0, (byte) 0x80}));
        // ED BF BF 对应 0xDFFF，同样在代理区内
        assertFalse(Utf8.isValid(new byte[]{(byte) 0xED, (byte) 0xBF, (byte) 0xBF}));
    }

    /**
     * 校验合法的三字节与四字节边界值被接受。
     */
    @Test
    @DisplayName("isValid：合法边界值被接受")
    void validBoundaries() {
        // ED 9F BF 对应码点 0xD7FF，仍是合法字符（代理区下界之前）
        assertTrue(Utf8.isValid(new byte[]{(byte) 0xED, (byte) 0x9F, (byte) 0xBF}));
        // EE 80 80 对应码点 0xE000（代理区上界之后）
        assertTrue(Utf8.isValid(new byte[]{(byte) 0xEE, (byte) 0x80, (byte) 0x80}));
        // F4 8F BF BF 对应码点 0x10FFFF，正好是上限
        assertTrue(Utf8.isValid(new byte[]{(byte) 0xF4, (byte) 0x8F, (byte) 0xBF, (byte) 0xBF}));
    }

    /**
     * 校验中间夹杂非法序列时整行被判为非法，而不是只看开头。
     */
    @Test
    @DisplayName("isValid：中段出现非法序列时整行非法")
    void invalidInMiddleMakesWholeLineInvalid() {
        // 前面是合法的 ASCII 与汉字，末尾插一个孤立的高位字节
        byte[] bytes = new byte[]{
                'a', (byte) 0xE4, (byte) 0xB8, (byte) 0x89, (byte) 0xFF
        };
        // 整行必须判为非法
        assertFalse(Utf8.isValid(bytes));
    }
}
