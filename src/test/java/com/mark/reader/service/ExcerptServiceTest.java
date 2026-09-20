package com.mark.reader.service;

import com.mark.reader.exception.BookNotFoundException;
import com.mark.reader.exception.ValidationException;
import com.mark.reader.model.Book;
import com.mark.reader.model.Excerpt;
import com.mark.reader.repository.file.FileBookRepository;
import com.mark.reader.repository.file.FileExcerptRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ExcerptService} 的单元测试。
 * <p>
 * 重点覆盖四类业务规则：书籍必须存在、正文不能空白、页码不能越界、同一本书内内容不重复。
 * 另外验证标签规范化（去空白、去空项、去重）是否符合预期。
 */
@DisplayName("ExcerptService 书摘业务")
class ExcerptServiceTest {

    /** 由 JUnit 注入的临时目录，用作数据目录 */
    @TempDir
    Path tempDir;

    /** 固定时钟：把「现在」钉在 2026-09-20 15:31（UTC） */
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-20T15:31:00Z"), ZoneOffset.UTC);

    /** 固定时钟对应的本地时间，用于断言创建时间 */
    private static final LocalDateTime EXPECTED_TIME = LocalDateTime.of(2026, 9, 20, 15, 31, 0);

    /**
     * 构造待测服务，两个仓储都指向临时目录，时钟固定。
     *
     * @return 书摘服务实例
     */
    private ExcerptService newService() {
        // 书籍仓储与书摘仓储共用同一个临时数据目录
        return new ExcerptService(new FileExcerptRepository(tempDir),
                new FileBookRepository(tempDir), FIXED_CLOCK);
    }

    /**
     * 先往数据目录里放一本书，返回它的主键。
     *
     * @param title      书名
     * @param totalPages 总页数
     * @return 书籍主键
     */
    private long prepareBook(String title, int totalPages) {
        // 直接用仓储写入，绕开书籍服务的重名校验，方便准备测试数据
        Book book = new Book(title, "余华", totalPages);
        book.setCreatedAt(EXPECTED_TIME);
        return new FileBookRepository(tempDir).save(book).getId();
    }

    /**
     * 校验正常记录书摘：自动分配主键、正文去空白、创建时间取固定时钟。
     */
    @Test
    @DisplayName("addExcerpt：正常记录，分配主键并写入创建时间")
    void addExcerptSucceeds() {
        // 准备一本书
        long bookId = prepareBook("活着", 191);
        // 正文两侧故意留空格，验证会被裁剪
        Excerpt excerpt = newService().addExcerpt(bookId, "  人是为活着本身而活着  ", 12, List.of("文学"));
        // 主键应为 1
        assertEquals(1, excerpt.getId());
        // 正文应已去空白
        assertEquals("人是为活着本身而活着", excerpt.getContent());
        // 页码应保持不变
        assertEquals(12, excerpt.getPage());
        // 创建时间应等于固定时钟的时间
        assertEquals(EXPECTED_TIME, excerpt.getCreatedAt());
        // 所属书籍应正确关联
        assertEquals(bookId, excerpt.getBookId());
    }

    /**
     * 校验往不存在的书籍上挂书摘会被拒绝。
     */
    @Test
    @DisplayName("addExcerpt：书籍不存在抛 BookNotFoundException")
    void addExcerptRejectsMissingBook() {
        // 没有准备任何书籍，直接挂书摘应当失败
        assertThrows(BookNotFoundException.class,
                () -> newService().addExcerpt(999L, "正文", 1, null));
    }

    /**
     * 校验正文为 null 或纯空白时被拒绝。
     */
    @Test
    @DisplayName("addExcerpt：正文为 null 或空白抛 ValidationException")
    void addExcerptRejectsBlankContent() {
        // 准备一本书
        long bookId = prepareBook("活着", 191);
        // 准备服务
        ExcerptService service = newService();
        // null 正文不合法
        assertThrows(ValidationException.class, () -> service.addExcerpt(bookId, null, 1, null));
        // 纯空白正文同样不合法
        assertThrows(ValidationException.class, () -> service.addExcerpt(bookId, "   ", 1, null));
    }

    /**
     * 校验页码为负数被拒绝，而 0（未标注）是允许的。
     */
    @Test
    @DisplayName("addExcerpt：页码为负被拒绝，0 表示未标注可接受")
    void addExcerptValidatesPageNumber() {
        // 准备一本书
        long bookId = prepareBook("活着", 191);
        // 准备服务
        ExcerptService service = newService();
        // 负数页码不合法
        assertThrows(ValidationException.class, () -> service.addExcerpt(bookId, "正文", -1, null));
        // 0 表示未标注，应当允许
        assertEquals(Excerpt.PAGE_UNKNOWN, service.addExcerpt(bookId, "正文", 0, null).getPage());
    }

    /**
     * 校验总页数已知时页码越界会被拒绝。
     */
    @Test
    @DisplayName("addExcerpt：页码超出总页数抛 ValidationException")
    void addExcerptRejectsPageBeyondTotal() {
        // 准备一本 191 页的书
        long bookId = prepareBook("活着", 191);
        // 准备服务
        ExcerptService service = newService();
        // 页码 192 超出了总页数
        assertThrows(ValidationException.class, () -> service.addExcerpt(bookId, "正文", 192, null));
        // 页码刚好等于总页数是允许的边界情况
        assertEquals(191, service.addExcerpt(bookId, "正文", 191, null).getPage());
    }

