package com.mark.reader.cli;

import com.mark.reader.model.Book;
import com.mark.reader.model.Excerpt;
import com.mark.reader.repository.BookRepository;
import com.mark.reader.repository.ExcerptRepository;
import com.mark.reader.repository.file.FileBookRepository;
import com.mark.reader.repository.file.FileExcerptRepository;
import com.mark.reader.service.BookService;
import com.mark.reader.service.ExcerptService;
import com.mark.reader.service.StatisticsService;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CommandLineApp} 的单元测试。
 * <p>
 * 借助 {@link FakeConsoleIO} 预设输入并捕获输出，覆盖主菜单的每一个分支：
 * 退出、添加书籍、记录书摘、按书查询、阅读统计，以及各类异常路径。
 * 由于界面直接依赖真实服务与文件仓储，这些用例同时也是整条链路（界面→服务→仓储）的端到端验证。
 */
@DisplayName("CommandLineApp 命令行界面")
class CommandLineAppTest {

    /** 由 JUnit 注入的临时目录，用作数据目录 */
    @TempDir
    Path tempDir;

    /** 固定时钟，让界面回显的时间稳定可断言 */
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-20T07:30:00Z"), ZoneOffset.UTC);

    /** 书籍仓储，测试中可直接用来准备数据或校验结果 */
    private BookRepository bookRepository;

    /** 书摘仓储，测试中可直接用来准备数据或校验结果 */
    private ExcerptRepository excerptRepository;

    /**
     * 每个用例前重建仓储，保证测试之间完全隔离。
     */
    @BeforeEach
    void setUp() {
        // 两个仓储共用同一个临时数据目录
        bookRepository = new FileBookRepository(tempDir);
        excerptRepository = new FileExcerptRepository(tempDir);
    }

    /**
     * 组装一个使用假输入输出的界面实例。
     *
     * @param io 假输入输出通道
     * @return 命令行界面实例
     */
    private CommandLineApp newApp(FakeConsoleIO io) {
        // 三个服务共享上面建立的仓储与固定时钟
        return new CommandLineApp(io,
                new BookService(bookRepository, FIXED_CLOCK),
                new ExcerptService(excerptRepository, bookRepository, FIXED_CLOCK),
                new StatisticsService(bookRepository, excerptRepository));
    }

    /**
     * 直接往仓储里写一本书，用于准备测试数据。
     *
     * @param title 书名
     * @param pages 总页数
     * @return 保存后的书籍
     */
    private Book prepareBook(String title, int pages) {
        // 组装实体并补上创建时间
        Book book = new Book(title, "余华", pages);
        book.setCreatedAt(LocalDateTime.of(2026, 9, 20, 9, 0, 0));
        // 写入仓储
        return bookRepository.save(book);
    }

    /**
     * 直接往仓储里写一条书摘，用于准备测试数据。
     *
     * @param bookId  所属书籍主键
     * @param content 正文
     * @param page    页码
     */
    private void prepareExcerpt(long bookId, String content, int page) {
        // 组装实体并补上创建时间
        Excerpt excerpt = new Excerpt(bookId, content, page, List.of("文学"));
        excerpt.setCreatedAt(LocalDateTime.of(2026, 9, 20, 10, 0, 0));
        // 写入仓储
        excerptRepository.save(excerpt);
    }

    /**
     * 校验选择 0 时程序正常退出并给出告别语。
     */
    @Test
    @DisplayName("菜单：选择 0 退出")
    void exitsWhenZeroChosen() {
        // 只提供一次输入 0
        FakeConsoleIO io = new FakeConsoleIO("0");
        // 启动界面
        newApp(io).run();
        // 输出里应当出现退出提示
        assertTrue(io.output().contains("已退出"));
    }

    /**
     * 校验输入流提前结束时程序不会死循环，而是自动退出。
     */
    @Test
    @DisplayName("菜单：输入流结束自动退出")
    void exitsWhenInputExhausted() {
        // 不提供任何输入，模拟管道被关闭
        FakeConsoleIO io = new FakeConsoleIO();
        // 启动界面应当立刻返回
        newApp(io).run();
        // 输出里应当出现输入结束提示
        assertTrue(io.output().contains("输入已结束"));
    }

    /**
     * 校验菜单选项越界时给出提示并继续询问，而不是直接崩溃。
     */
    @Test
    @DisplayName("菜单：选项越界时提示并继续")
    void rejectsOutOfRangeChoice() {
        // 先输入一个越界的 9，再输入 0 退出
        FakeConsoleIO io = new FakeConsoleIO("9", "0");
        // 启动界面
        newApp(io).run();
        // 应当出现范围提示
        assertTrue(io.output().contains("输入超出范围"));
        // 之后仍然正常退出
        assertTrue(io.output().contains("已退出"));
    }

