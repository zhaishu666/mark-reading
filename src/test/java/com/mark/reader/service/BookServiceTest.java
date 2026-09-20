package com.mark.reader.service;

import com.mark.reader.exception.BookNotFoundException;
import com.mark.reader.exception.ValidationException;
import com.mark.reader.model.Book;
import com.mark.reader.repository.file.FileBookRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BookService} 的单元测试。
 * <p>
 * 使用真实的文件仓储配合临时目录，因此这些用例同时也是「服务层 + 仓储层」的联调测试。
 * 时间通过固定时钟注入，保证创建时间断言稳定，不会因为运行时刻不同而时好时坏。
 */
@DisplayName("BookService 书籍业务")
class BookServiceTest {

    /** 由 JUnit 注入的临时目录，用作数据目录 */
    @TempDir
    Path tempDir;

    /** 固定时钟：把「现在」钉在 2026-09-20 07:30（UTC） */
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-20T07:30:00Z"), ZoneOffset.UTC);

    /** 固定时钟对应的本地时间，用于断言创建时间 */
    private static final LocalDateTime EXPECTED_TIME = LocalDateTime.of(2026, 9, 20, 7, 30, 0);

    /**
     * 构造待测服务，仓储指向临时目录，时钟固定。
     *
     * @return 书籍服务实例
     */
    private BookService newService() {
        // 仓储与时钟都由本方法提供，测试无需关心装配细节
        return new BookService(new FileBookRepository(tempDir), FIXED_CLOCK);
    }

    /**
     * 校验正常添加：自动分配主键、书名去空白、创建时间取固定时钟的时间。
     */
    @Test
    @DisplayName("addBook：正常添加，分配主键并写入创建时间")
    void addBookSucceeds() {
        // 准备服务
        BookService service = newService();
        // 书名两侧故意留空格，验证会被裁剪
        Book book = service.addBook("  活着  ", " 余华 ", 191);
        // 主键应为 1
        assertEquals(1, book.getId());
        // 书名应已去掉首尾空白
        assertEquals("活着", book.getTitle());
        // 作者同样去掉空白
        assertEquals("余华", book.getAuthor());
        // 创建时间应等于固定时钟的时间
        assertEquals(EXPECTED_TIME, book.getCreatedAt());
        // 新书的阅读进度为 0
        assertEquals(0, book.getCurrentPage());
    }

    /**
     * 校验书名为 null 时被拒绝。
     */
    @Test
    @DisplayName("addBook：书名为 null 抛 ValidationException")
    void addBookRejectsNullTitle() {
        // null 书名不合法
        assertThrows(ValidationException.class, () -> newService().addBook(null, "余华", 191));
    }

    /**
     * 校验纯空白的书名被拒绝。
     */
    @Test
    @DisplayName("addBook：书名为纯空白抛 ValidationException")
    void addBookRejectsBlankTitle() {
        // 三个空格同样不合法
        assertThrows(ValidationException.class, () -> newService().addBook("   ", "余华", 191));
    }

    /**
     * 校验负的页数被拒绝，而 0 作为「未知」是允许的。
     */
    @Test
    @DisplayName("addBook：页数为负被拒绝，0 表示未知可接受")
    void addBookValidatesTotalPages() {
        // 负数页数不合法
        assertThrows(ValidationException.class, () -> newService().addBook("活着", "余华", -1));
        // 0 表示页数未知，应当允许
        assertEquals(0, newService().addBook("活着", "余华", 0).getTotalPages());
    }

    /**
     * 校验重复书名被拒绝，且判断时忽略首尾空白与大小写。
     */
    @Test
    @DisplayName("addBook：书名重复抛 ValidationException（忽略大小写与空白）")
    void addBookRejectsDuplicateTitle() {
        // 先添加一本书
        BookService service = newService();
        service.addBook("Effective Java", "Joshua Bloch", 400);
        // 同名书应当被拒绝
        assertThrows(ValidationException.class, () -> service.addBook("Effective Java", "某人", 100));
        // 大小写不同、且带前后空格的同名书同样应被拒绝
        assertThrows(ValidationException.class, () -> service.addBook("  effective java  ", "某人", 100));
        // 换一个书名就能正常添加
        assertEquals(2, service.addBook("Effective Kotlin", "Marcin Moskala", 300).getId());
    }

    /**
     * 校验按主键查询命中时返回对应书籍。
     */
    @Test
    @DisplayName("getById：命中返回书籍")
    void getByIdReturnsBook() {
        // 先添加一本书
        BookService service = newService();
        Book saved = service.addBook("活着", "余华", 191);
        // 按主键查回应与保存结果一致
        assertEquals("活着", service.getById(saved.getId()).getTitle());
    }

    /**
     * 校验按主键查询未命中时抛出业务异常。
     */
    @Test
    @DisplayName("getById：主键不存在抛 BookNotFoundException")
    void getByIdThrowsWhenMissing() {
        // 查一个不存在的 id 应当抛异常
        assertThrows(BookNotFoundException.class, () -> newService().getById(999L));
    }

    /**
     * 校验关键字为空时返回全部书籍。
     */
    @Test
    @DisplayName("searchByKeyword：空关键字返回全部书籍")
    void searchByKeywordReturnsAllWhenBlank() {
        // 准备三本书
        BookService service = newService();
        service.addBook("活着", "余华", 191);
        service.addBook("许三观卖血记", "余华", 254);
        service.addBook("Effective Java", "Joshua Bloch", 400);
        // null 关键字返回全部
        assertEquals(3, service.searchByKeyword(null).size());
        // 纯空白关键字也返回全部
        assertEquals(3, service.searchByKeyword("   ").size());
    }

    /**
     * 校验按关键字模糊过滤，且忽略大小写。
     */
    @Test
    @DisplayName("searchByKeyword：按关键字过滤并忽略大小写")
    void searchByKeywordFilters() {
        // 准备三本书
        BookService service = newService();
        service.addBook("活着", "余华", 191);
        service.addBook("许三观卖血记", "余华", 254);
        service.addBook("Effective Java", "Joshua Bloch", 400);
        // 按中文书名片段搜索应命中许三观那一本
        assertEquals(1, service.searchByKeyword("卖血").size());
        // 英文关键字用小写搜索也应命中（忽略大小写）
        assertEquals(1, service.searchByKeyword("effective").size());
        // 书名匹配不到任何一本书时应返回空列表
        assertTrue(service.searchByKeyword("不存在的书名").isEmpty());
    }

    /**
     * 校验 listAll 返回全部书籍并按主键升序。
     */
    @Test
    @DisplayName("listAll：返回全部书籍")
    void listAllReturnsEverything() {
        // 准备两本书
        BookService service = newService();
        service.addBook("甲", "", 10);
        service.addBook("乙", "", 20);
        // 取出全部
        List<Book> books = service.listAll();
        // 条数与顺序都应正确
        assertEquals(2, books.size());
        assertEquals("甲", books.get(0).getTitle());
        assertEquals("乙", books.get(1).getTitle());
    }
}
