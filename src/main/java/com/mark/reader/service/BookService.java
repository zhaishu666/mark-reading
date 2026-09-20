package com.mark.reader.service;

import com.mark.reader.exception.BookNotFoundException;
import com.mark.reader.exception.ValidationException;
import com.mark.reader.model.Book;
import com.mark.reader.repository.BookRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 书籍业务服务。
 * <p>
 * 这一层承载与书籍相关的业务规则：书名不能为空、页数不能为负、同一书名不允许重复录入。
 * 校验通过后再交给仓储层落盘，从而保证「仓储里存的一定是干净数据」。
 * <p>
 * 时间来源通过构造参数 {@link Clock} 注入，而不是直接调用 {@code LocalDateTime.now()}，
 * 这样测试里就能把「现在」固定成任意时刻，不必依赖真实系统时间。
 */
public class BookService {

    /** 书籍仓储，负责真正的读写 */
    private final BookRepository bookRepository;

    /** 时钟，用于获取当前时间，注入以便测试替换 */
    private final Clock clock;

    /**
     * 使用系统默认时钟构造服务，供生产环境使用。
     *
     * @param bookRepository 书籍仓储
     */
    public BookService(BookRepository bookRepository) {
        // 委托给全参构造函数，时钟取系统默认时区
        this(bookRepository, Clock.systemDefaultZone());
    }

    /**
     * 全参构造函数。
     *
     * @param bookRepository 书籍仓储
     * @param clock           时钟，测试中可传入固定时钟
     */
    public BookService(BookRepository bookRepository, Clock clock) {
        // 保存仓储引用
        this.bookRepository = bookRepository;
        // 保存时钟引用
        this.clock = clock;
    }

    /**
     * 添加一本书。
     * 依次校验书名非空、页数非负、书名不重复，全部通过后写入仓储。
     *
     * @param title      书名，必填
     * @param author     作者，可为空
     * @param totalPages 总页数，传 0 表示未知
     * @return 保存后的书籍对象，已带主键与创建时间
     * @throws ValidationException 书名空白、页数为负或书名重复时抛出
     */
    public Book addBook(String title, String author, int totalPages) {
        // 校验书名不能为 null 或纯空白
        if (title == null || title.isBlank()) {
            throw new ValidationException("书名不能为空");
        }
        // 校验页数不能为负数（0 表示未知，是允许的）
        if (totalPages < 0) {
            throw new ValidationException("总页数不能为负数，未知请填 0");
        }
        // 去掉书名首尾空白后再入库，避免「活着」和「活着 」被当成两本书
        String normalizedTitle = title.trim();
        // 校验同名书籍不允许重复录入
        if (bookRepository.findByTitle(normalizedTitle).isPresent()) {
            throw new ValidationException("书名已存在，请勿重复添加：" + normalizedTitle);
        }
        // 作者为空时统一存成空字符串，避免文件里出现 null 字样
        String normalizedAuthor = (author == null) ? "" : author.trim();
        // 组装实体，主键留给仓储层分配
        Book book = new Book(normalizedTitle, normalizedAuthor, totalPages);
        // 新书的阅读进度从 0 开始
        book.setCurrentPage(0);
        // 记录创建时间，时间来源是注入的时钟
        book.setCreatedAt(LocalDateTime.now(clock));
        // 交给仓储层落盘并返回
        return bookRepository.save(book);
    }

    /**
     * 查询全部书籍。
     *
     * @return 书籍列表，按主键升序
     */
    public List<Book> listAll() {
        // 直接透传仓储结果
        return bookRepository.findAll();
    }

    /**
     * 按主键获取书籍，查不到时抛出业务异常。
     *
     * @param id 书籍主键
     * @return 对应的书籍对象
     * @throws BookNotFoundException 主键不存在时抛出
     */
    public Book getById(long id) {
        // 查不到就抛异常，逼调用方处理而不是拿到 null 后崩溃
        return bookRepository.findById(id)
                .orElseThrow(() -> new BookNotFoundException(id));
    }

    /**
     * 按书名关键字模糊搜索。
     * 关键字为空或纯空白时返回全部书籍，方便「列出所有书」这种场景复用同一个方法。
     *
     * @param keyword 书名关键字，可为 null
     * @return 匹配的书籍列表，按主键升序
     */
    public List<Book> searchByKeyword(String keyword) {
        // 关键字为空时直接返回全部，无需过滤
        if (keyword == null || keyword.isBlank()) {
            return bookRepository.findAll();
        }
        // 统一转小写做忽略大小写的包含匹配，同时兼容中文与英文书名
        String needle = keyword.trim().toLowerCase();
        // 流式过滤出书名包含关键字的书籍
        return bookRepository.findAll().stream()
                .filter(book -> book.getTitle() != null
                        && book.getTitle().toLowerCase().contains(needle))
                .toList();
    }
}
