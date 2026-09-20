package com.mark.reader.model;

import java.time.LocalDateTime;

/**
 * 全局阅读统计结果。
 * <p>
 * 与 {@link ReadingStats} 一样是不可变的值对象，只承载结果不负责计算。
 * 它汇总的是「整个书库」层面的数字，用于阅读统计界面的顶部概览。
 */
public class OverallStats {

    /** 书库中的书籍总数 */
    private final int totalBooks;

    /** 书库中的书摘总条数 */
    private final int totalExcerpts;

    /** 全局活跃天数：所有书摘的创建日期去重后的天数 */
    private final int totalActiveDays;

    /** 最近一次摘录时间；整个书库没有任何摘录时为 null */
    private final LocalDateTime lastExcerptAt;

    /**
     * 全字段构造函数。
     *
     * @param totalBooks     书籍总数
     * @param totalExcerpts  书摘总条数
     * @param totalActiveDays 全局活跃天数
     * @param lastExcerptAt  最近摘录时间，可为 null
     */
    public OverallStats(int totalBooks,
                        int totalExcerpts,
                        int totalActiveDays,
                        LocalDateTime lastExcerptAt) {
        // 逐个保存入参，构造完成后对象即不可变
        this.totalBooks = totalBooks;
        this.totalExcerpts = totalExcerpts;
        this.totalActiveDays = totalActiveDays;
        this.lastExcerptAt = lastExcerptAt;
    }

    /**
     * 获取书籍总数。
     *
     * @return 书籍总数
     */
    public int getTotalBooks() {
        return totalBooks;
    }

    /**
     * 获取书摘总条数。
     *
     * @return 书摘总条数
     */
    public int getTotalExcerpts() {
        return totalExcerpts;
    }

    /**
     * 获取全局活跃天数。
     *
     * @return 活跃天数
     */
    public int getTotalActiveDays() {
        return totalActiveDays;
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
     * 生成便于调试与日志输出的字符串。
     *
     * @return 包含全部汇总字段的描述文本
     */
    @Override
    public String toString() {
        // 拼出类似 OverallStats{totalBooks=2, totalExcerpts=5, ...} 的文本
        return "OverallStats{totalBooks=" + totalBooks
                + ", totalExcerpts=" + totalExcerpts
                + ", totalActiveDays=" + totalActiveDays
                + ", lastExcerptAt=" + lastExcerptAt
                + '}';
    }
}
