package com.mark.reader.service;

import com.mark.reader.ai.SummaryGenerator;
import com.mark.reader.exception.ValidationException;
import com.mark.reader.model.Book;
import com.mark.reader.model.Excerpt;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SummaryService {

    private final BookService bookService;

    private final ExcerptService excerptService;

    private final SummaryGenerator summaryGenerator;


    public SummaryService(BookService bookService, ExcerptService excerptService, SummaryGenerator summaryGenerator){
        this.bookService = Objects.requireNonNull(bookService, "bookService 不能为null ");
        this.excerptService = Objects.requireNonNull(excerptService, "excerptService 不能为null ");
        this.summaryGenerator = Objects.requireNonNull(summaryGenerator, "summaryGenerator 不能为null ");
    }

    public String summarizeBook(long bookId){
        Book book = bookService.getById(bookId);
        List<Excerpt> excerpts = excerptService.listByBook(bookId);
        if(excerpts.isEmpty()){
            throw new ValidationException("《" + book.getTitle() + "》: 当前书本无书摘");
        }
        return summaryGenerator.summarize(buildExcerptText(book, excerpts));
    }

    private String buildExcerptText(Book book, List<Excerpt> excerpts){
        List<String> lines = new ArrayList<>();

        lines.add("《" + book.getTitle() + "》共" + excerpts.size() + "条书摘: ");

        for (int i = 0; i < excerpts.size(); i++){
            Excerpt excerpt = excerpts.get(i);
            //页码可能没标注,hasPage()判断
            String page = excerpt.hasPage() ? "第 " + excerpt.getPage() + " 页" : "页码未知";
            //正文可能为 null（外部改坏文件),此处处理为空字符串
            String content = excerpt.getContent() == null ? "" : excerpt.getContent();
            lines.add((i + 1) + "." + page + "|" + content);
        }
        return String.join("\n", lines);
    }
}
