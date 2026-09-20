package com.mark.reader.repository.file;

import com.mark.reader.exception.ValidationException;

import java.util.ArrayList;
import java.util.List;

/**
 * TSV 文本编解码工具。
 * <p>
 * 数据文件采用「一行一条记录、字段以制表符分隔」的 TSV 格式，这种格式最大的风险是
 * 字段内容本身就含有制表符或换行，会把一条记录撑成两行、把一个字段切成两个。
 * 本类通过转义解决这个问题，转义规则如下（反斜杠为引导符）：
 * <pre>
 *   反斜杠 \        ->  \\
 *   制表符 TAB      ->  \t
 *   换行 LF         ->  \n
 *   回车 CR         ->  \r
 *   竖线 |          ->  \p    （因为标签列表用竖线做分隔符，需要保护标签内的竖线）
 * </pre>
 * 本类是纯函数工具：编码与解码互为逆运算，同样的输入永远得到同样的输出，因此非常适合单元测试。
 */
public final class TextCodec {

    /** 字段分隔符：制表符，一条记录内各字段用它隔开 */
    public static final char FIELD_SEPARATOR = '\t';

    /** 标签分隔符：竖线，一个标签字段内多个标签用它隔开 */
    public static final char TAG_SEPARATOR = '|';

    /** 转义引导符：反斜杠 */
    private static final char ESCAPE_CHAR = '\\';

    /**
     * 私有构造函数，防止工具类被实例化。
     */
    private TextCodec() {
        // 工具类只提供静态方法，不允许创建实例
    }

    /**
     * 把原始文本转义成可安全写入一行 TSV 的形式。
     * 传入 null 时统一返回空字符串，避免把 "null" 四个字母写进文件。
     *
     * @param raw 原始文本，允许为 null
     * @return 转义后的文本，保证不含制表符、换行与回车
     */
    public static String escape(String raw) {
        // 空值归一成空字符串，简化后续处理
        if (raw == null) {
            return "";
        }
        // 预估容量，避免 StringBuilder 反复扩容
        StringBuilder sb = new StringBuilder(raw.length() + 8);
        // 逐字符扫描，把特殊字符替换成转义序列
        for (int i = 0; i < raw.length(); i++) {
            // 取出当前位置的字符
            char c = raw.charAt(i);
            // 按转义规则逐项映射
            switch (c) {
                // 反斜杠是引导符，自身必须最先转义，否则会与后续序列冲突
                case ESCAPE_CHAR -> sb.append("\\\\");
                // 制表符会破坏字段分隔，转义为 \t
                case FIELD_SEPARATOR -> sb.append("\\t");
                // 换行会破坏行分隔，转义为 \n
                case '\n' -> sb.append("\\n");
                // 回车同样会破坏行分隔，转义为 \r
                case '\r' -> sb.append("\\r");
                // 竖线是标签分隔符，转义为 \p 以保护标签内的竖线
                case TAG_SEPARATOR -> sb.append("\\p");
                // 其余字符原样输出
                default -> sb.append(c);
            }
        }
        // 返回转义结果
        return sb.toString();
    }

    /**
     * 把转义后的文本还原成原始文本，是 {@link #escape(String)} 的逆运算。
     *
     * @param encoded 转义后的文本，允许为 null
     * @return 还原后的原始文本
     * @throws ValidationException 当反斜杠开头但不是合法转义序列，或反斜杠位于末尾时抛出
     */
    public static String unescape(String encoded) {
        // 空值归一成空字符串
        if (encoded == null) {
            return "";
        }
        // 用于累积还原结果
        StringBuilder sb = new StringBuilder(encoded.length());
        // 逐字符扫描，遇到反斜杠就与后一个字符组合解读
        for (int i = 0; i < encoded.length(); i++) {
            // 取出当前位置的字符
            char c = encoded.charAt(i);
            // 普通字符直接追加
            if (c != ESCAPE_CHAR) {
                sb.append(c);
                continue;
            }
            // 已到末尾却还有引导符，说明文件被截断或手工改坏了
            if (i + 1 >= encoded.length()) {
                throw new ValidationException("转义序列不完整：字段以单个反斜杠结尾 -> " + encoded);
            }
            // 前移一位取出被转义的目标字符
            char next = encoded.charAt(++i);
            // 按转义规则还原
            switch (next) {
                // \\ 还原为反斜杠
                case ESCAPE_CHAR -> sb.append(ESCAPE_CHAR);
                // \t 还原为制表符
                case 't' -> sb.append(FIELD_SEPARATOR);
                // \n 还原为换行
                case 'n' -> sb.append('\n');
                // \r 还原为回车
                case 'r' -> sb.append('\r');
                // \p 还原为竖线
                case 'p' -> sb.append(TAG_SEPARATOR);
                // 其他组合都是非法序列，直接报错而不是静默吞掉
                default -> throw new ValidationException("无法识别的转义序列：\\" + next + " -> " + encoded);
            }
        }
        // 返回还原结果
        return sb.toString();
    }

