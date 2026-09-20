package com.mark.reader.model;

import java.time.LocalDateTime;

/**
 * 单本书的阅读统计结果。
 * <p>
 * 这是一个只读的值对象：所有字段在构造时一次性确定，之后不再修改，
 * 因此只提供 getter 而不提供 setter，防止统计结果被意外改写。
 * 具体的统计口径由 StatisticsService 负责实现，本类只负责承载结果。
 */
public class ReadingStats {

    /** 书籍主键 */
    private final long bookId;

    /** 书名，用于在报表里直接展示，省去再查一次书表 */
    private final String bookTitle;

    /** 该书摘录条数 */
    private final int excerptCount;

    /** 覆盖篇幅：出现过书摘的不同页码数量，未标注页码的书摘不计入 */
    private final int coveredPages;

    /** 书籍总页数；0 表示未知，此时无法计算覆盖率百分比 */
    private final int totalPages;

    /** 活跃天数：摘录创建日期去重后的天数 */
    private final int activeDays;

    /** 最近一次摘录的时间；该书没有任何摘录时为 null */
    private final LocalDateTime lastExcerptAt;

    /**
     * 全字段构造函数。
     *
     * @param bookId        书籍主键
     * @param bookTitle     书名
     * @param excerptCount  书摘条数
     * @param coveredPages  覆盖的不同页码数量
     * @param totalPages    总页数，0 表示未知
     * @param activeDays    活跃天数
     * @param lastExcerptAt 最近摘录时间，可为 null
     */
    public ReadingStats(long bookId,
                        String bookTitle,
                        int excerptCount,
                        int coveredPages,
                        int totalPages,
                        int activeDays,
                        LocalDateTime lastExcerptAt) {
        // 逐个保存入参，构造完成后对象即不可变
        this.bookId = bookId;
        this.bookTitle = bookTitle;
        this.excerptCount = excerptCount;
        this.coveredPages = coveredPages;
        this.totalPages = totalPages;
        this.activeDays = activeDays;
        this.lastExcerptAt = lastExcerptAt;
    }

    /**
     * 获取书籍主键。
     *
     * @return 书籍主键
     */
    public long getBookId() {
        return bookId;
    }

    /**
     * 获取书名。
     *
     * @return 书名
     */
    public String getBookTitle() {
        return bookTitle;
    }

    /**
     * 获取书摘条数。
     *
     * @return 书摘条数
     */
    public int getExcerptCount() {
        return excerptCount;
    }

    /**
     * 获取覆盖的不同页码数量。
     *
     * @return 覆盖页码数
     */
    public int getCoveredPages() {
        return coveredPages;
    }

    /**
     * 获取书籍总页数。
     *
     * @return 总页数，0 表示未知
     */
    public int getTotalPages() {
        return totalPages;
    }

    /**
     * 获取活跃天数。
     *
     * @return 摘录日期去重后的天数
     */
    public int getActiveDays() {
        return activeDays;
    }

    /**
     * 获取最近一次摘录时间。
     *
     * @return 最近摘录时间，可能为 null
     */
    public LocalDateTime getLastExcerptAt() {
        return lastExcerptAt;
    }

    /**
     * 判断总页数是否已知，决定能否计算覆盖率。
     *
     * @return true 表示总页数大于 0
     */
    public boolean isTotalPagesKnown() {
        // 与 Book 保持同一约定：0 表示未知
        return totalPages > 0;
    }

    /**
     * 计算书摘覆盖率，即覆盖页码占总页数的百分比。
     * 总页数未知时无法计算，返回 -1 由调用方决定如何展示。
     *
     * @return 0 到 100 之间的百分比，总页数未知时返回 -1
     */
    public double coveragePercent() {
        // 总页数未知，无法计算覆盖率
        if (!isTotalPagesKnown()) {
            return -1;
        }
        // 覆盖率 = 覆盖页码数 / 总页数 * 100
        return coveredPages * 100.0 / totalPages;
    }

    /**
     * 生成便于调试与日志输出的字符串。
     *
     * @return 包含全部统计字段的描述文本
     */
    @Override
    public String toString() {
        // 拼出类似 ReadingStats{bookTitle='活着', excerptCount=3, ...} 的文本
        return "ReadingStats{bookId=" + bookId
                + ", bookTitle='" + bookTitle + '\''
                + ", excerptCount=" + excerptCount
                + ", coveredPages=" + coveredPages
                + ", totalPages=" + totalPages
                + ", activeDays=" + activeDays
                + ", lastExcerptAt=" + lastExcerptAt
                + '}';
    }
}
