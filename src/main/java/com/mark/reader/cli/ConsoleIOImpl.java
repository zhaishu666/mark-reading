package com.mark.reader.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.PushbackInputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * {@link ConsoleIO} 的标准实现，基于 {@code System.in} 与 {@code System.out}。
 * <p>
 * <b>为什么编码不能靠「猜环境」解决。</b>
 * Windows 中文环境下 JDK 会把 {@code stdin.encoding} 报成 GBK，但实际送来的字节未必是 GBK：
 * IDE 的运行窗口、Git Bash、以及管道重定向通常送的都是 UTF-8。
 * 一旦按 GBK 去解 UTF-8 字节，中文就会变成「涓変綋」这种东西，
 * 并且它会作为一个「正常字符串」一路写进数据文件，事后极难追溯。
 * <p>
 * 本实现改为「看字节，而不是猜环境」，输入侧的判定顺序是：
 * <ol>
 *   <li>启动时用 {@code -Dmark.reader.encoding=xxx} 显式指定 —— 最高优先级，完全听用户的</li>
 *   <li>按行读取原始字节，整行是合法 UTF-8（见 {@link Utf8}）—— 按 UTF-8 解码</li>
 *   <li>否则认为它是控制台本地编码 —— 按 {@code stdin.encoding} 解码，取不到则用 JVM 默认</li>
 * </ol>
 * 输出侧不做自动判定（自己的输出无法自我校验），策略是显式覆盖优先，
 * 否则跟随 {@code stdout.encoding}，保持与控制台显示一致。
 */
public class ConsoleIOImpl implements ConsoleIO {

    /** 用于强制指定控制台编码的系统属性名 */
    public static final String ENCODING_PROPERTY = "mark.reader.encoding";

    /** 输入源；包一层回退流是为了处理 CRLF 换行多读出来的那个字节 */
    private final PushbackInputStream source;

    /** 输出写入器，构造时开启自动刷新 */
    private final PrintWriter writer;

    /** 用户显式指定的编码；未指定时为 null */
    private final Charset forcedCharset;

    /** 兜底编码：当一行的字节不是合法 UTF-8 时使用 */
    private final Charset fallbackCharset;

    /** 承载「当前这一行」原始字节的缓冲区，跨调用复用避免反复创建对象 */
    private final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();

    /**
     * 使用标准输入输出构造，并自动决定两侧的编码策略。
     */
    public ConsoleIOImpl() {
        // 输入侧：显式覆盖在解码时单独判断，这里传入控制台声明的编码作为兜底
        // 输出侧：由 createConsoleWriter 处理「显式覆盖优先、否则跟随控制台」
        this(System.in, createConsoleWriter(), consoleCharset("stdin.encoding"));
    }

    /**
     * 供测试与嵌入场景使用的构造函数，可指定输入流、输出通道与兜底编码。
     * 包级可见：它服务于测试替身，不属于对外 API。
     *
     * @param rawSource       原始字节输入流
     * @param writer          输出通道
     * @param fallbackCharset 非 UTF-8 字节所使用的兜底编码
     */
    ConsoleIOImpl(InputStream rawSource, PrintWriter writer, Charset fallbackCharset) {
        // 包装成支持 1 字节回退的流，用于吞掉 CRLF 中多余的 LF
        this.source = new PushbackInputStream(rawSource, 1);
        // 保存输出通道
        this.writer = writer;
        // 读取显式覆盖配置，未配置则为 null
        this.forcedCharset = overrideCharset();
        // 保存兜底编码
        this.fallbackCharset = fallbackCharset;
    }

    /**
     * 输出一行文本。
     *
     * @param message 要输出的内容
     */
    @Override
    public void printLine(String message) {
        // println 会触发自动刷新，内容立即送达控制台
        writer.println(message);
    }

    /**
     * 打印提示语后读取一行输入。
     * 返回的字符串已去掉首尾空白；输入流结束返回 null。
     *
     * @param prompt 提示语，可为 null
     * @return 解码后的一行输入，输入流结束返回 null
     */
    @Override
    public String readLine(String prompt) {
        // 有提示语就先输出且不换行，让用户在同一行输入
        if (prompt != null) {
            writer.print(prompt);
            // print 不触发自动刷新，必须手动刷新，否则用户面对黑屏不知道要输入什么
            writer.flush();
        }
        try {
            // 读一行原始字节并解码
            String line = readRawLine();
            // 输入流结束
            if (line == null) {
                return null;
            }
            // 去掉首尾空白后返回
            return line.trim();
        } catch (IOException e) {
            // 读取失败属于环境问题，抛出并说明原因
            throw new UncheckedIOException("读取控制台输入失败", e);
        }
    }

    /**
     * 读取一个指定范围内的整数，非法输入会重新询问。
     *
     * @param prompt 提示语
     * @param min    允许的最小值（含）
     * @param max    允许的最大值（含）
     * @return 合法的整数值，输入流结束返回 null
     */
    @Override
    public Integer readInt(String prompt, int min, int max) {
        // 循环直到拿到合法输入或输入结束
        while (true) {
            // 读取一行输入
            String line = readLine(prompt);
            // 输入结束，向上返回 null 让调用方退出
            if (line == null) {
                return null;
            }
            // 尝试把输入解析成整数
            try {
                int value = Integer.parseInt(line);
                // 超出范围时给出提示并重新询问
                if (value < min || value > max) {
                    printLine("输入超出范围，请输入 " + min + " 到 " + max + " 之间的整数。");
                    continue;
                }
                // 合法输入直接返回
                return value;
            } catch (NumberFormatException e) {
                // 不是数字时给出提示并重新询问
                printLine("输入不是合法整数，请重新输入。");
            }
        }
    }