    /**
     * 把若干字段拼成一行 TSV 文本，每个字段先转义再用制表符连接。
     *
     * @param fields 字段列表，元素允许为 null（按空字符串处理）
     * @return 一行文本，不含换行符
     */
    public static String joinLine(List<String> fields) {
        // 用于累积整行内容
        StringBuilder sb = new StringBuilder();
        // 依次追加每个字段
        for (int i = 0; i < fields.size(); i++) {
            // 除第一个字段外，前面都要补一个字段分隔符
            if (i > 0) {
                sb.append(FIELD_SEPARATOR);
            }
            // 字段内容先转义再追加
            sb.append(escape(fields.get(i)));
        }
        // 返回拼好的整行
        return sb.toString();
    }

    /**
     * 把一行 TSV 文本拆成字段列表，是 {@link #joinLine(List)} 的逆运算。
     *
     * @param line 一行文本，允许为 null
     * @return 还原后的字段列表，null 输入返回空列表
     */
    public static List<String> splitLine(String line) {
        // 准备承载结果
        List<String> fields = new ArrayList<>();
        // null 输入视为没有字段
        if (line == null) {
            return fields;
        }
        // 使用 -1 作为 limit，保证行尾的空字段不会被丢弃
        String[] parts = line.split(String.valueOf(FIELD_SEPARATOR), -1);
        // 逐个字段反转义后收集
        for (String part : parts) {
            fields.add(unescape(part));
        }
        // 返回结果
        return fields;
    }

    /**
     * 把标签列表编码成单个字段，便于写进 TSV。
     * 注意执行顺序：先逐个标签转义（标签内的竖线会变成 \p），再用竖线连接，
     * 这样连接用的竖线不会被误转义，解码时才能正确切分。
     *
     * @param tags 标签列表，允许为 null
     * @return 编码后的标签字段，空列表返回空字符串
     */
    public static String encodeTags(List<String> tags) {
        // 没有标签时返回空字符串，读取端会还原成空列表
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        // 用于累积标签字段
        StringBuilder sb = new StringBuilder();
        // 依次追加每个标签
        for (int i = 0; i < tags.size(); i++) {
            // 除第一个标签外，前面补上标签分隔符
            if (i > 0) {
                sb.append(TAG_SEPARATOR);
            }
            // 标签内容先转义再追加
            sb.append(escape(tags.get(i)));
        }
        // 返回编码结果
        return sb.toString();
    }

    /**
     * 把标签字段还原成标签列表，是 {@link #encodeTags(List)} 的逆运算。
     * 本方法保持纯粹：不做去空白、不去重，这些业务规则留给上层的服务类处理，
     * 以确保「编码再解码等于原值」这条性质在测试中严格成立。
     *
     * @param encoded 编码后的标签字段，允许为 null
     * @return 标签列表，无标签时返回空列表
     */
    public static List<String> decodeTags(String encoded) {
        // 准备承载结果
        List<String> tags = new ArrayList<>();
        // 空字段表示没有标签
        if (encoded == null || encoded.isEmpty()) {
            return tags;
        }
        // 使用 -1 作为 limit，保留末尾的空标签以便与编码过程严格对称
        String[] parts = encoded.split("\\" + TAG_SEPARATOR, -1);
        // 逐个标签反转义后收集
        for (String part : parts) {
            tags.add(unescape(part));
        }
        // 返回结果
        return tags;
    }
}
