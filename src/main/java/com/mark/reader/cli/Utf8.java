package com.mark.reader.cli;

/**
 * UTF-8 字节序列校验工具。
 * <p>
 * 用途很具体：控制台到底用什么编码，程序在运行前是猜不准的。
 * Windows 中文环境下 JDK 会把 {@code stdin.encoding} 报成 GBK，
 * 但 IDE 的运行窗口、Git Bash、管道重定向送来的却是 UTF-8 字节，
 * 结果是中文被按 GBK 解开，变成「涓変綋」这种乱码并写进数据文件。
 * <p>
 * 与其猜环境，不如直接看字节：合法的 UTF-8 有非常严格的位模式，
 * 随便一段 GBK 中文几乎不可能同时满足。因此可以先用本类做一次校验，
 * 能通过就按 UTF-8 解，通不过再退回控制台本地编码。
 * <p>
 * 校验规则依据 RFC 3629：
 * <pre>
 *   1 字节   0xxxxxxx                                      码点 0x00 ~ 0x7F
 *   2 字节   110xxxxx 10xxxxxx                             码点 0x80 ~ 0x7FF
 *   3 字节   1110xxxx 10xxxxxx 10xxxxxx                    码点 0x800 ~ 0xFFFF
 *   4 字节   11110xxx 10xxxxxx 10xxxxxx 10xxxxxx           码点 0x10000 ~ 0x10FFFF
 * </pre>
 * 除了位模式，还要额外拒绝三类非法序列：过长编码、超出 Unicode 上限、落在代理区（0xD800~0xDFFF）。
 * 本类是纯函数：同样的输入永远得到同样的输出，不碰任何外部状态。
 */
public final class Utf8 {

    /** 各长度首字节所携带的有效数据位掩码，下标即「后续延续字节的个数」 */
    private static final int[] LEAD_DATA_MASK = {0x00, 0x1F, 0x0F, 0x07};

    /** Unicode 允许的最大码点 */
    private static final int MAX_CODE_POINT = 0x10FFFF;

    /** 代理区下界，UTF-8 中不允许出现单独的代理码点 */
    private static final int SURROGATE_MIN = 0xD800;

    /** 代理区上界 */
    private static final int SURROGATE_MAX = 0xDFFF;

    /**
     * 私有构造函数，防止工具类被实例化。
     */
    private Utf8() {
        // 工具类只提供静态方法，不允许创建实例
    }

    /**
     * 判断一段字节序列是否是合法的 UTF-8 编码。
     * 空数组视为合法（对应空字符串）。
     *
     * @param bytes 待校验的字节序列，允许为 null
     * @return true 表示整段字节都符合 UTF-8 规范
     */
    public static boolean isValid(byte[] bytes) {
        // null 不可能是合法编码
        if (bytes == null) {
            return false;
        }
        // 逐字节向前扫描
        int index = 0;
        while (index < bytes.length) {
            // 取当前字节的无符号值
            int lead = bytes[index] & 0xFF;
            // ASCII 区间：单字节字符，直接跳过
            if (lead < 0x80) {
                index++;
                continue;
            }
            // 根据首字节判断这个字符总共还应该有几个延续字节
            int extraBytes;
            // 该长度允许的最小码点，用来识别「过长编码」这种非法写法
            int minCodePoint;
            if (lead >= 0xC2 && lead <= 0xDF) {
                // 110xxxxx：两字节字符，首字节上限到 0xDF
                extraBytes = 1;
                minCodePoint = 0x80;
            } else if (lead >= 0xE0 && lead <= 0xEF) {
                // 1110xxxx：三字节字符，覆盖常用汉字
                extraBytes = 2;
                minCodePoint = 0x800;
            } else if (lead >= 0xF0 && lead <= 0xF4) {
                // 11110xxx：四字节字符，覆盖 emoji 与增补平面
                extraBytes = 3;
                minCodePoint = 0x10000;
            } else {
                // 0x80~0xC1 是延续字节或过长编码的首字节，0xF5~0xFF 超出范围，全部非法
                return false;
            }
            // 后续字节不够，说明序列被截断
            if (index + extraBytes >= bytes.length) {
                return false;
            }
            // 取出首字节承载的数据位
            int codePoint = lead & LEAD_DATA_MASK[extraBytes];
            // 逐个拼接延续字节的数据位
            for (int offset = 1; offset <= extraBytes; offset++) {
                int continuation = bytes[index + offset] & 0xFF;
                // 延续字节必须是 10xxxxxx，否则位模式不合法
                if ((continuation & 0xC0) != 0x80) {
                    return false;
                }
                // 每读入一个延续字节，码点左移 6 位并补上新拿到的 6 位
                codePoint = (codePoint << 6) | (continuation & 0x3F);
            }
            // 用了多于必需的字节来表示一个小码点，属于过长编码，会带来安全隐患
            if (codePoint < minCodePoint) {
                return false;
            }
            // 超出 Unicode 码点上限
            if (codePoint > MAX_CODE_POINT) {
                return false;
            }
            // 单独的代理码点在标准 UTF-8 中不允许出现
            if (codePoint >= SURROGATE_MIN && codePoint <= SURROGATE_MAX) {
                return false;
            }
            // 当前字符校验通过，跳到下一个字符
            index += extraBytes + 1;
        }
        // 整段扫描完毕且未发现问题
        return true;
    }
}
