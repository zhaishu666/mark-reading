package com.mark.reader.repository;

import com.mark.reader.exception.ValidationException;
import com.mark.reader.model.Book;
import com.mark.reader.repository.file.FileBookRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FileBookRepository} 的单元测试。
 * <p>
 * 所有用例都在 JUnit 提供的临时目录 {@link TempDir} 中运行，用完即删，
 * 保证测试之间互不干扰，也不会污染项目里真实的 data 目录。
 */
@DisplayName("FileBookRepository 文件仓储")
class FileBookRepositoryTest {

    /** 由 JUnit 注入的临时目录，每个测试方法各拿到一个全新目录 */
    @TempDir
    Path tempDir;

    /**
     * 构造一个待测仓储实例，避免每个用例重复写 new。
     *
     * @return 指向临时目录的书籍仓储
     */
    private FileBookRepository newRepository() {
        // 数据目录就是临时目录
        return new FileBookRepository(tempDir);
    }

    /**
     * 造一本带创建时间的测试书籍，省去每个用例重复设置时间。
     *
     * @param title 书名
     * @return 组装好的书籍对象
     */
    private Book newBook(String title) {
        // 使用便捷构造函数建对象
        Book book = new Book(title, "余华", 191);
        // 补上创建时间，验证日期字段的持久化
        book.setCreatedAt(LocalDateTime.of(2026, 9, 20, 15, 30, 0));
        // 返回对象
        return book;
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
        Path file = tempDir.resolve("books.tsv");
        assertTrue(Files.exists(file));
        // 首行应当是表头
        assertEquals("id\ttitle\tauthor\ttotalPages\tcurrentPage\tcreatedAt",
                Files.readAllLines(file, StandardCharsets.UTF_8).get(0));
    }

    /**
     * 校验新增书籍时主键从 1 开始并逐个递增。
     */
    @Test
    @DisplayName("新增书籍自动分配递增主键")
    void assignsIncrementingIds() {
        // 准备仓储
        FileBookRepository repository = newRepository();
        // 第一本应拿到主键 1
        assertEquals(1, repository.save(newBook("活着")).getId());
        // 第二本应拿到主键 2
        assertEquals(2, repository.save(newBook("许三观卖血记")).getId());
        // 下一可用主键应为 3
        assertEquals(3, repository.nextId());
    }

    /**
     * 校验按主键查询能命中，且查不存在的 id 返回空 Optional。
     */
    @Test
    @DisplayName("findById：命中与未命中")
    void findByIdHitsAndMisses() {
        // 准备仓储并存入一本书
        FileBookRepository repository = newRepository();
        Book saved = repository.save(newBook("活着"));
        // 命中时应当拿到书名一致的书籍
        Optional<Book> hit = repository.findById(saved.getId());
        assertTrue(hit.isPresent());
        assertEquals("活着", hit.get().getTitle());
        // 未命中时应当返回空 Optional
        assertTrue(repository.findById(999L).isEmpty());
    }

    /**
     * 校验按书名查询时忽略大小写与首尾空白。
     */
    @Test
    @DisplayName("findByTitle：忽略大小写与首尾空白")
    void findByTitleIgnoresCaseAndSpaces() {
        // 准备仓储并存入一本英文名书籍
        FileBookRepository repository = newRepository();
        repository.save(newBook("Effective Java"));
        // 带空格且大小写不同的查询仍应命中
        assertTrue(repository.findByTitle("  effective java  ").isPresent());
        // 完全无关的书名应当查不到
        assertTrue(repository.findByTitle("不存在的书").isEmpty());
    }

    /**
     * 校验 findAll 返回全部书籍且保持主键升序。
     */
    @Test
    @DisplayName("findAll：返回全部并保持主键升序")
    void findAllReturnsAllInIdOrder() {
        // 准备仓储并存入三本书
        FileBookRepository repository = newRepository();
        repository.save(newBook("甲"));
        repository.save(newBook("乙"));
        repository.save(newBook("丙"));
        // 取出全部
        List<Book> books = repository.findAll();
        // 条数应为 3
        assertEquals(3, books.size());
        // 主键应为 1、2、3
        assertEquals(1, books.get(0).getId());
        assertEquals(3, books.get(2).getId());
    }

