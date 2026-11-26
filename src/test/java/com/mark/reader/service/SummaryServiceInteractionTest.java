package com.mark.reader.service;

import com.mark.reader.ai.SummaryGenerator;
import com.mark.reader.exception.BookNotFoundException;
import com.mark.reader.exception.ValidationException;
import com.mark.reader.model.Book;
import com.mark.reader.model.Excerpt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SummaryServiceInteractionTest {

    //@Mock注解的作用是创建一个模拟对象,默认返回null/0/false等初始化值
    @Mock private BookService bookService;
    @Mock private ExcerptService excerptService;
    @Mock private SummaryGenerator generator;

    private SummaryService summaryService;

    @BeforeEach
    void setup(){
        summaryService = new SummaryService(bookService, excerptService, generator);
    }

    @Test
    @DisplayName("拼装后的文本交给生成器，生成器的返回值原样返回")
    void summarize_book_sends_built_text_to_generator(){
        Book book = new Book("三体", "刘慈欣", 0);
        Excerpt excerpt = new Excerpt(1L, "给文明以岁月,而不是给岁月以文明", 0, List.of());

        //一个注意的点:@ExtendWith(MockitoExtension.class) 默认开启 STRICT_STUBS：when(...) 安排了但没被实际调用的桩,会让测试失败
        when(bookService.getById(1L)).thenReturn(book);  //定义 Mock 模拟对象在特定方法调用时的返回值
        when(excerptService.listByBook(1L)).thenReturn(List.of(excerpt));
        when(generator.summarize(anyString())).thenReturn("摘要结果");

        String result = summaryService.summarizeBook(1L);

        // 捕捉器要按"实际传进去的参数类型"声明：summarize(String) 收的是 String
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(generator).summarize(captor.capture());

        String prompt = captor.getValue();
        assertEquals("摘要结果", result);
        assertTrue(prompt.contains("三体"), "应包含书名");
        assertTrue(prompt.contains("给文明以岁月,而不是给岁月以文明"), "应包含书摘内容");
        assertTrue(prompt.contains("共1条书摘"), "应包含条数");
    }

    @Test
    @DisplayName("书没有书摘时抛异常,并且不调用generator")
    void no_excerpts_never_calls_generator(){
        when(bookService.getById(1L)).thenReturn(new Book("某书","某人",100));
        when(excerptService.listByBook(1L)).thenReturn(List.of());

        assertThrows(ValidationException.class, () -> summaryService.summarizeBook(1L));
        verifyNoInteractions(generator);
    }

    @Test
    @DisplayName("书不存在时抛异常,并且不调用generator")
    void unknown_book_never_calls_generator(){
        when(bookService.getById(9999L)).thenThrow(BookNotFoundException.class);

        assertThrows(BookNotFoundException.class, () -> summaryService.summarizeBook(9999L));
        verifyNoInteractions(generator);
    }
}
