package com.mark.reader.repository;

import com.mark.reader.model.Book;

import java.util.List;
import java.util.Optional;

/**
 * 书籍仓储接口。
 * <p>
 * 这一层只负责「把对象存进去、取出来」，不包含任何业务规则（书名是否重复、页数是否合法，
 * 都属于 BookService 的职责）。
 * 之所以先定义接口再写实现，是为了将来把文件存储换成 MySQL 时，
 * 只需新增一个实现类并在 Main 里改一行装配代码，上层的服务类完全不用动。
 */
public interface BookRepository {

    /**
     * 计算下一个可用的书籍主键。
     * 由仓储层负责分配主键，业务层只负责使用，避免多套 id 生成规则互相打架。
     *
     * @return 下一个可用主键，空库时返回 1
     */
    long nextId();

    /**
     * 保存一本书：主键为 0 时视为新增并自动分配主键，主键已存在时视为更新。
     *
     * @param book 待保存的书籍，不允许为 null
     * @return 保存后的书籍对象，新增场景下其主键已被填充
     */
    Book save(Book book);

    /**
     * 按主键查找书籍。
     *
     * @param id 书籍主键
     * @return 命中时返回包含书籍的 Optional，未命中返回空 Optional
     */
    Optional<Book> findById(long id);

    /**
     * 按书名精确查找书籍，比较时忽略首尾空白与大小写。
     *
     * @param title 书名
     * @return 命中时返回包含书籍的 Optional，未命中返回空 Optional
     */
    Optional<Book> findByTitle(String title);

    /**
     * 查询书库中的全部书籍。
     *
     * @return 书籍列表，按主键升序；书库为空时返回空列表
     */
    List<Book> findAll();

    /**
     * 按主键删除书籍。
     *
     * @param id 书籍主键
     * @return true 表示确实删掉了一本书，false 表示主键不存在
     */
    boolean deleteById(long id);
}