    /**
     * 校验「添加书籍」分支：输入合法数据后提示成功，且数据确实落到了仓储里。
     */
    @Test
    @DisplayName("功能一：添加书籍成功并落库")
    void addsBookThroughMenu() {
        // 依次输入：菜单 1、书名、作者、页数，最后 0 退出
        FakeConsoleIO io = new FakeConsoleIO("1", "活着", "余华", "191", "0");
        // 启动界面
        newApp(io).run();
        // 输出里应当出现成功提示与书名
        assertTrue(io.output().contains("添加成功"));
        assertTrue(io.output().contains("活着"));
        // 仓储里应当真的有这本书
        List<Book> books = bookRepository.findAll();
        assertEquals(1, books.size());
        assertEquals("活着", books.get(0).getTitle());
        assertEquals(191, books.get(0).getTotalPages());
    }

    /**
     * 校验重复添加同名书籍时，界面把校验异常转换成友好提示而不是打印异常栈。
     */
    @Test
    @DisplayName("功能一：书名重复时提示操作失败")
    void reportsDuplicateBookTitle() {
        // 连续两次添加同名书籍，最后退出
        FakeConsoleIO io = new FakeConsoleIO(
                "1", "活着", "余华", "191",
                "1", "活着", "某人", "100",
                "0");
        // 启动界面
        newApp(io).run();
        // 应当出现失败提示，并且提示里带上书名
        assertTrue(io.output().contains("操作失败"));
        assertTrue(io.output().contains("书名已存在"));
        // 仓储里仍然只有一本
        assertEquals(1, bookRepository.findAll().size());
    }

    /**
     * 校验「记录书摘」分支：能选中书籍、录入正文页码标签，并正确落库。
     */
    @Test
    @DisplayName("功能二：记录书摘成功并落库")
    void addsExcerptThroughMenu() {
        // 先准备一本书
        Book book = prepareBook("活着", 191);
        // 依次输入：菜单 2、书籍序号 1、正文、页码、标签，最后 0 退出
        FakeConsoleIO io = new FakeConsoleIO("2", "1", "人是为活着本身而活着", "12", "文学,人生", "0");
        // 启动界面
        newApp(io).run();
        // 输出里应当出现成功提示
        assertTrue(io.output().contains("记录成功"));
        // 仓储里应当真的有一条书摘
        List<Excerpt> excerpts = excerptRepository.findByBookId(book.getId());
        assertEquals(1, excerpts.size());
        assertEquals("人是为活着本身而活着", excerpts.get(0).getContent());
        assertEquals(12, excerpts.get(0).getPage());
        // 标签应当被拆成两个
        assertEquals(List.of("文学", "人生"), excerpts.get(0).getTags());
    }

    /**
     * 校验书库为空时记录书摘会给出明确提示而不是让用户对着空列表发愣。
     */
    @Test
    @DisplayName("功能二：书库为空时给出提示")
    void reportsEmptyLibraryWhenAddingExcerpt() {
        // 选择菜单 2，然后退出
        FakeConsoleIO io = new FakeConsoleIO("2", "0");
        // 启动界面
        newApp(io).run();
        // 应当提示书库为空
        assertTrue(io.output().contains("书库还是空的"));
    }

    /**
     * 校验「按书查询书摘」分支：关键字留空时列出全部书籍，选中后展示书摘明细。
     */
    @Test
    @DisplayName("功能三：按书查询书摘并展示明细")
    void queriesExcerptsThroughMenu() {
        // 先准备一本书与两条书摘
        Book book = prepareBook("活着", 191);
        prepareExcerpt(book.getId(), "人是为活着本身而活着", 12);
        prepareExcerpt(book.getId(), "做人还是平常点好", 30);
        // 依次输入：菜单 3、关键字留空、书籍序号 1，最后 0 退出
        FakeConsoleIO io = new FakeConsoleIO("3", "", "1", "0");
        // 启动界面
        newApp(io).run();
        // 输出里应当出现书名与两条书摘的正文
        assertTrue(io.output().contains("活着"));
        assertTrue(io.output().contains("人是为活着本身而活着"));
        assertTrue(io.output().contains("做人还是平常点好"));
        // 还应当出现页码与标签
        assertTrue(io.output().contains("第 12 页"));
        assertTrue(io.output().contains("文学"));
    }

