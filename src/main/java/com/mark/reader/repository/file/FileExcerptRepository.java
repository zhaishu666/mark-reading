package com.mark.reader.repository.file;

import com.mark.reader.exception.ValidationException;
import com.mark.reader.model.Excerpt;
import com.mark.reader.repository.ExcerptRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 基于文本文件的书籍摘录仓储实现。
 * <p>
 * 数据落在 {@code <数据目录>/excerpts.tsv}，结构与书籍文件一致：首行表头、每行一条记录、制表符分隔。
 * 与书籍仓储的关键差别有两点：
 * 一是标签字段需要先把列表编码成字符串再写入，
 * 二是提供了按书籍主键批量删除的能力，用于书籍被删除时清理级联数据。
 */
public class FileExcerptRepository implements ExcerptRepository {

    /** 数据文件名，与书籍文件放在同一目录 */
    private static final String FILE_NAME = "excerpts.tsv";

    /** 表头行，字段顺序必须与 {@link #toLine(Excerpt)} 保持严格一致 */
    private static final String HEADER = "id\tbookId\tcontent\tpage\ttags\tcreatedAt";

    /** 每行应该有的字段个数，用于识别损坏的数据行 */
    private static final int FIELD_COUNT = 6;

    /** 数据文件的绝对路径 */
    private final Path dataFile;

    /**
     * 使用数据目录构造仓储，文件不存在时会自动创建并写入表头。
     *
     * @param dataDir 数据目录，不存在时会被自动创建
     */
    public FileExcerptRepository(Path dataDir) {
        // 定位到本仓储负责的数据文件
        this.dataFile = dataDir.resolve(FILE_NAME);
        // 保证目录与文件就绪
        ensureFileReady();
    }

