package com.mark.reader.cli;

import com.mark.reader.exception.BookNotFoundException;
import com.mark.reader.exception.ValidationException;
import com.mark.reader.model.Book;
import com.mark.reader.model.Excerpt;
import com.mark.reader.model.OverallStats;
import com.mark.reader.model.ReadingStats;
import com.mark.reader.service.BookService;
import com.mark.reader.service.ExcerptService;
import com.mark.reader.service.StatisticsService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 命令行交互界面。
 * <p>
 * 本类只负责三件事：把菜单画出来、把用户的按键翻译成一次业务调用、把结果打印成人能看懂的样子。
 * 任何业务判断（书名是否重复、页码是否越界）都不写在这里，而是交给服务层，
 * 这样界面逻辑足够薄，换成图形界面时丢弃即可，业务代码不受影响。
 * <p>
 * 依赖通过构造参数注入，测试时可以传入假的输入输出实现，从而对每个菜单分支做断言。
 */
public class CommandLineApp {

    /** 菜单里允许输入的最小选项值，0 表示退出 */
    private static final int MENU_MIN = 0;

    /** 菜单里允许输入的最大选项值 */
    private static final int MENU_MAX = 4;

    /** 页数未知时允许录入的页码上限，防止用户手滑敲出天文数字 */
    private static final int MAX_PAGE_INPUT = 100_000;

    /** 时间展示格式，精确到分钟即可满足阅读记录场景 */
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 输入输出通道，可替换为测试用实现 */
    private final ConsoleIO io;

    /** 书籍业务服务 */
    private final BookService bookService;

    /** 书摘业务服务 */
    private final ExcerptService excerptService;

    /** 统计业务服务 */
    private final StatisticsService statisticsService;

    /**
     * 构造函数，注入界面所需的全部依赖。
     *
     * @param io                输入输出通道
     * @param bookService       书籍业务服务
     * @param excerptService    书摘业务服务
     * @param statisticsService 统计业务服务
     */
    public CommandLineApp(ConsoleIO io,
                          BookService bookService,
                          ExcerptService excerptService,
                          StatisticsService statisticsService) {
        // 保存输入输出通道
        this.io = io;
        // 保存书籍服务
        this.bookService = bookService;
        // 保存书摘服务
        this.excerptService = excerptService;
        // 保存统计服务
        this.statisticsService = statisticsService;
    }

    /**
     * 启动主循环，直到用户选择退出或输入流结束。
     * 业务异常在这里被统一捕获并转成友好提示，避免异常栈直接糊在用户脸上。
     */
    public void run() {
        // 打印欢迎语
        io.printLine("========== 马克阅读 ==========");
        // 循环处理用户操作
        while (true) {
            // 每轮先画菜单
            printMenu();
            // 读取用户选择；返回 null 说明输入流已结束
            Integer choice = io.readInt("请选择操作：", MENU_MIN, MENU_MAX);
            // 输入结束或用户选择 0 都表示要退出
            if (choice == null) {
                io.printLine("输入已结束，程序退出。");
                return;
            }
            if (choice == 0) {
                io.printLine("已退出，感谢使用马克阅读。");
                return;
            }
            // 分发到对应的处理分支，业务异常在此统一兜底
            try {
                switch (choice) {
                    // 1：添加书籍
                    case 1 -> handleAddBook();
                    // 2：记录书摘
                    case 2 -> handleAddExcerpt();
                    // 3：按书查询书摘
                    case 3 -> handleQueryExcerpts();
                    // 4：阅读统计
                    case 4 -> handleShowStatistics();
                    // 其余取值已被 readInt 的范围校验挡住，这里只是防御性分支
                    default -> io.printLine("暂不支持该操作。");
                }
            } catch (ValidationException | BookNotFoundException e) {
                // 业务校验失败属于用户可修正的问题，打印原因即可，不中断程序
                io.printLine("操作失败：" + e.getMessage());
            }
        }
    }

    /**
     * 打印主菜单。
     */
    private void printMenu() {
        // 空行用于把菜单与上一次的输出隔开，视觉上更清晰
        io.printLine("");
        io.printLine("1. 添加书籍");
        io.printLine("2. 记录书摘");
        io.printLine("3. 按书查询书摘");
        io.printLine("4. 阅读统计");
        io.printLine("0. 退出");
    }

    /**
     * 处理「添加书籍」：依次询问书名、作者、总页数，然后调用服务层保存。
     */
    private void handleAddBook() {
        // 询问书名
        String title = io.readLine("请输入书名：");
        // 输入流结束则直接返回主菜单
        if (title == null) {
            return;
        }
        // 询问作者，允许留空
        String author = io.readLine("请输入作者（可留空）：");
        // 输入流结束则返回
        if (author == null) {
            return;
        }
        // 询问总页数，0 表示未知，上限给一个宽松但安全的值
        Integer totalPages = io.readInt("请输入总页数（未知请填 0）：", 0, MAX_PAGE_INPUT);
        // 输入流结束则返回
        if (totalPages == null) {
            return;
        }
        // 交给服务层完成校验与落盘
        Book book = bookService.addBook(title, author, totalPages);
        // 提示成功并回显关键信息
        io.printLine("添加成功：《" + book.getTitle() + "》 编号 " + book.getId());
    }

