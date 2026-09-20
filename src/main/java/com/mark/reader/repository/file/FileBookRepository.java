package com.mark.reader.repository.file;

import com.mark.reader.exception.ValidationException;
import com.mark.reader.model.Book;
import com.mark.reader.repository.BookRepository;

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
 * 基于文本文件的书籍仓储实现。
 * <p>
 * 数据落在 {@code <数据目录>/books.tsv}，首行是表头，之后每行一本书，
 * 字段以制表符分隔，字段内部的特殊字符由 {@link TextCodec} 负责转义。
 * 实现上没有做内存缓存，每次读写都直接操作文件：数据量只有几百本书时性能完全够用，
 * 换来的是「任何时候文件内容都是最新真相」这一简单可靠的语义，也让测试更容易验证持久化结果。
 */
public class FileBookRepository implements BookRepository {

    /** 数据文件名 */
    private static final String FILE_NAME = "books.tsv";

    /** 表头行，字段顺序必须与 {@link #toLine(Book)} 保持严格一致 */
    private static final String HEADER = "id\ttitle\tauthor\ttotalPages\tcurrentPage\tcreatedAt";

    /** 每行应该有的字段个数，用于识别损坏的数据行 */
    private static final int FIELD_COUNT = 6;

    /** 数据文件的绝对路径 */
    private final Path dataFile;

    /**
     * 使用数据目录构造仓储，文件不存在时会自动创建并写入表头。
     *
     * @param dataDir 数据目录，不存在时会被自动创建
     */
    public FileBookRepository(Path dataDir) {
        // 定位到本仓储负责的数据文件
        this.dataFile = dataDir.resolve(FILE_NAME);
        // 保证目录与文件就绪，后续读写才不会失败
        ensureFileReady();
    }

    /**
     * 确保数据目录存在且文件已初始化。
     * 该方法只在构造时调用一次，属于「一次性准备工作」。
     */
    private void ensureFileReady() {
        try {
            // 取出父目录，理论上一定有（路径由目录拼接而来），防御性判空
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
            // 初始化失败属于环境问题，直接抛出并带上文件路径便于排查
            throw new UncheckedIOException("初始化书籍数据文件失败：" + dataFile, e);
        }
    }

    /**
     * 计算下一个可用的书籍主键，取现有最大主键加一。
     *
     * @return 下一个可用主键，空库时返回 1
     */
    @Override
    public long nextId() {
        // 取现有主键的最大值再加一，保证主键单调递增且不复用
        return maxId(readAll()) + 1;
    }

    /**
     * 保存书籍：主键为 0 时新增，否则按主键覆盖。
     *
     * @param book 待保存的书籍
     * @return 保存后的书籍对象
     */
    @Override
    public Book save(Book book) {
        // 先读出当前全部书籍，再在内存中修改，最后整体写回
        List<Book> books = readAll();
        // 主键为 0 说明是新书，需要分配主键后追加
        if (book.getId() == 0) {
            book.setId(maxId(books) + 1);
            books.add(book);
        } else {
            // 主键已存在则找到对应位置替换；找不到（被外部删掉）就当作更新为追加
            int index = indexOf(books, book.getId());
            if (index >= 0) {
                books.set(index, book);
            } else {
                books.add(book);
            }
        }
        // 整体写回文件
        writeAll(books);
        // 返回已带主键的对象，方便调用方继续使用
        return book;
    }

    /**
     * 按主键查找书籍。
     *
     * @param id 书籍主键
     * @return 命中则返回书籍，未命中返回空 Optional
     */
    @Override
    public Optional<Book> findById(long id) {
        // 逐个比较主键，找到即返回
        for (Book book : readAll()) {
            if (book.getId() == id) {
                return Optional.of(book);
            }
        }
        // 遍历完仍未命中
        return Optional.empty();
    }

    /**
     * 按书名精确查找书籍，忽略首尾空白与大小写。
     *
     * @param title 书名
     * @return 命中则返回书籍，未命中返回空 Optional
     */
    @Override
    public Optional<Book> findByTitle(String title) {
        // 空书名不可能匹配到任何记录，直接返回空
        if (title == null) {
            return Optional.empty();
        }
        // 双方都去掉首尾空白后忽略大小写比较，兼顾中文与英文书名
        String expected = title.trim();
        for (Book book : readAll()) {
            // 书名可能为 null，需要先兜住再比较
            if (book.getTitle() != null && book.getTitle().trim().equalsIgnoreCase(expected)) {
                return Optional.of(book);
            }
        }
        // 遍历完仍未命中
        return Optional.empty();
    }

    /**
     * 查询全部书籍。
     *
     * @return 书籍列表，按文件中的存放顺序（即主键升序）
     */
    @Override
    public List<Book> findAll() {
        // 直接返回读到的列表
        return readAll();
    }

    /**
     * 按主键删除书籍。
     *
     * @param id 书籍主键
     * @return true 表示删除成功，false 表示主键不存在
     */
    @Override
    public boolean deleteById(long id) {
        // 先读出全部书籍
        List<Book> books = readAll();
        // 找到目标所在位置
        int index = indexOf(books, id);
        // 不存在则直接返回失败
        if (index < 0) {
            return false;
        }
        // 从列表中移除后整体写回
        books.remove(index);
        writeAll(books);
        // 返回删除成功
        return true;
    }

