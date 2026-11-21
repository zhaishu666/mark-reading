package com.mark.reader;

import com.mark.reader.cli.CommandLineApp;
import com.mark.reader.config.AppConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 程序入口。
 * <p>
 * 装配由 {@link AppConfig} 负责，本类只做两件事：启动容器、取出
 * {@link CommandLineApp} 并运行。把装配集中在配置类而不是散落在各个类里，
 * 是为了让业务类不依赖任何框架类型 —— 将来把文件存储换成 MySQL，
 * 只需在 AppConfig 里改一个 @Bean，业务代码一行都不用碰。
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
       try(ConfigurableApplicationContext ctx = SpringApplication.run(AppConfig.class, args)){
           ctx.getBean(CommandLineApp.class).run();
       }
    }
}