    /**
     * 处理「记录书摘」：先让用户挑一本书，再录入正文、页码与标签。
     */
    private void handleAddExcerpt() {
        // 取出全部书籍供选择
        List<Book> books = bookService.listAll();
        // 书库为空时无法记录书摘，直接提示返回
        if (books.isEmpty()) {
            io.printLine("书库还是空的，请先添加书籍。");
            return;
        }
        // 打印书籍清单
        printBookList(books);
        // 让用户选择书籍，0 表示返回
        Integer index = io.readInt("请输入书籍序号（0 返回）：", 0, books.size());
        // 输入结束或用户选择返回
        if (index == null || index == 0) {
            return;
        }
        // 把从 1 开始的序号换算成列表下标
        Book book = books.get(index - 1);
        // 询问书摘正文
        String content = io.readLine("请输入书摘内容：");
        // 输入流结束则返回
        if (content == null) {
            return;
        }
        // 页码上限：总页数已知时以总页数为界，未知时用宽松上限
        int pageMax = book.isTotalPagesKnown() ? book.getTotalPages() : MAX_PAGE_INPUT;
        // 询问页码
        Integer page = io.readInt("请输入页码（未标注请填 0）：", 0, pageMax);
        // 输入流结束则返回
        if (page == null) {
            return;
        }
        // 询问标签
        String tagLine = io.readLine("请输入标签（多个用逗号分隔，可留空）：");
        // 输入流结束则返回
        if (tagLine == null) {
            return;
        }
        // 交给服务层完成校验、去重与落盘
        Excerpt excerpt = excerptService.addExcerpt(book.getId(), content, page, splitTags(tagLine));
        // 提示成功并回显编号
        io.printLine("记录成功：《" + book.getTitle() + "》 书摘编号 " + excerpt.getId());
    }

    /**
     * 处理「按书查询书摘」：先按关键字筛书，再展示选中书籍的全部书摘。
     */
    private void handleQueryExcerpts() {
        // 询问书名关键字，允许留空表示列出全部书籍
        String keyword = io.readLine("请输入书名关键字（直接回车列出全部）：");
        // 输入流结束则返回
        if (keyword == null) {
            return;
        }
        // 按关键字筛选书籍
        List<Book> matched = bookService.searchByKeyword(keyword);
        // 没有匹配的书就提示返回
        if (matched.isEmpty()) {
            io.printLine("没有匹配的书籍。");
            return;
        }
        // 打印匹配结果，并附上每本书的书摘条数
        for (int i = 0; i < matched.size(); i++) {
            Book book = matched.get(i);
            // 条数通过书摘服务查询，保证与后续展示的数据一致
            int count = excerptService.listByBook(book.getId()).size();
            io.printLine((i + 1) + ". 《" + book.getTitle() + "》 " + formatAuthor(book) + " 书摘 " + count + " 条");
        }
        // 让用户选择书籍
        Integer index = io.readInt("请输入书籍序号（0 返回）：", 0, matched.size());
        // 输入结束或用户选择返回
        if (index == null || index == 0) {
            return;
        }
        // 取出选中的书
        Book book = matched.get(index - 1);
        // 查询该书全部书摘
        List<Excerpt> excerpts = excerptService.listByBook(book.getId());
        // 没有书摘时给出明确提示，而不是什么都不显示
        if (excerpts.isEmpty()) {
            io.printLine("《" + book.getTitle() + "》还没有书摘。");
            return;
        }
        // 打印标题行
        io.printLine("《" + book.getTitle() + "》 共 " + excerpts.size() + " 条书摘：");
        // 逐条打印书摘明细
        for (Excerpt excerpt : excerpts) {
            io.printLine(formatExcerpt(excerpt));
        }
    }

    /**
     * 处理「阅读统计」：先打印全局汇总，再打印各书明细排行。
     */
    private void handleShowStatistics() {
        // 取全局汇总数据
        OverallStats overall = statisticsService.overall();
        // 打印汇总区块
        io.printLine("========== 阅读统计 ==========");
        io.printLine("书籍总数：" + overall.getTotalBooks() + " 本");
        io.printLine("书摘总数：" + overall.getTotalExcerpts() + " 条");
        io.printLine("活跃天数：" + overall.getTotalActiveDays() + " 天");
        io.printLine("最近摘录：" + formatTime(overall.getLastExcerptAt()));
        // 取各书明细排行
        List<ReadingStats> ranking = statisticsService.forAllBooks();
        // 没有任何书时到此为止
        if (ranking.isEmpty()) {
            io.printLine("书库还是空的，先去添加一本书吧。");
            return;
        }
        // 打印明细表头
        io.printLine("");
        io.printLine("各书明细（按书摘数从多到少）：");
        // 逐本打印统计行
        for (ReadingStats stats : ranking) {
            io.printLine(formatReadingStats(stats));
        }
    }