    /**
     * 从文件中读出全部书籍。
     * 空白行会被忽略，字段个数不对或数字格式错误的行会抛出校验异常并指明行号，
     * 这样数据损坏时能第一时间暴露，而不是悄悄丢数据。
     *
     * @return 书籍列表
     */
    private List<Book> readAll() {
        // 准备承载结果
        List<Book> books = new ArrayList<>();
        try {
            // 一次性读出所有行（UTF-8，兼容换行符差异）
            List<String> lines = Files.readAllLines(dataFile, StandardCharsets.UTF_8);
            // 从第 1 行开始遍历，第 0 行是表头
            for (int i = 1; i < lines.size(); i++) {
                // 取出当前行内容
                String line = lines.get(i);
                // 跳过空白行，允许用户手工编辑文件时留下空行
                if (line.isBlank()) {
                    continue;
                }
                // 解析这一行；行号按人的习惯从 1 开始计数，便于定位
                books.add(fromLine(line, i + 1));
            }
        } catch (IOException e) {
            // 读取失败属于环境问题，抛出并带上文件路径
            throw new UncheckedIOException("读取书籍数据文件失败：" + dataFile, e);
        }
        // 返回解析结果
        return books;
    }

    /**
     * 把内存中的书籍列表整体写回文件，覆盖原内容。
     *
     * @param books 待写入的书籍列表
     */
    private void writeAll(List<Book> books) {
        // 准备写入内容，第一行固定是表头
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        // 逐本编码成一行
        for (Book book : books) {
            lines.add(toLine(book));
        }
        try {
            // 覆盖写入，UTF-8 编码保证中文正确落盘
            Files.write(dataFile, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // 写入失败属于环境问题，抛出并带上文件路径
            throw new UncheckedIOException("写入书籍数据文件失败：" + dataFile, e);
        }
    }

    /**
     * 把书籍对象编码成一行 TSV 文本。
     *
     * @param book 书籍对象
     * @return 编码后的单行文本
     */
    private String toLine(Book book) {
        // 按表头定义的顺序组织字段，日期为 null 时写空字符串
        return TextCodec.joinLine(List.of(
                String.valueOf(book.getId()),
                book.getTitle(),
                book.getAuthor(),
                String.valueOf(book.getTotalPages()),
                String.valueOf(book.getCurrentPage()),
                book.getCreatedAt() == null ? "" : book.getCreatedAt().toString()
        ));
    }

    /**
     * 把一行 TSV 文本解析成书籍对象。
     *
     * @param line     单行文本
     * @param lineNo   行号，仅用于拼装错误提示
     * @return 解析出的书籍对象
     * @throws ValidationException 字段个数不符或数字、日期格式非法时抛出
     */
    private Book fromLine(String line, int lineNo) {
        // 先拆出字段
        List<String> fields = TextCodec.splitLine(line);
        // 字段个数不对说明文件被改坏了，立刻报错而不是猜
        if (fields.size() != FIELD_COUNT) {
            throw new ValidationException("书籍数据文件第 " + lineNo + " 行字段个数异常：期望 "
                    + FIELD_COUNT + " 个，实际 " + fields.size() + " 个");
        }
        // 逐个字段组装成对象
        try {
            // 新建空实体
            Book book = new Book();
            // 第 0 个字段是主键
            book.setId(Long.parseLong(fields.get(0)));
            // 第 1 个字段是书名
            book.setTitle(fields.get(1));
            // 第 2 个字段是作者
            book.setAuthor(fields.get(2));
            // 第 3 个字段是总页数
            book.setTotalPages(Integer.parseInt(fields.get(3)));
            // 第 4 个字段是当前页码
            book.setCurrentPage(Integer.parseInt(fields.get(4)));
            // 第 5 个字段是创建时间，空字符串表示未记录
            String createdAt = fields.get(5);
            book.setCreatedAt(createdAt.isEmpty() ? null : LocalDateTime.parse(createdAt));
            // 返回组装好的对象
            return book;
        } catch (NumberFormatException | DateTimeParseException e) {
            // 数字或日期解析失败，包装成校验异常并附加行号
            throw new ValidationException("书籍数据文件第 " + lineNo + " 行格式非法：" + line);
        }
    }

    /**
     * 在列表中查找指定主键所在的下标。
     *
     * @param books 书籍列表
     * @param id    目标主键
     * @return 下标，未找到返回 -1
     */
    private int indexOf(List<Book> books, long id) {
        // 顺序扫描即可，数据量很小
        for (int i = 0; i < books.size(); i++) {
            if (books.get(i).getId() == id) {
                return i;
            }
        }
        // 未找到
        return -1;
    }

    /**
     * 计算列表中的最大主键。
     *
     * @param books 书籍列表
     * @return 最大主键，列表为空时返回 0
     */
    private long maxId(List<Book> books) {
        // 从 0 开始，因为主键约定为正数，0 可安全作为下界
        long max = 0;
        for (Book book : books) {
            max = Math.max(max, book.getId());
        }
        // 返回最大值
        return max;
    }
}
