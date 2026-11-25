package com.mark.reader.service;

import com.mark.reader.ai.SummaryGenerator;
import com.mark.reader.exception.BookNotFoundException;
import com.mark.reader.exception.ValidationException;
import com.mark.reader.repository.BookRepository;
import com.mark.reader.repository.ExcerptRepository;
import com.mark.reader.repository.file.FileBookRepository;
import com.mark.reader.repository.file.FileExcerptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SummaryService 书摘服务")
public class SummaryServiceTest {

    /** 记录收到的 prompt，并返回固定摘要，用于断言"传进去的是什么" */
    static class CapturingGenerator implements SummaryGenerator {

        /** 每次调用收到的文本，按调用顺序记录 */
        private final List<String> inputs = new ArrayList<>();

        @Override
        public String summarize(String text) {
            inputs.add(text);
            return "假摘要";
        }
    }

    @TempDir
    Path tempDir;

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-20T15:31:00Z"), ZoneOffset.UTC);

    private BookService bookService;
    private ExcerptService excerptService;

    @BeforeEach
    void setUp() {
        BookRepository bookRepository = new FileBookRepository(tempDir);
        ExcerptRepository excerptRepository = new FileExcerptRepository(tempDir);
        bookService = new BookService(bookRepository, FIXED_CLOCK);
        excerptService = new ExcerptService(excerptRepository, bookRepository, FIXED_CLOCK);
    }

    @Test
    @DisplayName("拼装结果包含书名、条数、每条书摘与指令")
    void summarize_book_passes_all_excerpts_as_text() {
        // Arrange：造一本书、两条书摘（一条有页码、一条没有）
        long bookId = bookService.addBook("活着", "余华", 200).getId();
        excerptService.addExcerpt(bookId, "人是为了活着本身而活着", 12, List.of());
        excerptService.addExcerpt(bookId, "第二条摘录", 0, List.of());

        CapturingGenerator generator = new CapturingGenerator();
        SummaryService service = new SummaryService(bookService, excerptService, generator);

        // Act：只调一次，只断言一次行为
        String result = service.summarizeBook(bookId);

        // Assert：返回值 + 只调一次 + prompt 里该有的东西
        assertEquals("假摘要", result);
        assertEquals(1, generator.inputs.size());

        String prompt = generator.inputs.get(0);
        assertTrue(prompt.contains("活着"), "prompt 应包含书名");
        assertTrue(prompt.contains("共2条书摘"), "prompt 应包含书摘条数");
        assertTrue(prompt.contains("人是为了活着本身而活着"), "第一条书摘内容缺失");
        assertTrue(prompt.contains("第二条摘录"), "第二条书摘内容缺失");
        assertTrue(prompt.contains("页码未知"), "未标注页码的书摘应显示占位文本");
    }

    @Test
    @DisplayName("书没有书摘时抛 ValidationException，且一次都不调用生成器")
    void summarize_book_with_no_excerpts_throws() {
        long bookId = bookService.addBook("空书", "某人", 100).getId();

        CapturingGenerator generator = new CapturingGenerator();
        SummaryService service = new SummaryService(bookService, excerptService, generator);

        assertThrows(ValidationException.class, () -> service.summarizeBook(bookId));

        // 关键断言：没书摘就不该花钱调模型
        assertEquals(0, generator.inputs.size());
    }

    @Test
    @DisplayName("书不存在时抛 BookNotFoundException，且不调用生成器")
    void summarize_unknown_book_throws() {
        CapturingGenerator generator = new CapturingGenerator();
        SummaryService service = new SummaryService(bookService, excerptService, generator);

        assertThrows(BookNotFoundException.class, () -> service.summarizeBook(999L));
        assertEquals(0, generator.inputs.size());
    }

}

