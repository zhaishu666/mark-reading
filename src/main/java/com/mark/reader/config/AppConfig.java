package com.mark.reader.config;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Clock;

/**
 * Spring 装配配置：全项目唯一与框架耦合的地方。
 * <p>
 * 本类只负责「造对象、连依赖」，把仓储实现、时钟、业务服务与控制台界面拼装成 Bean。
 * 业务类因此不必出现任何 Spring 类型，将来把文件存储换成数据库，
 * 只需改动这里的某个 @Bean 方法，其余代码无需感知。
 * <p>
 * {@code proxyBeanMethods = false} 表示本类的 @Bean 方法之间不会互相调用，
 * 关闭 CGLIB 代理可以省掉一层子类化，让容器启动更快。
 */
@Configuration(proxyBeanMethods = false)
public class AppConfig {

    /**
     * 书籍仓储 Bean，数据落在 {@code <数据目录>/books.tsv}。
     * <p>
     * 数据目录取配置项 {@code mark.reader.data-dir}，未配置时回退到相对启动目录的 {@code data}。
     * 冒号后的 {@code data} 是 {@code @Value} 的默认值语法，不是硬编码路径。
     *
     * @param dataDir 数据目录，由 Spring 从配置注入
     * @return 基于文件的书籍仓储
     */
    @Bean
    public BookRepository bookRepository(@Value("${mark.reader.data-dir:data}") String dataDir){
        return new FileBookRepository(Path.of(dataDir));
    }

    /**
     * 书摘仓储 Bean，数据落在 {@code <数据目录>/excerpts.tsv}，与书籍文件同目录。
     *
     * @param dataDir 数据目录，由 Spring 从配置注入
     * @return 基于文件的书摘仓储
     */

    //@Value注解写了两次完全相同的,接入Spring AI时优化
    @Bean
    public ExcerptRepository excerptRepository(@Value("${mark.reader.data-dir:data}") String dataDir){
        return new FileExcerptRepository(Path.of(dataDir));
    }

    /**
     * 系统时钟 Bean。
     * <p>
     * 服务层通过构造参数拿到 {@link Clock}，而不是直接调用 {@code LocalDateTime.now()}：
     * 生产环境用系统默认时区，测试环境可换成固定时钟，让与时间相关的断言稳定可重复。
     *
     * @return 使用系统默认时区的时钟
     */
    @Bean
    public Clock clock(){
        return Clock.systemDefaultZone();
    }

    /**
     * 书籍业务服务 Bean：负责校验、去重与落盘。
     *
     * @param bookRepository 书籍仓储
     * @param clock          时间来源，用于记录创建时间
     * @return 书籍服务实例
     */
    @Bean
    public BookService bookService(BookRepository bookRepository, Clock clock){
        return new BookService(bookRepository, clock);
    }

    /**
     * 书摘业务服务 Bean。
     * <p>
     * 除了书摘仓储，还需要书籍仓储：记录书摘前要确认所属书籍真实存在，并据此校验页码是否越界。
     *
     * @param excerptRepository 书摘仓储
     * @param bookRepository    书籍仓储，用于存在性与总页数校验
     * @param clock             时间来源，用于记录创建时间
     * @return 书摘服务实例
     */
    @Bean
    public ExcerptService excerptService(ExcerptRepository excerptRepository, BookRepository bookRepository, Clock clock){
        return new ExcerptService(excerptRepository, bookRepository, clock);
    }

    /**
     * 阅读统计服务 Bean。
     * <p>
     * 只读取两个仓储做汇总计算，不产生时间戳，因此不需要注入时钟。
     *
     * @param bookRepository    书籍仓储
     * @param excerptRepository 书摘仓储
     * @return 统计服务实例
     */
    @Bean
    public StatisticsService statisticsService(BookRepository bookRepository, ExcerptRepository excerptRepository){
        return new StatisticsService(bookRepository, excerptRepository);
    }

    /**
     * 控制台输入输出门面 Bean。
     * <p>
     * 以接口形式注册而非直接 new 具体实现，是为了让测试可以注入假的 ConsoleIO 驱动命令行流程，
     * 不必真的去读键盘和打印屏幕。
     *
     * @return 基于标准输入输出的实现
     */
    @Bean
    public ConsoleIO consoleIO(){
        return new ConsoleIOImpl();
    }

    /**
     * 命令行应用 Bean：由容器把界面与三个服务注入后，在 {@code Main} 中调用 run() 启动交互循环。
     *
     * @param consoleIO         控制台输入输出
     * @param bookService       书籍服务
     * @param excerptService    书摘服务
     * @param statisticsService 统计服务
     * @return 命令行应用实例
     */
    @Bean
    public CommandLineApp commandLineApp(ConsoleIO consoleIO, BookService bookService, ExcerptService excerptService, StatisticsService statisticsService){
        return new CommandLineApp(consoleIO, bookService, excerptService, statisticsService);
    }
}
