package com.mark.reader.service;

import com.mark.reader.exception.BookNotFoundException;
import com.mark.reader.model.Book;
import com.mark.reader.model.Excerpt;
import com.mark.reader.model.OverallStats;
import com.mark.reader.model.ReadingStats;
import com.mark.reader.repository.file.FileBookRepository;
import com.mark.reader.repository.file.FileExcerptRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StatisticsService} 的单元测试。
 * <p>
 * 这里直接使用仓储写入数据（而不经过服务层），目的是能精确控制每条书摘的创建时间，
 * 从而确定性地验证「活跃天数」「最近摘录」这类与时间强相关的统计口径。
 */
@DisplayName("StatisticsService 阅读统计")
class StatisticsServiceTest {

    /** 由 JUnit 注入的临时目录，用作数据目录 */
    @TempDir
    Path tempDir;

    /** 构造待测服务，两个仓储都指向临时目录 */
    private StatisticsService newService() {
        // 统计服务只需要两个仓储，不需要时钟
        return new StatisticsService(new FileBookRepository(tempDir),
                new FileExcerptRepository(tempDir));
    }

    /**
     * 直接往数据目录里写一本书，返回其主键。
     *
     * @param title      书名
     * @param totalPages 总页数
     * @return 书籍主键
     */
    private long addBook(String title, int totalPages) {
        // 组装实体并固定创建时间，避免影响统计结果
        Book book = new Book(title, "作者", totalPages);
        book.setCreatedAt(LocalDateTime.of(2026, 9, 1, 9, 0, 0));
        // 写入仓储
        return new FileBookRepository(tempDir).save(book).getId();
    }

    /**
     * 直接往数据目录里写一条书摘，创建时间由调用方指定。
     *
     * @param bookId    所属书籍主键
     * @param content   正文
     * @param page      页码
     * @param createdAt 创建时间
     */
    private void addExcerpt(long bookId, String content, int page, LocalDateTime createdAt) {
        // 组装实体
        Excerpt excerpt = new Excerpt(bookId, content, page, List.of());
        // 指定创建时间，便于控制活跃天数
        excerpt.setCreatedAt(createdAt);
        // 写入仓储
        new FileExcerptRepository(tempDir).save(excerpt);
    }

    /**
     * 校验空书库下的全局统计全部为零，且最近摘录时间为 null。
     */
    @Test
    @DisplayName("overall：空书库返回全零")
    void overallOnEmptyLibrary() {
        // 取全局统计
        OverallStats stats = newService().overall();
        // 各项计数都应为 0
        assertEquals(0, stats.getTotalBooks());
        assertEquals(0, stats.getTotalExcerpts());
        assertEquals(0, stats.getTotalActiveDays());
        // 没有摘录时最近时间应为 null
        assertNull(stats.getLastExcerptAt());
    }

    /**
     * 校验空书库时按书统计的列表为空。
     */
    @Test
    @DisplayName("forAllBooks：空书库返回空列表")
    void forAllBooksOnEmptyLibrary() {
        // 没有任何书时列表应为空
        assertTrue(newService().forAllBooks().isEmpty());
    }

    /**
     * 校验单本书的核心统计口径：条数、覆盖页码去重、活跃天数去重、最近摘录时间。
     */
    @Test
    @DisplayName("forBook：条数、覆盖页码、活跃天数与最近摘录时间")
    void forBookComputesCoreMetrics() {
        // 准备一本 191 页的书
        long bookId = addBook("活着", 191);
        // 三条书摘：页码 12、12、30，其中两条落在同一天
        addExcerpt(bookId, "第一段", 12, LocalDateTime.of(2026, 9, 20, 10, 0, 0));
        addExcerpt(bookId, "第二段", 12, LocalDateTime.of(2026, 9, 20, 20, 0, 0));
        addExcerpt(bookId, "第三段", 30, LocalDateTime.of(2026, 9, 21, 8, 0, 0));
        // 取统计结果
        ReadingStats stats = newService().forBook(bookId);
        // 书摘条数为 3
        assertEquals(3, stats.getExcerptCount());
        // 覆盖页码去重后为 2（12 与 30）
        assertEquals(2, stats.getCoveredPages());
        // 活跃天数去重后为 2（9 月 20 日与 9 月 21 日）
        assertEquals(2, stats.getActiveDays());
        // 最近摘录时间应为三条中最大的那个
        assertEquals(LocalDateTime.of(2026, 9, 21, 8, 0, 0), stats.getLastExcerptAt());
        // 书名与总页数应一并带回
        assertEquals("活着", stats.getBookTitle());
        assertEquals(191, stats.getTotalPages());
    }