    /**
     * 从输入流按字节读取一整行，然后交给解码逻辑。
     * 以 {@code LF}、{@code CRLF} 或单独 {@code CR} 作为行结束标记。
     *
     * @return 解码后的一行文本；整个输入流已结束时返回 null
     * @throws IOException 读取底层流失败时抛出
     */
    private String readRawLine() throws IOException {
        // 复用缓冲区前先清空
        lineBuffer.reset();
        // 标记这一行是否读到过任何字节，用于区分「空行」与「输入结束」
        boolean readAnyByte = false;
        // 逐字节读取直到遇到行结束符或流末尾
        while (true) {
            int current = source.read();
            // 流已结束
            if (current == -1) {
                // 一个字节都没读到，说明真的没有下一行了
                if (!readAnyByte) {
                    return null;
                }
                // 否则是最后一行没有以换行符收尾，按正常一行处理
                break;
            }
            // 记录读到了内容
            readAnyByte = true;
            // 遇到 LF 直接结束本行
            if (current == '\n') {
                break;
            }
            // 遇到 CR：可能是 CRLF，也可能是老式 Mac 的单独 CR
            if (current == '\r') {
                skipOptionalLineFeed();
                break;
            }
            // 普通字节写入缓冲区
            lineBuffer.write(current);
        }
        // 把收集到的字节解码成字符串
        return decodeLine(lineBuffer.toByteArray());
    }

    /**
     * 处理 CRLF 中的第二个字符：若下一个字节确实是 LF 就吃掉它，
     * 否则把它退回输入流，避免误吞下一行的首字节。
     *
     * @throws IOException 读取或回退失败时抛出
     */
    private void skipOptionalLineFeed() throws IOException {
        // 预读一个字节
        int next = source.read();
        // 既不是 LF 也不是流结束，说明它属于下一行，必须还回去
        if (next != '\n' && next != -1) {
            source.unread(next);
        }
    }

    /**
     * 把一行的原始字节解码成字符串。
     * 判定顺序：显式覆盖 &gt; 合法 UTF-8 &gt; 兜底编码。
     *
     * @param bytes 这一行的原始字节
     * @return 解码后的字符串
     */
    private String decodeLine(byte[] bytes) {
        // 用户显式指定了编码，就不做任何猜测
        if (forcedCharset != null) {
            return new String(bytes, forcedCharset);
        }
        // 整行是合法 UTF-8，按 UTF-8 解码（纯 ASCII 也走这里，结果与本地编码完全一致）
        if (Utf8.isValid(bytes)) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        // 不是合法 UTF-8，按控制台本地编码解码
        return new String(bytes, fallbackCharset);
    }

    /**
     * 读取显式指定的编码配置。
     *
     * @return 用户指定的字符集；未配置时返回 null
     * @throws IllegalArgumentException 配置存在但编码名不受支持时抛出
     */
    static Charset overrideCharset() {
        // 读取系统属性
        String value = System.getProperty(ENCODING_PROPERTY);
        // 未配置或只填了空白，视为没有覆盖
        if (value == null || value.isBlank()) {
            return null;
        }
        // 尝试按名称解析
        try {
            return Charset.forName(value.trim());
        } catch (Exception e) {
            // 用户显式写错的配置必须立刻报错，而不是悄悄回退成乱码
            throw new IllegalArgumentException("系统属性 " + ENCODING_PROPERTY
                    + " 指定的编码不受支持：" + value, e);
        }
    }

    /**
     * 读取 JDK 记录的控制台编码。
     *
     * @param propertyName 属性名，如 stdin.encoding
     * @return 解析出的字符集；属性缺失或名称非法时返回 JVM 默认字符集
     */
    static Charset consoleCharset(String propertyName) {
        // 读取系统属性，低版本 JDK 上该属性可能不存在
        String name = System.getProperty(propertyName);
        // 属性存在时尝试解析
        if (name != null && !name.isBlank()) {
            try {
                // 按名称查找字符集
                return Charset.forName(name.trim());
            } catch (Exception ignored) {
                // 名称非法时静默回退，避免因为编码探测失败而无法启动程序
            }
        }
        // 兜底使用 JVM 默认字符集
        return Charset.defaultCharset();
    }

    /**
     * 创建面向标准输出的写入器。
     *
     * @return 使用「显式覆盖优先、否则跟随 stdout.encoding」策略的写入器
     */
    private static PrintWriter createConsoleWriter() {
        // 先看有没有显式覆盖
        Charset override = overrideCharset();
        // 覆盖存在就用它，否则跟随控制台声明的输出编码
        Charset charset = (override != null) ? override : consoleCharset("stdout.encoding");
        // 第二个参数 true 表示调用 println 后自动刷新
        return new PrintWriter(new OutputStreamWriter(System.out, charset), true);
    }
}