    /**
     * 校验对已存在主键调用 save 属于更新，不会新增一条重复记录。
     */
    @Test
    @DisplayName("save：主键已存在时执行更新而非追加")
    void saveWithExistingIdUpdatesInsteadOfAppending() {
        // 准备仓储并存入一本书
        FileBookRepository repository = newRepository();
        Book saved = repository.save(newBook("活着"));
        // 修改书名后再存一次
        saved.setTitle("活着（修订版）");
        repository.save(saved);
        // 总数仍应为 1
        assertEquals(1, repository.findAll().size());
        // 书名应当是修改后的值
        assertEquals("活着（修订版）", repository.findById(saved.getId()).orElseThrow().getTitle());
    }

    /**
     * 校验删除行为：存在则删除成功，不存在则返回 false。
     */
    @Test
    @DisplayName("deleteById：删除成功与目标不存在的分支")
    void deleteByIdBranches() {
        // 准备仓储并存入一本书
        FileBookRepository repository = newRepository();
        Book saved = repository.save(newBook("活着"));
        // 删除存在的书应返回 true
        assertTrue(repository.deleteById(saved.getId()));
        // 删除后书库应为空
        assertTrue(repository.findAll().isEmpty());
        // 再次删除同一主键应返回 false
        assertFalse(repository.deleteById(saved.getId()));
    }

    /**
     * 校验数据真正落盘：新建另一个仓储实例（模拟程序重启）后仍能读到原有数据。
     */
    @Test
    @DisplayName("持久化：重建仓储实例后数据仍在")
    void dataSurvivesRepositoryRecreation() {
        // 第一个实例写入一本书
        newRepository().save(newBook("活着"));
        // 第二个实例指向同一目录，相当于重启程序
        FileBookRepository reopened = newRepository();
        // 应当能读到那本书
        List<Book> books = reopened.findAll();
        assertEquals(1, books.size());
        // 书名与创建时间都应完整还原
        assertEquals("活着", books.get(0).getTitle());
        assertEquals(LocalDateTime.of(2026, 9, 20, 15, 30, 0), books.get(0).getCreatedAt());
        // 主键分配应当接续已有记录，而不是从 1 重来
        assertEquals(2, reopened.nextId());
    }

    /**
     * 校验中文以及含制表符、换行的字段经过转义后能正确往返。
     */
    @Test
    @DisplayName("转义：中文与含制表符换行的字段往返正确")
    void specialCharactersSurviveRoundTrip() {
        // 准备仓储
        FileBookRepository repository = newRepository();
        // 作者名故意包含制表符与换行，这是最容易撑破 TSV 的情况
        Book book = new Book("活着", "余华\t（\n中国作家）", 191);
        book.setCreatedAt(LocalDateTime.of(2026, 9, 20, 8, 0, 0));
        // 保存后用全新实例读回
        repository.save(book);
        Book restored = newRepository().findAll().get(0);
        // 书名、作者、页数都应完全一致
        assertEquals("活着", restored.getTitle());
        assertEquals("余华\t（\n中国作家）", restored.getAuthor());
        assertEquals(191, restored.getTotalPages());
    }

    /**
     * 校验数据文件字段个数异常时抛出校验异常，并且错误信息里带有行号。
     */
    @Test
    @DisplayName("数据损坏：字段个数异常时抛出 ValidationException")
    void throwsWhenFieldCountIsWrong() throws IOException {
        // 手工写入一个只有三个字段的坏行
        Path file = tempDir.resolve("books.tsv");
        Files.write(file, List.of(
                "id\ttitle\tauthor\ttotalPages\tcurrentPage\tcreatedAt",
                "1\t活着\t余华"), StandardCharsets.UTF_8);
        // 构造仓储本身不会失败
        FileBookRepository repository = newRepository();
        // 但读取时应当报错，且提示里包含行号 2
        ValidationException exception = assertThrows(ValidationException.class, repository::findAll);
        assertTrue(exception.getMessage().contains("第 2 行"));
    }
}
