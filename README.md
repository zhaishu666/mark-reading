# 马克阅读（Mark Reading）

个人阅读与书摘管理应用——记录书籍、摘抄书摘、查询与统计。

当前为 **v0.1.0 命令行版**：纯 Java 实现，不依赖任何第三方运行时库，用文本文件保存数据。

## 技术栈

- 当前：Java 17 + Maven + JUnit 5 + TSV 文件持久化（运行时零第三方依赖）
- 规划：Spring Boot 3.x + MySQL + Redis

## 功能

已实现（v0.1.0）：

- [x] 添加书籍（书名 / 作者 / 总页数，带重复与合法性校验）
- [x] 记录书摘（正文 / 页码 / 标签，同书内容自动去重）
- [x] 按书查询书摘（支持书名关键字筛选，展示页码、标签、摘录时间）
- [x] 阅读统计（全局汇总 + 各书明细排行）

规划中：

- [ ] 删除与编辑书籍、删除书摘
- [ ] 书摘导出（Markdown / CSV）
- [ ] 全文检索与标签筛选
- [ ] 迁移到 Spring Boot + MySQL

## 环境要求

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17 及以上 | 本机已验证 JDK 25；源码按 Java 17 语法编写 |
| Maven | 3.9 及以上 | 本机安装于 `C:\Users\翟曙\tools\apache-maven-3.9.16`，已加入用户 PATH |
| JAVA_HOME | 指向 JDK 根目录 | 本机为 `E:\JDK26`，`mvn` 依赖它定位 JDK |

## 构建与运行

```bash
# 编译
mvn -B compile

# 运行测试（交付前必须全绿）
mvn -B test

# 直接运行（开发期）
mvn -B exec:java

# 打包后运行
mvn -B package
java -jar target/mark-reader-0.1.0.jar
```

程序启动后进入交互式菜单：

```
1. 添加书籍
2. 记录书摘
3. 按书查询书摘
4. 阅读统计
0. 退出
```

## 中文编码处理

程序**不靠猜环境来决定编码**。Windows 中文环境下 JDK 会把 `stdin.encoding` 报成 GBK，
但 IDE 运行窗口、Git Bash、管道重定向送来的往往是 UTF-8 字节，
一旦按 GBK 去解，中文就会变成「涓変綋」这种东西并被写进数据文件。

因此输入侧改为**看字节判定**，优先级如下：

| 顺序 | 条件 | 采用编码 |
| --- | --- | --- |
| 1 | 启动参数指定了 `-Dmark.reader.encoding=xxx` | 用户指定的编码，不做任何猜测 |
| 2 | 整行字节是合法 UTF-8 | UTF-8（IDE 运行窗口、Git Bash、管道重定向都属于这种情况） |
| 3 | 以上都不满足 | 控制台本地编码 `stdin.encoding`，取不到则用 JVM 默认 |

输出侧不做自动判定——自己的输出无法自我校验——优先用显式覆盖，否则跟随 `stdout.encoding`。

**结论：正常情况下不需要任何额外配置。** 只有环境确实特殊时才需要显式指定：

```bash
# 例如在 GBK 控制台里重定向 GBK 文本
java -Dmark.reader.encoding=GBK -jar target/mark-reader-0.1.0.jar

# 或者先切换代码页为 UTF-8 再启动
chcp 65001
```

> 数据文件本身始终以 UTF-8 写入，与终端编码无关。用编辑器打开 `data/*.tsv` 时请选择 UTF-8。

### 看到乱码时怎么判断

「涓変綋」是「三体」的 UTF-8 字节被当作 GBK 解读的结果，这类形态可以直接认出问题所在：

| 现象 | 含义 | 处理 |
| --- | --- | --- |
| 存进去的中文变成「涓変綋」这类怪字，但字形本身正常 | 输入解码用错了编码 | 升级到已修复版本；旧数据需要手工订正 |
| 中文中间夹着 `�`（Unicode 替换字符） | 该行字节被错误编码截断，通常伴随上一种情况 | 同上 |
| 打开 `data/*.tsv` 看到乱码，但程序里显示正常 | 编辑器没用 UTF-8 打开 | 把编辑器编码切成 UTF-8 |


## 目录结构

```
src/main/java/com/mark/reader/
├─ Main.java                    程序入口，唯一的依赖装配点
├─ cli/                         交互层：菜单、输入输出
│  ├─ ConsoleIO.java            输入输出接口（便于测试替换）
│  ├─ ConsoleIOImpl.java        控制台实现，含输入编码自动判定
│  ├─ Utf8.java                 UTF-8 字节序列校验（编码判定的依据）
│  └─ CommandLineApp.java       菜单主循环与功能分发
├─ service/                     业务层：校验与业务规则
│  ├─ BookService.java
│  ├─ ExcerptService.java
│  └─ StatisticsService.java
├─ repository/                  存储层
│  ├─ BookRepository.java       接口
│  ├─ ExcerptRepository.java    接口
│  └─ file/                     文本文件实现
│     ├─ FileBookRepository.java
│     ├─ FileExcerptRepository.java
│     └─ TextCodec.java         TSV 转义编解码
├─ model/                       实体与值对象
│  ├─ Book.java  Excerpt.java
│  └─ ReadingStats.java  OverallStats.java
└─ exception/                   业务异常
   ├─ ValidationException.java
   └─ BookNotFoundException.java
```

## 架构约定

- **依赖方向单向**：`cli` → `service` → `repository` → 文件。上层只依赖下层的接口，不反向依赖。
- **存储可替换**：`repository` 先定义接口，`file` 包提供文件实现。将来迁移 MySQL 时新增一个实现类，只需修改 `Main` 里的两行装配代码，业务代码零改动。
- **业务逻辑与界面解耦**：菜单类只做「画界面、读输入、调服务、打结果」，任何校验规则都写在服务层，因此图形界面可以随时替换。
- **时间可注入**：服务层通过构造参数接收 `java.time.Clock`，测试中使用固定时钟，断言不依赖真实系统时间。

## 数据文件

数据保存在工作目录下的 `data/` 中，**不纳入版本控制**（已在 `.gitignore` 中排除）。

`data/books.tsv`：

```
id	title	author	totalPages	currentPage	createdAt
1	活着	余华	191	0	2026-09-20T15:36:01.488678100
```

`data/excerpts.tsv`：

```
id	bookId	content	page	tags	createdAt
1	1	人是为活着本身而活着	12	文学\p人生	2026-09-20T15:36:01.503793700
```

字段以制表符分隔。字段内部的制表符、换行、回车、反斜杠、竖线由 `TextCodec` 转义：

| 原始字符 | 转义后 |
| --- | --- |
| `\` | `\\` |
| 制表符 | `\t` |
| 换行 | `\n` |
| 回车 | `\r` |
| `\|`（竖线） | `\p` |

竖线需要转义，是因为标签字段内部用竖线分隔多个标签。

## 统计口径

| 指标 | 口径 |
| --- | --- |
| 书摘数 | 该书摘录条数 |
| 覆盖篇幅 | 出现过书摘的**不同页码**数量；未标注页码的书摘不计入 |
| 覆盖率 | 覆盖页码数 ÷ 总页数；总页数未知（填 0）时不显示百分比 |
| 活跃天数 | 摘录创建日期去重后的天数 |
| 最近摘录 | 该书最新一条摘录的创建时间 |
| 全局汇总 | 书籍总数、书摘总数、全局活跃天数（跨书同日合并计一天） |

## 迭代记录

见 commit 历史。每个提交对应一次可独立回滚的改动，且提交时测试全部通过。
