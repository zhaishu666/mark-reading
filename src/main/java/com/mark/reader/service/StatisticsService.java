package com.mark.reader.service;

import com.mark.reader.exception.BookNotFoundException;
import com.mark.reader.model.Book;
import com.mark.reader.model.Excerpt;
import com.mark.reader.model.OverallStats;
import com.mark.reader.model.ReadingStats;
import com.mark.reader.repository.BookRepository;
import com.mark.reader.repository.ExcerptRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 阅读统计服务。
 * <p>
 * 本类只做聚合计算，不写任何数据，因此不需要时钟依赖。
 * 统计口径在这里被明确固化下来，避免同一组数据在不同界面上算出不同结果：
 * <ul>
 *   <li>书摘数：该书摘录条数</li>
 *   <li>覆盖篇幅：该书出现过书摘的「不同页码」数量，未标注页码的书摘不计入</li>
 *   <li>活跃天数：摘录创建日期去重后的天数</li>
 *   <li>最近摘录：该书最新一条摘录的创建时间</li>
 * </ul>
 */
public class StatisticsService {

    /** 书籍仓储，用于取书籍清单 */
    private final BookRepository bookRepository;

    /** 书摘仓储，用于取书摘数据作为统计原料 */
    private final ExcerptRepository excerptRepository;

    /**
     * 构造函数。
     *
     * @param bookRepository    书籍仓储
     * @param excerptRepository 书摘仓储
     */
    public StatisticsService(BookRepository bookRepository, ExcerptRepository excerptRepository) {
        // 保存书籍仓储引用
        this.bookRepository = bookRepository;
        // 保存书摘仓储引用
        this.excerptRepository = excerptRepository;
    }

    /**
     * 统计单本书的阅读情况。
     *
     * @param bookId 书籍主键
     * @return 该书的统计结果
     * @throws BookNotFoundException 书籍不存在时抛出
     */
    public ReadingStats forBook(long bookId) {
        // 查不到书就没有统计的必要
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
        // 用这本书的书摘数据算出统计结果
        return buildStats(book, excerptRepository.findByBookId(bookId));
    }

    /**
     * 统计书库中全部书籍，并按书摘数从多到少排序，形成「最投入的书」排行。
     *
     * @return 统计结果列表，书摘数多的在前，相同则按书名升序
     */
    public List<ReadingStats> forAllBooks() {
        // 准备承载结果
        List<ReadingStats> result = new ArrayList<>();
        // 逐本计算
        for (Book book : bookRepository.findAll()) {
            result.add(buildStats(book, excerptRepository.findByBookId(book.getId())));
        }
        // 先按书摘数降序，条数相同时按书名升序，保证输出顺序稳定可复现
        result.sort(Comparator.comparingInt(ReadingStats::getExcerptCount).reversed()
                .thenComparing(ReadingStats::getBookTitle, Comparator.nullsLast(Comparator.naturalOrder())));
        // 返回排序后的结果
        return result;
    }

    /**
     * 统计整个书库的汇总数据。
     *
     * @return 汇总统计结果
     */
    public OverallStats overall() {
        // 取出全部书摘作为原料
        List<Excerpt> allExcerpts = excerptRepository.findAll();
        // 书籍总数直接取书籍清单长度
        int totalBooks = bookRepository.findAll().size();
        // 全局活跃天数：所有摘录日期去重
        int totalActiveDays = distinctDayCount(allExcerpts);
        // 最近摘录时间取所有摘录中的最大值
        LocalDateTime lastExcerptAt = latestTime(allExcerpts);
        // 组装汇总对象返回
        return new OverallStats(totalBooks, allExcerpts.size(), totalActiveDays, lastExcerptAt);
    }

    /**
     * 根据书籍与其书摘列表计算统计结果。
     *
     * @param book     书籍
     * @param excerpts 该书的书摘列表
     * @return 统计结果
     */
    private ReadingStats buildStats(Book book, List<Excerpt> excerpts) {
        // 书摘条数就是列表长度
        int excerptCount = excerpts.size();
        // 覆盖篇幅：只统计标注了页码的书摘，并对页码去重
        Set<Integer> coveredPageSet = new LinkedHashSet<>();
        for (Excerpt excerpt : excerpts) {
            // 未标注页码的书摘无法参与篇幅统计，跳过
            if (excerpt.hasPage()) {
                coveredPageSet.add(excerpt.getPage());
            }
        }
        // 活跃天数按创建日期去重
        int activeDays = distinctDayCount(excerpts);
        // 最近摘录时间取最大值
        LocalDateTime lastExcerptAt = latestTime(excerpts);
        // 组装统计结果
        return new ReadingStats(book.getId(), book.getTitle(), excerptCount,
                coveredPageSet.size(), book.getTotalPages(), activeDays, lastExcerptAt);
    }

    /**
     * 统计一组书摘覆盖了多少个不同的日期。
     * 创建时间为 null 的记录会被忽略，防止脏数据把天数算多。
     *
     * @param excerpts 书摘列表
     * @return 去重后的天数
     */
    private int distinctDayCount(List<Excerpt> excerpts) {
        // 用 Set 天然去重
        Set<LocalDate> days = new LinkedHashSet<>();
        // 逐个提取日期
        for (Excerpt excerpt : excerpts) {
            // 跳过没有创建时间的记录
            if (excerpt.getCreatedAt() != null) {
                days.add(excerpt.getCreatedAt().toLocalDate());
            }
        }
        // 返回天数
        return days.size();
    }

    /**
     * 取一组书摘中最近的创建时间。
     *
     * @param excerpts 书摘列表
     * @return 最大创建时间，全部为 null 或列表为空时返回 null
     */
    private LocalDateTime latestTime(List<Excerpt> excerpts) {
        // 流式取最大值，全程忽略 null
        return excerpts.stream()
                .map(Excerpt::getCreatedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }
}
