package com.mark.reader.model;

import java.time.LocalDateTime;

/**
 * 书籍实体。
 * <p>
 * 一个 Book 对象对应数据文件 data/books.tsv 中的一行，也对应将来数据库里 books 表的一条记录。
 * 这里刻意使用「可变 POJO + 无参构造 + getter/setter」的老派写法，而不是 Java 17 的 record，
 * 原因是将来换成 MyBatis / JPA 时框架需要通过无参构造反射建对象、再靠 setter 注入字段，
 * 现在就按那个形状写，日后迁移数据库不需要改实体类。
 */
public class Book {

    /** 书籍主键；由仓储层在保存时分配，取值为 0 表示尚未持久化 */
    private long id;

    /** 书名；业务上必填，不允许为空白字符串 */
    private String title;

    /** 作者；允许为空字符串，表示未填写 */
    private String author;

    /** 书籍总页数；取值为 0 表示页数未知 */
    private int totalPages;

    /** 当前读到第几页；取值为 0 表示尚未记录进度 */
    private int currentPage;

    /** 创建时间；由业务层在新增时写入，用于统计与排序 */
    private LocalDateTime createdAt;

    /**
     * 无参构造函数。
     * 当前代码不会主动调用它，保留它纯粹是为了将来 ORM 框架能反射创建实例。
     */
    public Book() {
        // 故意留空：所有字段保持默认值，等待调用方通过 setter 逐个赋值
    }

    /**
     * 业务层创建新书时使用的便捷构造函数。
     * 主键 id 与创建时间不在这里指定，它们分别由仓储层和业务层负责填充。
     *
     * @param title      书名
     * @param author     作者
     * @param totalPages 总页数，传 0 表示未知
     */
    public Book(String title, String author, int totalPages) {
        // 直接使用入参初始化三个业务字段
        this.title = title;
        this.author = author;
        this.totalPages = totalPages;
    }

    /**
     * 获取书籍主键。
     *
     * @return 主键，0 表示尚未持久化
     */
    public long getId() {
        return id;
    }

    /**
     * 设置书籍主键，仅供仓储层在分配 id 时调用。
     *
     * @param id 主键
     */
    public void setId(long id) {
        this.id = id;
    }

    /**
     * 获取书名。
     *
     * @return 书名
     */
    public String getTitle() {
        return title;
    }

    /**
     * 设置书名。
     *
     * @param title 书名
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * 获取作者。
     *
     * @return 作者，可能为空字符串
     */
    public String getAuthor() {
        return author;
    }

    /**
     * 设置作者。
     *
     * @param author 作者
     */
    public void setAuthor(String author) {
        this.author = author;
    }

    /**
     * 获取总页数。
     *
     * @return 总页数，0 表示未知
     */
    public int getTotalPages() {
        return totalPages;
    }

    /**
     * 设置总页数。
     *
     * @param totalPages 总页数
     */
    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    /**
     * 获取当前阅读页码。
     *
     * @return 当前页码，0 表示未记录
     */
    public int getCurrentPage() {
        return currentPage;
    }

    /**
     * 设置当前阅读页码。
     *
     * @param currentPage 当前页码
     */
    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    /**
     * 获取创建时间。
     *
     * @return 创建时间，可能为 null（例如手工构造的对象）
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置创建时间。
     *
     * @param createdAt 创建时间
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 判断总页数是否已知。
     * 统计覆盖篇幅时，总页数未知的书只能显示占位符。
     *
     * @return true 表示总页数大于 0，即已知
     */
    public boolean isTotalPagesKnown() {
        // 约定 0 为「未知」的哨兵值，因此大于 0 才算已知
        return totalPages > 0;
    }

    /**
     * 判断两本书是否代表同一条已持久化的记录。
     * 采用实体类的经典语义：只有双方都已分配主键时才按主键比较；
     * 只要有一方未持久化（id 为 0），就一律视为不同对象，避免两本同名新书被误判成同一本。
     *
     * @param other 待比较对象
     * @return true 表示两者主键相同
     */
    @Override
    public boolean equals(Object other) {
        // 同一个引用自然相等
        if (this == other) {
            return true;
        }
        // 类型不匹配或对方为 null 时不等
        if (!(other instanceof Book book)) {
            return false;
        }
        // 任一方未持久化时不使用主键比较，直接判定为不同对象
        if (this.id == 0 || book.id == 0) {
            return false;
        }
        // 双方都已持久化，按主键判断是否为同一条记录
        return this.id == book.id;
    }

    /**
     * 计算哈希值，与 equals 保持一致，只依赖主键。
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        // 与 equals 使用同一字段，保证哈希一致
        return Long.hashCode(id);
    }

    /**
     * 生成便于调试与日志输出的字符串。
     *
     * @return 包含全部业务字段的描述文本
     */
    @Override
    public String toString() {
        // 拼出类似 Book{id=1, title='活着', ...} 的文本
        return "Book{id=" + id
                + ", title='" + title + '\''
                + ", author='" + author + '\''
                + ", totalPages=" + totalPages
                + ", currentPage=" + currentPage
                + ", createdAt=" + createdAt
                + '}';
    }
}