    /**
     * 打印书籍清单，序号从 1 开始。
     *
     * @param books 书籍列表
     */
    private void printBookList(List<Book> books) {
        // 先打印标题
        io.printLine("书库中共有 " + books.size() + " 本书：");
        // 逐本打印序号与书名
        for (int i = 0; i < books.size(); i++) {
            Book book = books.get(i);
            io.printLine((i + 1) + ". 《" + book.getTitle() + "》 " + formatAuthor(book));
        }
    }

    /**
     * 把一行输入拆成标签列表，支持中英文逗号分隔，并丢弃空白项。
     *
     * @param tagLine 用户输入的一行标签文本
     * @return 标签列表，输入为 null 或空白时返回空列表
     */
    private List<String> splitTags(String tagLine) {
        // 准备承载结果
        List<String> tags = new ArrayList<>();
        // 空输入表示没有标签
        if (tagLine == null || tagLine.isBlank()) {
            return tags;
        }
        // 中文逗号与英文逗号都当作分隔符，[,，] 是字符组写法
        String[] parts = tagLine.split("[,，]");
        // 逐个清洗
        for (String part : parts) {
            // 去掉首尾空白
            String trimmed = part.trim();
            // 丢弃空项，避免产生无意义标签
            if (!trimmed.isEmpty()) {
                tags.add(trimmed);
            }
        }
        // 返回结果
        return tags;
    }

    /**
     * 格式化作者信息，作者为空时返回占位文本。
     *
     * @param book 书籍
     * @return 形如「作者：余华」或「作者：未知」的文本
     */
    private String formatAuthor(Book book) {
        // 作者为 null 或空白时统一显示「未知」，避免出现空括号
        if (book.getAuthor() == null || book.getAuthor().isBlank()) {
            return "作者：未知";
        }
        // 正常情况直接回显作者
        return "作者：" + book.getAuthor();
    }

    /**
     * 把一条书摘格式化成多行文本，便于在终端里阅读。
     *
     * @param excerpt 书摘
     * @return 多行展示文本
     */
    private String formatExcerpt(Excerpt excerpt) {
        // 用 StringBuilder 逐段拼接，避免反复创建字符串
        StringBuilder sb = new StringBuilder();
        // 第一行：编号与页码
        sb.append("  #").append(excerpt.getId()).append("  ");
        if (excerpt.hasPage()) {
            sb.append("第 ").append(excerpt.getPage()).append(" 页");
        } else {
            sb.append("页码未标注");
        }
        // 有标签时追加标签信息
        if (!excerpt.getTags().isEmpty()) {
            sb.append("  标签：").append(String.join("、", excerpt.getTags()));
        }
        // 第二行：正文
        sb.append("\n      ").append(excerpt.getContent());
        // 第三行：摘录时间
        sb.append("\n      摘录于 ").append(formatTime(excerpt.getCreatedAt()));
        // 返回拼接结果
        return sb.toString();
    }

    /**
     * 把单本书的统计结果格式化成一行文本。
     *
     * @param stats 统计结果
     * @return 单行展示文本
     */
    private String formatReadingStats(ReadingStats stats) {
        // 逐段拼接：书名、条数、覆盖篇幅、活跃天数、最近摘录
        return "《" + stats.getBookTitle() + "》"
                + "  书摘 " + stats.getExcerptCount() + " 条"
                + "  覆盖 " + formatCoverage(stats)
                + "  活跃 " + stats.getActiveDays() + " 天"
                + "  最近 " + formatTime(stats.getLastExcerptAt());
    }

    /**
     * 格式化覆盖篇幅，区分「没有标注页码」「总页数未知」「可算覆盖率」三种情况。
     *
     * @param stats 统计结果
     * @return 覆盖篇幅描述文本
     */
    private String formatCoverage(ReadingStats stats) {
        // 一条页码都没标注时无法谈覆盖
        if (stats.getCoveredPages() == 0) {
            return "—";
        }
        // 总页数已知时给出覆盖率百分比，保留一位小数
        if (stats.isTotalPagesKnown()) {
            return stats.getCoveredPages() + "/" + stats.getTotalPages() + " 页（"
                    + String.format("%.1f", stats.coveragePercent()) + "%）";
        }
        // 总页数未知时只报绝对页数
        return stats.getCoveredPages() + " 页（总页数未知）";
    }

    /**
     * 格式化时间，null 统一显示为占位符。
     *
     * @param time 时间，可为 null
     * @return 形如 2026-09-20 15:31 的文本，null 时返回「—」
     */
    private String formatTime(LocalDateTime time) {
        // 没有时间就返回占位符，避免打印出 null
        if (time == null) {
            return "—";
        }
        // 按固定格式输出
        return time.format(TIME_FORMATTER);
    }
}