    /**
     * 校验总页数未知（填 0）时不做过严的页码限制。
     */
    @Test
    @DisplayName("addExcerpt：总页数未知时不限制页码上限")
    void addExcerptAllowsAnyPageWhenTotalUnknown() {
        // 准备一本页数未知的书
        long bookId = prepareBook("页数未知的书", 0);
        // 页码写到 9999 也应当允许
        assertEquals(9999, newService().addExcerpt(bookId, "正文", 9999, null).getPage());
    }

    /**
     * 校验同一本书内内容相同的书摘不会重复入库，而是返回已存在的那一条。
     */
    @Test
    @DisplayName("addExcerpt：同一本书内容重复时返回已有记录")
    void addExcerptDeduplicatesWithinSameBook() {
        // 准备一本书
        long bookId = prepareBook("活着", 191);
        // 准备服务
        ExcerptService service = newService();
        // 第一次正常写入
        Excerpt first = service.addExcerpt(bookId, "人是为活着本身而活着", 12, null);
        // 第二次写入内容相同（含首尾空白差异）的书摘
        Excerpt second = service.addExcerpt(bookId, "  人是为活着本身而活着  ", 30, List.of("另一批标签"));
        // 返回的应当是第一次那条记录（主键相同），而不是新建一条
        assertEquals(first.getId(), second.getId());
        // 页码也应保持首次录入的值，说明没有被覆盖
        assertEquals(12, second.getPage());
        // 该书书摘总数仍为 1
        assertEquals(1, service.listByBook(bookId).size());
    }

    /**
     * 校验不同书籍中出现同一句话是允许的，不会被当成重复。
     */
    @Test
    @DisplayName("addExcerpt：跨书相同内容不算重复")
    void addExcerptAllowsSameContentInDifferentBooks() {
        // 准备两本书
        long firstBookId = prepareBook("甲书", 100);
        long secondBookId = prepareBook("乙书", 100);
        // 准备服务
        ExcerptService service = newService();
        // 两本书各写入同一句话
        service.addExcerpt(firstBookId, "同一句话", 1, null);
        service.addExcerpt(secondBookId, "同一句话", 1, null);
        // 各自的条数都应为 1
        assertEquals(1, service.listByBook(firstBookId).size());
        assertEquals(1, service.listByBook(secondBookId).size());
    }

    /**
     * 校验标签规范化：去掉首尾空白、丢弃空标签、按顺序去重。
     */
    @Test
    @DisplayName("addExcerpt：标签去空白、去空项、去重且保持顺序")
    void addExcerptNormalizesTags() {
        // 准备一本书
        long bookId = prepareBook("活着", 191);
        // 传入带空白、空字符串与重复项的标签列表
        List<String> rawTags = Arrays.asList("  文学  ", "", "  ", "人生", "文学", null);
        // 记录书摘
        Excerpt excerpt = newService().addExcerpt(bookId, "正文", 1, rawTags);
        // 结果应当只剩「文学」「人生」两个，且顺序按首次出现排列
        assertEquals(List.of("文学", "人生"), excerpt.getTags());
    }

    /**
     * 校验不传标签时得到空列表而不是 null。
     */
    @Test
    @DisplayName("addExcerpt：不传标签时标签为空列表")
    void addExcerptWithoutTags() {
        // 准备一本书
        long bookId = prepareBook("活着", 191);
        // 标签传 null
        Excerpt excerpt = newService().addExcerpt(bookId, "正文", 1, null);
        // 应当得到空列表而非 null
        assertTrue(excerpt.getTags().isEmpty());
    }

    /**
     * 校验按书查询返回该书全部书摘，且不混入其他书的记录。
     */
    @Test
    @DisplayName("listByBook：只返回该书书摘")
    void listByBookReturnsOnlyThatBook() {
        // 准备两本书
        long firstBookId = prepareBook("甲书", 100);
        long secondBookId = prepareBook("乙书", 100);
        // 准备服务并写入书摘
        ExcerptService service = newService();
        service.addExcerpt(firstBookId, "甲书第一段", 1, null);
        service.addExcerpt(firstBookId, "甲书第二段", 2, null);
        service.addExcerpt(secondBookId, "乙书第一段", 1, null);
        // 查询甲书应得到两条
        List<Excerpt> ofFirst = service.listByBook(firstBookId);
        assertEquals(2, ofFirst.size());
        // 且全部属于甲书
        assertTrue(ofFirst.stream().allMatch(e -> e.getBookId() == firstBookId));
    }

    /**
     * 校验查询不存在的书籍时抛出业务异常，而不是返回空列表。
     */
    @Test
    @DisplayName("listByBook：书籍不存在抛 BookNotFoundException")
    void listByBookThrowsWhenBookMissing() {
        // 查一本不存在的书应当抛异常，避免与「确实没有摘录」混淆
        assertThrows(BookNotFoundException.class, () -> newService().listByBook(999L));
    }
}