    /**
     * 校验关键字没有匹配到任何书籍时给出提示。
     */
    @Test
    @DisplayName("功能三：关键字无匹配时给出提示")
    void reportsNoMatchedBook() {
        // 准备一本书，但用一个匹配不到的关键字查询
        prepareBook("活着", 191);
        // 依次输入：菜单 3、关键字、退出
        FakeConsoleIO io = new FakeConsoleIO("3", "不存在的关键字", "0");
        // 启动界面
        newApp(io).run();
        // 应当提示没有匹配的书籍
        assertTrue(io.output().contains("没有匹配的书籍"));
    }

    /**
     * 校验书摘为空的书籍在查询时给出明确提示。
     */
    @Test
    @DisplayName("功能三：书籍没有书摘时给出提示")
    void reportsBookWithoutExcerpts() {
        // 准备一本没有任何书摘的书
        prepareBook("还没开始读", 100);
        // 依次输入：菜单 3、关键字留空、书籍序号 1、退出
        FakeConsoleIO io = new FakeConsoleIO("3", "", "1", "0");
        // 启动界面
        newApp(io).run();
        // 应当提示该书还没有书摘
        assertTrue(io.output().contains("还没有书摘"));
    }

    /**
     * 校验「阅读统计」分支：打印全局汇总与各书明细。
     */
    @Test
    @DisplayName("功能四：阅读统计输出汇总与明细")
    void showsStatisticsThroughMenu() {
        // 准备一本书与两条跨天的书摘
        Book book = prepareBook("活着", 191);
        prepareExcerpt(book.getId(), "第一段", 12);
        prepareExcerpt(book.getId(), "第二段", 30);
        // 依次输入：菜单 4、退出
        FakeConsoleIO io = new FakeConsoleIO("4", "0");
        // 启动界面
        newApp(io).run();
        // 输出里应当出现汇总指标
        assertTrue(io.output().contains("书籍总数：1 本"));
        assertTrue(io.output().contains("书摘总数：2 条"));
        assertTrue(io.output().contains("活跃天数"));
        // 也应当出现明细区块
        assertTrue(io.output().contains("各书明细"));
        assertTrue(io.output().contains("覆盖 2/191 页"));
        // 两条书摘创建时间相同，活跃天数应为 1
        assertTrue(io.output().contains("活跃 1 天"));
    }

    /**
     * 校验书库为空时查看统计不会报错，而是给出友好提示。
     */
    @Test
    @DisplayName("功能四：书库为空时给出提示")
    void showsEmptyLibraryStatistics() {
        // 依次输入：菜单 4、退出
        FakeConsoleIO io = new FakeConsoleIO("4", "0");
        // 启动界面
        newApp(io).run();
        // 汇总部分应当显示 0
        assertTrue(io.output().contains("书籍总数：0 本"));
        // 明细部分应当提示书库为空
        assertTrue(io.output().contains("书库还是空的"));
    }

    /**
     * 校验菜单在完成一次操作后会重新显示，支持连续多次操作。
     */
    @Test
    @DisplayName("菜单：完成一次操作后重新显示，支持连续操作")
    void menuIsShownAgainAfterEachAction() {
        // 连续添加两本书后退出
        FakeConsoleIO io = new FakeConsoleIO(
                "1", "甲书", "作者甲", "100",
                "1", "乙书", "作者乙", "200",
                "0");
        // 启动界面
        newApp(io).run();
        // 仓储里应当有两本书
        assertEquals(2, bookRepository.findAll().size());
        // 输出里「添加成功」应当出现两次
        assertEquals(2, countOccurrences(io.output(), "添加成功"));
    }

    /**
     * 统计一段文本中某个子串出现的次数。
     *
     * @param text   被检索的文本
     * @param target 目标子串
     * @return 出现次数
     */
    private int countOccurrences(String text, String target) {
        // 从 0 开始累计出现次数
        int count = 0;
        // 从上次命中位置之后继续查找，避免重复计数
        int index = text.indexOf(target);
        while (index >= 0) {
            count++;
            index = text.indexOf(target, index + target.length());
        }
        // 返回总次数
        return count;
    }

    /**
     * 校验空书库场景下查询功能不会抛异常。
     */
    @Test
    @DisplayName("功能三：空书库查询不会抛异常")
    void queryOnEmptyLibraryDoesNotThrow() {
        // 依次输入：菜单 3、关键字留空、退出
        FakeConsoleIO io = new FakeConsoleIO("3", "", "0");
        // 启动界面应当正常结束
        newApp(io).run();
        // 应当提示没有匹配的书籍
        assertTrue(io.output().contains("没有匹配的书籍"));
        // 输出里不应出现任何异常痕迹
        assertFalse(io.output().contains("Exception"));
    }
}
