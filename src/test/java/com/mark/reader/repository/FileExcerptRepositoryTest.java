package com.mark.reader.repository;

import com.mark.reader.exception.ValidationException;
import com.mark.reader.model.Excerpt;
import com.mark.reader.repository.file.FileExcerptRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FileExcerptRepository} 的单元测试。
 * <p>
 * 同样运行在临时目录中。除了常规增查删之外，重点验证两件事：
 * 标签列表（含标签内的竖线）能否无损往返，以及按书籍主键批量删除是否准确。
 */
@DisplayName("FileExcerptRepository 文件仓储")
class FileExcerptRepositoryTest {

    /** 由 JUnit 注入的临时目录 */
    @TempDir
    Path tempDir;

    /**
     * 构造一个待测仓储实例。
     *
     * @return 指向临时目录的书摘仓储
     */
    private FileExcerptRepository newRepository() {
        // 数据目录就是临时目录
        return new FileExcerptRepository(tempDir);
    }

    /**
     * 造一条带创建时间的测试书摘。
     *
     * @param bookId  所属书籍主键
     * @param content 正文
     * @return 组装好的书摘对象
     */
    private Excerpt newExcerpt(long bookId, String content) {
        // 使用便捷构造函数建对象，标签暂时为空
        Excerpt excerpt = new Excerpt(bookId, content, 12, List.of("文学"));
        // 补上创建时间
        excerpt.setCreatedAt(LocalDateTime.of(2026, 9, 20, 15, 31, 0));
        // 返回对象
        return excerpt;
    }

    /**
     * 校验构造仓储时会自动创建数据文件并写入表头。
     */
    @Test
    @DisplayName("构造时自动创建数据文件并写入表头")
    void createsDataFileOnConstruction() throws IOException {
        // 构造仓储
        newRepository();
        // 数据文件应当已存在
        Path file = tempDir.resolve("excerpts.tsv");
        assertTrue(Files.exists(file));
        // 首行应当是表头
        assertEquals("id\tbookId\tcontent\tpage\ttags\tcreatedAt",
                Files.readAllLines(file, StandardCharsets.UTF_8).get(0));
    }

    /**
     * 校验新增书摘时主键从 1 开始递增，且书摘主键与书籍主键彼此独立。
     */
    @Test
    @DisplayName("新增书摘自动分配递增主键")
    void assignsIncrementingIds() {
        // 准备仓储
        FileExcerptRepository repository = newRepository();
        // 第一条应拿到主键 1
        assertEquals(1, repository.save(newExcerpt(1L, "第一段")).getId());
        // 第二条应拿到主键 2
        assertEquals(2, repository.save(newExcerpt(1L, "第二段")).getId());
        // 下一可用主键应为 3
        assertEquals(3, repository.nextId());
    }

    /**
     * 校验按主键查询的命中与未命中分支。
     */
    @Test
    @DisplayName("findById：命中与未命中")
    void findByIdHitsAndMisses() {
        // 准备仓储并存入一条书摘
        FileExcerptRepository repository = newRepository();
        Excerpt saved = repository.save(newExcerpt(1L, "人是为活着本身而活着"));
        // 命中
        Optional<Excerpt> hit = repository.findById(saved.getId());
        assertTrue(hit.isPresent());
        assertEquals("人是为活着本身而活着", hit.get().getContent());
        // 未命中
        assertTrue(repository.findById(999L).isEmpty());
    }

    /**
     * 校验 findByBookId 只返回指定书籍的书摘，不会把其他书的记录混进来。
     */
    @Test
    @DisplayName("findByBookId：只返回指定书籍的书摘")
    void findByBookIdFiltersOtherBooks() {
        // 准备仓储，给两本书各存两条
        FileExcerptRepository repository = newRepository();
        repository.save(newExcerpt(1L, "甲书第一段"));
        repository.save(newExcerpt(1L, "甲书第二段"));
        repository.save(newExcerpt(2L, "乙书第一段"));
        // 查询 1 号书的书摘
        List<Excerpt> ofBookOne = repository.findByBookId(1L);
        // 应当只有两条
        assertEquals(2, ofBookOne.size());
        // 且都属于 1 号书
        assertTrue(ofBookOne.stream().allMatch(e -> e.getBookId() == 1L));
        // 查询不存在的书籍应返回空列表
        assertTrue(repository.findByBookId(999L).isEmpty());
    }

    /**
     * 校验 findAll 返回全部书摘。
     */
    @Test
    @DisplayName("findAll：返回全部书摘")
    void findAllReturnsEverything() {
        // 准备仓储并跨两本书存三条
        FileExcerptRepository repository = newRepository();
        repository.save(newExcerpt(1L, "甲"));
        repository.save(newExcerpt(2L, "乙"));
        repository.save(newExcerpt(2L, "丙"));
        // 总数应为 3
        assertEquals(3, repository.findAll().size());
    }

