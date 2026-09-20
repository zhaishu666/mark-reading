package com.mark.reader.service;

import com.mark.reader.exception.BookNotFoundException;
import com.mark.reader.exception.ValidationException;
import com.mark.reader.model.Book;
import com.mark.reader.model.Excerpt;
import com.mark.reader.repository.BookRepository;
import com.mark.reader.repository.ExcerptRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 书摘业务服务。
 * <p>
 * 承载与书摘相关的业务规则：所属书籍必须存在、正文不能为空、页码不能越界、
 * 同一本书内重复内容不重复入库、标签需要规范化。
 * <p>
 * 这里同时依赖两个仓储：书籍仓储用于校验书是否存在以及取总页数，
 * 书摘仓储负责落盘。虽然多了一个依赖，但换来的是一句「书摘一定挂在一本真实存在的书上」的保证。
 */
public class ExcerptService {

    /** 书摘仓储，负责书摘的读写 */
    private final ExcerptRepository excerptRepository;

    /** 书籍仓储，仅用于校验书籍存在性与取总页数 */
    private final BookRepository bookRepository;

    /** 时钟，用于获取当前时间，注入以便测试替换 */
    private final Clock clock;

    /**
     * 使用系统默认时钟构造服务，供生产环境使用。
     *
     * @param excerptRepository 书摘仓储
     * @param bookRepository    书籍仓储
     */
    public ExcerptService(ExcerptRepository excerptRepository, BookRepository bookRepository) {
        // 委托给全参构造函数，时钟取系统默认时区
        this(excerptRepository, bookRepository, Clock.systemDefaultZone());
    }

    /**
     * 全参构造函数。
     *
     * @param excerptRepository 书摘仓储
     * @param bookRepository    书籍仓储
     * @param clock             时钟，测试中可传入固定时钟
     */
    public ExcerptService(ExcerptRepository excerptRepository, BookRepository bookRepository, Clock clock) {
        // 保存书摘仓储引用
        this.excerptRepository = excerptRepository;
        // 保存书籍仓储引用
        this.bookRepository = bookRepository;
        // 保存时钟引用
        this.clock = clock;
    }

    /**
     * 记录一条书摘。
     * 处理顺序为：先确认书存在，再校验内容与页码，然后做去重，最后落盘。
     *
     * @param bookId  所属书籍主键
     * @param content 书摘正文，必填
     * @param page    所在页码，传 0 表示未标注
     * @param tags    标签列表，可为 null
     * @return 保存后的书摘；若同一本书中已有相同内容，则直接返回已存在的那一条
     * @throws BookNotFoundException 书籍不存在时抛出
     * @throws ValidationException   正文空白或页码越界时抛出
     */
    public Excerpt addExcerpt(long bookId, String content, int page, List<String> tags) {
        // 先确认书籍存在，不存在就抛出异常而不是写入孤儿数据
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
        // 校验正文不能为 null 或纯空白
        if (content == null || content.isBlank()) {
            throw new ValidationException("书摘内容不能为空");
        }
        // 校验页码不能为负数
        if (page < 0) {
            throw new ValidationException("页码不能为负数，未标注请填 0");
        }
        // 书籍总页数已知时，页码不允许超过总页数；未知则不做过严的限制
        if (book.isTotalPagesKnown() && page > book.getTotalPages()) {
            throw new ValidationException("页码 " + page + " 超出《" + book.getTitle()
                    + "》的总页数 " + book.getTotalPages());
        }
        // 正文去掉首尾空白后入库，保证去重判断稳定
        String normalizedContent = content.trim();
        // 查一下这本书里是否已有同样内容，避免重复摘抄同一句话
        Optional<Excerpt> duplicated = findSameContent(bookId, normalizedContent);
        if (duplicated.isPresent()) {
            // 已存在则直接返回原记录，让调用方知道「这条已经有了」
            return duplicated.get();
        }
        // 组装实体，主键留给仓储层分配
        Excerpt excerpt = new Excerpt(bookId, normalizedContent, page, normalizeTags(tags));
        // 记录创建时间，时间来源是注入的时钟
        excerpt.setCreatedAt(LocalDateTime.now(clock));
        // 交给仓储层落盘并返回
        return excerptRepository.save(excerpt);
    }

    /**
     * 查询某本书的全部书摘。
     *
     * @param bookId 书籍主键
     * @return 该书摘录列表，按主键升序
     * @throws BookNotFoundException 书籍不存在时抛出
     */
    public List<Excerpt> listByBook(long bookId) {
        // 先确认书存在，避免用户查询一本不存在的书时得到空列表而误以为「确实没摘录」
        if (bookRepository.findById(bookId).isEmpty()) {
            throw new BookNotFoundException(bookId);
        }
        // 返回该书全部书摘
        return excerptRepository.findByBookId(bookId);
    }

    /**
     * 在某本书中查找正文相同的书摘，内容比较时忽略首尾空白。
     *
     * @param bookId  书籍主键
     * @param content 已去除首尾空白的正文
     * @return 命中则返回已有书摘，未命中返回空 Optional
     */
    private Optional<Excerpt> findSameContent(long bookId, String content) {
        // 只在该书的书摘范围内比较，不同书里出现同一句话是合理的
        for (Excerpt existing : excerptRepository.findByBookId(bookId)) {
            // 正文可能为 null（外部改坏文件），需要先兜住
            if (existing.getContent() != null && existing.getContent().trim().equals(content)) {
                return Optional.of(existing);
            }
        }
        // 没有重复
        return Optional.empty();
    }

    /**
     * 规范化标签列表：去掉首尾空白、剔除空标签、按出现顺序去重。
     * 使用 LinkedHashSet 是为了既去重又保留用户输入的顺序，让展示结果稳定可预期。
     *
     * @param tags 原始标签列表，可为 null
     * @return 规范化后的标签列表，永不为 null
     */
    private List<String> normalizeTags(List<String> tags) {
        // 准备去重容器，LinkedHashSet 保证顺序
        Set<String> unique = new LinkedHashSet<>();
        // null 视为没有标签，直接返回空列表
        if (tags == null) {
            return new ArrayList<>();
        }
        // 逐个标签清洗
        for (String tag : tags) {
            // 跳过 null 项
            if (tag == null) {
                continue;
            }
            // 去掉首尾空白
            String trimmed = tag.trim();
            // 空白标签直接丢弃，避免产生噪音数据
            if (!trimmed.isEmpty()) {
                unique.add(trimmed);
            }
        }
        // 转成普通列表返回，便于后续序列化
        return new ArrayList<>(unique);
    }
}