    /**
     * 确保数据目录存在且文件已初始化。
     */
    private void ensureFileReady() {
        try {
            // 取出父目录并防御性判空
            Path parent = dataFile.getParent();
            // 父目录不存在则递归创建
            if (parent != null) {
                Files.createDirectories(parent);
            }
            // 文件不存在时写入表头，建立空数据集
            if (Files.notExists(dataFile)) {
                Files.write(dataFile, List.of(HEADER), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            // 初始化失败属于环境问题，抛出并带上文件路径便于排查
            throw new UncheckedIOException("初始化书摘数据文件失败：" + dataFile, e);
        }
    }

    /**
     * 计算下一个可用的书摘主键，取现有最大主键加一。
     *
     * @return 下一个可用主键，空库时返回 1
     */
    @Override
    public long nextId() {
        // 取现有主键的最大值再加一
        return maxId(readAll()) + 1;
    }

    /**
     * 保存书摘：主键为 0 时新增，否则按主键覆盖。
     *
     * @param excerpt 待保存的书摘
     * @return 保存后的书摘对象
     */
    @Override
    public Excerpt save(Excerpt excerpt) {
        // 先读出全部书摘
        List<Excerpt> excerpts = readAll();
        // 主键为 0 说明是新记录，分配主键后追加
        if (excerpt.getId() == 0) {
            excerpt.setId(maxId(excerpts) + 1);
            excerpts.add(excerpt);
        } else {
            // 主键已存在则替换，找不到就追加
            int index = indexOf(excerpts, excerpt.getId());
            if (index >= 0) {
                excerpts.set(index, excerpt);
            } else {
                excerpts.add(excerpt);
            }
        }
        // 整体写回文件
        writeAll(excerpts);
        // 返回已带主键的对象
        return excerpt;
    }

    /**
     * 按主键查找书摘。
     *
     * @param id 书摘主键
     * @return 命中则返回书摘，未命中返回空 Optional
     */
    @Override
    public Optional<Excerpt> findById(long id) {
        // 逐个比较主键
        for (Excerpt excerpt : readAll()) {
            if (excerpt.getId() == id) {
                return Optional.of(excerpt);
            }
        }
        // 未命中
        return Optional.empty();
    }

    /**
     * 查询某本书的全部书摘。
     *
     * @param bookId 书籍主键
     * @return 该书摘录列表，按主键升序
     */
    @Override
    public List<Excerpt> findByBookId(long bookId) {
        // 准备承载结果
        List<Excerpt> result = new ArrayList<>();
        // 逐个筛选，只保留属于该书的记录
        for (Excerpt excerpt : readAll()) {
            if (excerpt.getBookId() == bookId) {
                result.add(excerpt);
            }
        }
        // 返回筛选结果
        return result;
    }

    /**
     * 查询全部书摘。
     *
     * @return 书摘列表，按文件中的存放顺序（即主键升序）
     */
    @Override
    public List<Excerpt> findAll() {
        // 直接返回读到的列表
        return readAll();
    }

    /**
     * 删除某本书的全部书摘，用于书籍删除后的级联清理。
     *
     * @param bookId 书籍主键
     * @return 实际删除的条数
     */
    @Override
    public int deleteByBookId(long bookId) {
        // 先读出全部书摘
        List<Excerpt> excerpts = readAll();
        // 记录删除前的条数，用于计算实际删除数量
        int before = excerpts.size();
        // 用 removeIf 一次性摘掉属于该书的记录
        excerpts.removeIf(excerpt -> excerpt.getBookId() == bookId);
        // 计算实际删除数量
        int removed = before - excerpts.size();
        // 只有确实删掉了内容才需要写回文件，避免无意义的磁盘写入
        if (removed > 0) {
            writeAll(excerpts);
        }
        // 返回删除条数
        return removed;
    }

    /**
     * 从文件中读出全部书摘。
     *
     * @return 书摘列表
     */
    private List<Excerpt> readAll() {
        // 准备承载结果
        List<Excerpt> excerpts = new ArrayList<>();
        try {
            // 一次性读出所有行
            List<String> lines = Files.readAllLines(dataFile, StandardCharsets.UTF_8);
            // 从第 1 行开始，跳过表头
            for (int i = 1; i < lines.size(); i++) {
                // 取出当前行
                String line = lines.get(i);
                // 跳过空白行
                if (line.isBlank()) {
                    continue;
                }
                // 解析这一行，行号从 1 开始计数
                excerpts.add(fromLine(line, i + 1));
            }
        } catch (IOException e) {
            // 读取失败属于环境问题
            throw new UncheckedIOException("读取书摘数据文件失败：" + dataFile, e);
        }
        // 返回解析结果
        return excerpts;
    }

    /**
     * 把内存中的书摘列表整体写回文件，覆盖原内容。
     *
     * @param excerpts 待写入的书摘列表
     */
    private void writeAll(List<Excerpt> excerpts) {
        // 准备写入内容，第一行固定是表头
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        // 逐条编码成一行
        for (Excerpt excerpt : excerpts) {
            lines.add(toLine(excerpt));
        }
        try {
            // 覆盖写入，UTF-8 编码保证中文正确落盘
            Files.write(dataFile, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // 写入失败属于环境问题
            throw new UncheckedIOException("写入书摘数据文件失败：" + dataFile, e);
        }
    }

    /**
     * 把书摘对象编码成一行 TSV 文本。
     *
     * @param excerpt 书摘对象
     * @return 编码后的单行文本
     */
    private String toLine(Excerpt excerpt) {
        // 标签列表先编码成字符串；日期为 null 时写空字符串
        return TextCodec.joinLine(List.of(
                String.valueOf(excerpt.getId()),
                String.valueOf(excerpt.getBookId()),
                excerpt.getContent(),
                String.valueOf(excerpt.getPage()),
                TextCodec.encodeTags(excerpt.getTags()),
                excerpt.getCreatedAt() == null ? "" : excerpt.getCreatedAt().toString()
        ));
    }

    /**
     * 把一行 TSV 文本解析成书摘对象。
     *
     * @param line   单行文本
     * @param lineNo 行号，仅用于拼装错误提示
     * @return 解析出的书摘对象
     * @throws ValidationException 字段个数不符或数字、日期格式非法时抛出
     */
    private Excerpt fromLine(String line, int lineNo) {
        // 先拆出字段
        List<String> fields = TextCodec.splitLine(line);
        // 字段个数不对说明文件被改坏了
        if (fields.size() != FIELD_COUNT) {
            throw new ValidationException("书摘数据文件第 " + lineNo + " 行字段个数异常：期望 "
                    + FIELD_COUNT + " 个，实际 " + fields.size() + " 个");
        }
        // 逐个字段组装成对象
        try {
            // 新建空实体
            Excerpt excerpt = new Excerpt();
            // 第 0 个字段是主键
            excerpt.setId(Long.parseLong(fields.get(0)));
            // 第 1 个字段是所属书籍主键
            excerpt.setBookId(Long.parseLong(fields.get(1)));
            // 第 2 个字段是正文
            excerpt.setContent(fields.get(2));
            // 第 3 个字段是页码
            excerpt.setPage(Integer.parseInt(fields.get(3)));
            // 第 4 个字段是标签，需要从字符串还原成列表
            excerpt.setTags(TextCodec.decodeTags(fields.get(4)));
            // 第 5 个字段是创建时间，空字符串表示未记录
            String createdAt = fields.get(5);
            excerpt.setCreatedAt(createdAt.isEmpty() ? null : LocalDateTime.parse(createdAt));
            // 返回组装好的对象
            return excerpt;
        } catch (NumberFormatException | DateTimeParseException e) {
            // 数字或日期解析失败，包装成校验异常并附加行号
            throw new ValidationException("书摘数据文件第 " + lineNo + " 行格式非法：" + line);
        }
    }

    /**
     * 在列表中查找指定主键所在的下标。
     *
     * @param excerpts 书摘列表
     * @param id       目标主键
     * @return 下标，未找到返回 -1
     */
    private int indexOf(List<Excerpt> excerpts, long id) {
        // 顺序扫描即可
        for (int i = 0; i < excerpts.size(); i++) {
            if (excerpts.get(i).getId() == id) {
                return i;
            }
        }
        // 未找到
        return -1;
    }

    /**
     * 计算列表中的最大主键。
     *
     * @param excerpts 书摘列表
     * @return 最大主键，列表为空时返回 0
     */
    private long maxId(List<Excerpt> excerpts) {
        // 从 0 开始，因为主键约定为正数
        long max = 0;
        for (Excerpt excerpt : excerpts) {
            max = Math.max(max, excerpt.getId());
        }
        // 返回最大值
        return max;
    }
}