    /**
     * 校验未标注页码的书摘不计入覆盖篇幅。
     */
    @Test
    @DisplayName("forBook：未标注页码的书摘不计入覆盖篇幅")
    void forBookIgnoresUnnumberedExcerpts() {
        // 准备一本书
        long bookId = addBook("活着", 191);
        // 两条标了页码，一条未标注（页码 0）
        addExcerpt(bookId, "已标页码一", 5, LocalDateTime.of(2026, 9, 20, 10, 0, 0));
        addExcerpt(bookId, "已标页码二", 6, LocalDateTime.of(2026, 9, 20, 11, 0, 0));
        addExcerpt(bookId, "未标页码", Excerpt.PAGE_UNKNOWN, LocalDateTime.of(2026, 9, 20, 12, 0, 0));
        // 取统计结果
        ReadingStats stats = newService().forBook(bookId);
        // 条数按全部书摘计，为 3
        assertEquals(3, stats.getExcerptCount());
        // 但覆盖页码只算标注过的那两个
        assertEquals(2, stats.getCoveredPages());
    }

    /**
     * 校验总页数已知时能算出覆盖率百分比。
     */
    @Test
    @DisplayName("forBook：总页数已知时算出覆盖率")
    void coveragePercentWhenTotalKnown() {
        // 准备一本 10 页的书
        long bookId = addBook("薄书", 10);
        // 标注了 2 个不同页码
        addExcerpt(bookId, "第一段", 1, LocalDateTime.of(2026, 9, 20, 10, 0, 0));
        addExcerpt(bookId, "第二段", 5, LocalDateTime.of(2026, 9, 20, 11, 0, 0));
        // 取统计结果
        ReadingStats stats = newService().forBook(bookId);
        // 总页数已知
        assertTrue(stats.isTotalPagesKnown());
        // 覆盖率应为 2 / 10 = 20%
        assertEquals(20.0, stats.coveragePercent(), 0.001);
    }

    /**
     * 校验总页数未知时无法计算覆盖率，返回 -1 由界面决定展示方式。
     */
    @Test
    @DisplayName("forBook：总页数未知时覆盖率为 -1")
    void coveragePercentUnknownWhenTotalMissing() {
        // 准备一本页数未知（填 0）的书
        long bookId = addBook("页数未知的书", 0);
        // 写一条书摘
        addExcerpt(bookId, "第一段", 3, LocalDateTime.of(2026, 9, 20, 10, 0, 0));
        // 取统计结果
        ReadingStats stats = newService().forBook(bookId);
        // 总页数未知
        assertFalse(stats.isTotalPagesKnown());
        // 覆盖率返回 -1 表示无法计算
        assertEquals(-1.0, stats.coveragePercent(), 0.001);
    }

    /**
     * 校验没有书摘的书统计结果为零，且最近摘录时间为 null。
     */
    @Test
    @DisplayName("forBook：没有书摘的书各项计数为零")
    void forBookWithoutExcerpts() {
        // 准备一本书但不写书摘
        long bookId = addBook("还没开始读", 100);
        // 取统计结果
        ReadingStats stats = newService().forBook(bookId);
        // 各项应为 0
        assertEquals(0, stats.getExcerptCount());
        assertEquals(0, stats.getCoveredPages());
        assertEquals(0, stats.getActiveDays());
        // 最近摘录时间为 null
        assertNull(stats.getLastExcerptAt());
    }

