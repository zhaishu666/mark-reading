package com.mark.reader.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 书摘实体。
 * <p>
 * 一个 Excerpt 对象对应数据文件 data/excerpts.tsv 中的一行，代表从某本书里摘抄下来的一段文字。
 * 它与 Book 之间通过 {@link #bookId} 建立关联，相当于关系数据库里的外键，
 * 这里刻意不直接持有 Book 对象，避免将来换成数据库时出现对象图加载的麻烦。
 */
public class Excerpt {

    /** 页码的哨兵值：页数字段取该值表示用户未标注这一条书摘来自第几页 */
    public static final int PAGE_UNKNOWN = 0;

    /** 书摘主键；由仓储层在保存时分配，取值为 0 表示尚未持久化 */
    private long id;

    /** 所属书籍的主键，指向 Book.id，不可为空 */
    private long bookId;

    /** 书摘正文；业务上必填，不允许为空白字符串 */
    private String content;

    /** 书摘所在页码；取值为 {@link #PAGE_UNKNOWN} 表示未标注 */
    private int page;

    /** 标签列表；允许为空列表，表示这条书摘没有打标签 */
    private List<String> tags;

    /** 创建时间；由业务层在新增时写入，统计活跃天数时依赖它 */
    private LocalDateTime createdAt;

    /**
     * 无参构造函数，仅为将来 ORM 反射创建实例预留。
     */
    public Excerpt() {
        // 未指定标签时默认给一个空列表，避免调用方到处判空
        this.tags = new ArrayList<>();
    }

    /**
     * 业务层创建新书摘时使用的便捷构造函数。
     * 主键 id 与创建时间由仓储层、业务层分别填充，故不在此处接收。
     *
     * @param bookId  所属书籍主键
     * @param content 书摘正文
     * @param page    所在页码，传 {@link #PAGE_UNKNOWN} 表示未标注
     * @param tags    标签列表，允许为 null
     */
    public Excerpt(long bookId, String content, int page, List<String> tags) {
        // 记录所属书籍主键
        this.bookId = bookId;
        // 记录书摘正文
        this.content = content;
        // 记录页码
        this.page = page;
        // 标签为空时统一存成空列表，保证 getTags() 永不返回 null
        this.tags = (tags == null) ? new ArrayList<>() : new ArrayList<>(tags);
    }

    /**
     * 获取书摘主键。
     *
     * @return 主键，0 表示尚未持久化
     */
    public long getId() {
        return id;
    }

    /**
     * 设置书摘主键，仅供仓储层分配 id 时调用。
     *
     * @param id 主键
     */
    public void setId(long id) {
        this.id = id;
    }

    /**
     * 获取所属书籍主键。
     *
     * @return 书籍主键
     */
    public long getBookId() {
        return bookId;
    }

    /**
     * 设置所属书籍主键。
     *
     * @param bookId 书籍主键
     */
    public void setBookId(long bookId) {
        this.bookId = bookId;
    }

    /**
     * 获取书摘正文。
     *
     * @return 正文内容
     */
    public String getContent() {
        return content;
    }

    /**
     * 设置书摘正文。
     *
     * @param content 正文内容
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * 获取页码。
     *
     * @return 页码，{@link #PAGE_UNKNOWN} 表示未标注
     */
    public int getPage() {
        return page;
    }

    /**
     * 设置页码。
     *
     * @param page 页码
     */
    public void setPage(int page) {
        this.page = page;
    }

    /**
     * 获取标签列表。
     *
     * @return 标签列表，永不为 null
     */
    public List<String> getTags() {
        return tags;
    }

    /**
     * 设置标签列表，传入 null 时自动转为空列表。
     *
     * @param tags 标签列表
     */
    public void setTags(List<String> tags) {
        // 防御性处理：把 null 归一成空列表，并把入参复制一份避免外部改动影响实体内部状态
        this.tags = (tags == null) ? new ArrayList<>() : new ArrayList<>(tags);
    }

    /**
     * 获取创建时间。
     *
     * @return 创建时间，可能为 null
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
     * 判断该条书摘是否标注了页码。
     *
     * @return true 表示标注了有效页码
     */
    public boolean hasPage() {
        // 只要页码不是哨兵值，就认为标注过
        return page != PAGE_UNKNOWN;
    }

    /**
     * 判断两段书摘是否代表同一条已持久化的记录，语义与 Book 保持一致。
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
        if (!(other instanceof Excerpt excerpt)) {
            return false;
        }
        // 任一方未持久化时不使用主键比较
        if (this.id == 0 || excerpt.id == 0) {
            return false;
        }
        // 双方都已持久化，按主键判断
        return this.id == excerpt.id;
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
        // 拼出类似 Excerpt{id=1, bookId=1, page=12, ...} 的文本
        return "Excerpt{id=" + id
                + ", bookId=" + bookId
                + ", content='" + content + '\''
                + ", page=" + page
                + ", tags=" + tags
                + ", createdAt=" + createdAt
                + '}';
    }
}
