package com.mark.reader;

import com.mark.reader.cli.CommandLineApp;
import com.mark.reader.cli.ConsoleIO;
import com.mark.reader.cli.ConsoleIOImpl;
import com.mark.reader.repository.BookRepository;
import com.mark.reader.repository.ExcerptRepository;
import com.mark.reader.repository.file.FileBookRepository;
import com.mark.reader.repository.file.FileExcerptRepository;
import com.mark.reader.service.BookService;
import com.mark.reader.service.ExcerptService;
import com.mark.reader.service.StatisticsService;

import java.nio.file.Path;

/**
 * 程序入口。
 * <p>
 * 这是整个项目里唯一负责「装配」的地方：决定用哪套仓储实现、把依赖一层层传下去。
 * 之所以把装配集中在这里而不是散落在各个类中，是因为将来把文件存储换成 MySQL 时，
 * 只需要改动本文件里创建仓储的那两行，其余代码一行都不用碰。
 * 这种「在入口处手工完成依赖注入」的做法，正是后面学习 Spring 容器所要做的事情的手工版本。
 */
public class Main {

    /** 默认数据目录名，相对于程序启动时的工作目录 */
    private static final String DATA_DIR = "data";

    /**
     * 私有构造函数，防止入口类被实例化。
     */
    private Main() {
        // 入口类只提供静态 main 方法
    }

    /**
     * 程序主方法。
     *
     * @param args 命令行参数，当前版本未使用
     */
    public static void main(String[] args) {
        // 确定数据目录，文件仓储会在目录不存在时自动创建它
        Path dataDir = Path.of(DATA_DIR);
        // 创建书籍仓储（面向接口编程，右侧是具体实现）
        BookRepository bookRepository = new FileBookRepository(dataDir);
        // 创建书摘仓储
        ExcerptRepository excerptRepository = new FileExcerptRepository(dataDir);
        // 组装书籍业务服务
        BookService bookService = new BookService(bookRepository);
        // 组装书摘业务服务，它同时需要书籍仓储来校验书是否存在
        ExcerptService excerptService = new ExcerptService(excerptRepository, bookRepository);
        // 组装统计业务服务
        StatisticsService statisticsService = new StatisticsService(bookRepository, excerptRepository);
        // 创建控制台输入输出通道
        ConsoleIO io = new ConsoleIOImpl();
        // 组装命令行界面并启动主循环
        new CommandLineApp(io, bookService, excerptService, statisticsService).run();
    }
}