    /**
     * 校验排行榜按书摘数降序排列。
     */
    @Test
    @DisplayName("forAllBooks：按书摘数降序排列")
    void forAllBooksSortsByExcerptCountDescending() {
        // 准备三本书，书摘数分别为 1、3、2
        long oneBook = addBook("一条书摘", 100);
        long threeBook = addBook("三条书摘", 100);
        long twoBook = addBook("两条书摘", 100);
        // 一号书 1 条
        addExcerpt(oneBook, "A", 1, LocalDateTime.of(2026, 9, 20, 10, 0, 0));
        // 二号书 3 条
        addExcerpt(threeBook, "B1", 1, LocalDateTime.of(2026, 9, 21, 10, 0, 0));
        addExcerpt(threeBook, "B2", 2, LocalDateTime.of(2026, 9, 22, 10, 0, 0));
        addExcerpt(threeBook, "B3", 3, LocalDateTime.of(2026, 9, 23, 10, 0, 0));
        // 三号书 2 条
        addExcerpt(twoBook, "C1", 1, LocalDateTime.of(2026, 9, 24, 10, 0, 0));
        addExcerpt(twoBook, "C2", 2, LocalDateTime.of(2026, 9, 25, 10, 0, 0));
        // 取排行榜
        List<ReadingStats> ranking = newService().forAllBooks();
        // 应为三本
        assertEquals(3, ranking.size());
        // 顺序应为「三条书摘」>「两条书摘」>「一条书摘」
        assertEquals("三条书摘", ranking.get(0).getBookTitle());
        assertEquals("两条书摘", ranking.get(1).getBookTitle());
        assertEquals("一条书摘", ranking.get(2).getBookTitle());
    }

    /**
     * 校验全局汇总：书籍总数、书摘总数与跨书去重后的活跃天数。
     */
    @Test
    @DisplayName("overall：汇总书籍数、书摘数与全局活跃天数")
    void overallAggregatesAcrossBooks() {
        // 准备两本书
        long firstBook = addBook("甲书", 100);
        long secondBook = addBook("乙书", 100);
        // 甲书写两条，分别落在 9 月 20 日与 21 日
        addExcerpt(firstBook, "甲一", 1, LocalDateTime.of(2026, 9, 20, 10, 0, 0));
        addExcerpt(firstBook, "甲二", 2, LocalDateTime.of(2026, 9, 21, 10, 0, 0));
        // 乙书写两条，一条复用 9 月 21 日，一条落在 9 月 25 日
        addExcerpt(secondBook, "乙一", 1, LocalDateTime.of(2026, 9, 21, 20, 0, 0));
        addExcerpt(secondBook, "乙二", 2, LocalDateTime.of(2026, 9, 25, 10, 0, 0));
        // 取全局统计
        OverallStats stats = newService().overall();
        // 书籍总数为 2
        assertEquals(2, stats.getTotalBooks());
        // 书摘总数为 4
        assertEquals(4, stats.getTotalExcerpts());
        // 全局活跃天数为 3（9 月 20、21、25 日各算一天，同一天跨书合并）
        assertEquals(3, stats.getTotalActiveDays());
        // 最近摘录时间为 9 月 25 日
        assertEquals(LocalDateTime.of(2026, 9, 25, 10, 0, 0), stats.getLastExcerptAt());
    }

    /**
     * 校验对不存在的书籍求统计时抛出业务异常。
     */
    @Test
    @DisplayName("forBook：书籍不存在抛 BookNotFoundException")
    void forBookThrowsWhenBookMissing() {
        // 查一本不存在的书应当抛异常
        assertThrows(BookNotFoundException.class, () -> newService().forBook(999L));
    }
}