    /**
     * 校验按书籍主键批量删除的计数与边界情况。
     */
    @Test
    @DisplayName("deleteByBookId：返回删除条数，无匹配时返回 0")
    void deleteByBookIdCountsRemovedRows() {
        // 准备仓储，给两本书各存两条
        FileExcerptRepository repository = newRepository();
        repository.save(newExcerpt(1L, "甲书第一段"));
        repository.save(newExcerpt(1L, "甲书第二段"));
        repository.save(newExcerpt(2L, "乙书第一段"));
        // 删除 1 号书的书摘应返回 2
        assertEquals(2, repository.deleteByBookId(1L));
        // 剩下的应当只有 2 号书那一条
        List<Excerpt> remaining = repository.findAll();
        assertEquals(1, remaining.size());
        assertEquals(2L, remaining.get(0).getBookId());
        // 再次删除 1 号书应返回 0
        assertEquals(0, repository.deleteByBookId(1L));
    }

    /**
     * 校验数据真正落盘：重建仓储实例后仍能读到原有数据。
     */
    @Test
    @DisplayName("持久化：重建仓储实例后数据仍在")
    void dataSurvivesRepositoryRecreation() {
        // 第一个实例写入一条书摘
        newRepository().save(newExcerpt(1L, "人是为活着本身而活着"));
        // 第二个实例指向同一目录
        FileExcerptRepository reopened = newRepository();
        // 应当能读到那条书摘，且字段完整
        List<Excerpt> excerpts = reopened.findAll();
        assertEquals(1, excerpts.size());
        assertEquals("人是为活着本身而活着", excerpts.get(0).getContent());
        assertEquals(12, excerpts.get(0).getPage());
        assertEquals(List.of("文学"), excerpts.get(0).getTags());
        // 主键分配应当接续，而不是从 1 重来
        assertEquals(2, reopened.nextId());
    }

    /**
     * 校验正文含换行、制表符，标签含竖线时都能无损往返。
     */
    @Test
    @DisplayName("转义：正文含换行制表符、标签含竖线时往返正确")
    void specialCharactersSurviveRoundTrip() {
        // 准备仓储
        FileExcerptRepository repository = newRepository();
        // 正文里塞进换行与制表符，标签里塞进竖线与空格
        String content = "第一行\n第二行\t缩进";
        List<String> tags = Arrays.asList("文学", "人生|感悟", " 带空格 ");
        Excerpt excerpt = new Excerpt(7L, content, 88, tags);
        excerpt.setCreatedAt(LocalDateTime.of(2026, 9, 20, 9, 0, 0));
        // 保存后换新实例读回
        repository.save(excerpt);
        Excerpt restored = newRepository().findAll().get(0);
        // 正文必须一字不差
        assertEquals(content, restored.getContent());
        // 标签列表必须与原始列表完全一致
        assertEquals(tags, restored.getTags());
        // 页码与所属书籍也要正确
        assertEquals(88, restored.getPage());
        assertEquals(7L, restored.getBookId());
    }

    /**
     * 校验对已存在主键调用 save 属于更新，不会新增重复记录。
     */
    @Test
    @DisplayName("save：主键已存在时执行更新而非追加")
    void saveWithExistingIdUpdatesInsteadOfAppending() {
        // 准备仓储并存入一条书摘
        FileExcerptRepository repository = newRepository();
        Excerpt saved = repository.save(newExcerpt(1L, "原始内容"));
        // 修改正文后再存一次
        saved.setContent("修改后的内容");
        repository.save(saved);
        // 总数仍应为 1
        assertEquals(1, repository.findAll().size());
        // 正文应当是修改后的值
        assertEquals("修改后的内容", repository.findById(saved.getId()).orElseThrow().getContent());
    }

    /**
     * 校验数据文件字段个数异常时抛出带行号的校验异常。
     */
    @Test
    @DisplayName("数据损坏：字段个数异常时抛出 ValidationException")
    void throwsWhenFieldCountIsWrong() throws IOException {
        // 手工写入一个字段数不足的坏行
        Path file = tempDir.resolve("excerpts.tsv");
        Files.write(file, List.of(
                "id\tbookId\tcontent\tpage\ttags\tcreatedAt",
                "1\t1\t正文"), StandardCharsets.UTF_8);
        // 构造仓储本身不会失败
        FileExcerptRepository repository = newRepository();
        // 读取时应报错，且提示里包含行号 2
        ValidationException exception = assertThrows(ValidationException.class, repository::findAll);
        assertTrue(exception.getMessage().contains("第 2 行"));
    }
}
