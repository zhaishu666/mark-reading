package com.mark.reader.repository;

import com.mark.reader.model.Excerpt;

import java.util.List;
import java.util.Optional;

/**
 * 书摘仓储接口。
 * <p>
 * 与 {@link BookRepository} 一样，只做数据存取，不含业务规则。
 * 「书摘是否重复」「页码是否越界」这类判断属于 ExcerptService 的职责。
 */
public interface ExcerptRepository {

    /**
     * 计算下一个可用的书摘主键。
     * 书摘主键与书籍主键各自独立编号，互不干扰。
     *
     * @return 下一个可用主键，空库时返回 1
     */
    long nextId();

    /**
     * 保存一条书摘：主键为 0 时视为新增并自动分配主键，主键已存在时视为更新。
     *
     * @param excerpt 待保存的书摘，不允许为 null
     * @return 保存后的书摘对象，新增场景下其主键已被填充
     */
    Excerpt save(Excerpt excerpt);

    /**
     * 按主键查找书摘。
     *
     * @param id 书摘主键
     * @return 命中时返回包含书摘的 Optional，未命中返回空 Optional
     */
    Optional<Excerpt> findById(long id);

    /**
     * 查询某本书的全部书摘。
     *
     * @param bookId 书籍主键
     * @return 该书摘录列表，按主键升序；没有摘录时返回空列表
     */
    List<Excerpt> findByBookId(long bookId);

    /**
     * 查询书库中的全部书摘。
     *
     * @return 书摘列表，按主键升序；没有任何摘录时返回空列表
     */
    List<Excerpt> findAll();

    /**
     * 删除某本书的全部书摘，用于书籍被删除时做级联清理，避免留下孤儿数据。
     *
     * @param bookId 书籍主键
     * @return 实际删除的书摘条数，没有任何摘录时返回 0
     */
    int deleteByBookId(long bookId);
}
