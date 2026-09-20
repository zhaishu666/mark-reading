package com.mark.reader.exception;

/**
 * 书籍不存在异常。
 * <p>
 * 当按主键查找书籍却查不到时抛出。例如记录书摘时指定的 bookId 在书库中并不存在，
 * 说明调用方传入了脏数据，属于编程层面的错误而非用户输入问题，因此同样使用运行时异常。
 */
public class BookNotFoundException extends RuntimeException {

    /** 序列化版本号，继承自 RuntimeException 的类按惯例声明 */
    private static final long serialVersionUID = 1L;

    /**
     * 使用书籍主键构造异常，自动拼出提示信息。
     *
     * @param bookId 查不到的书籍主键
     */
    public BookNotFoundException(long bookId) {
        // 直接生成一句可供界面展示的中文提示
        super("未找到 id 为 " + bookId + " 的书籍");
    }

    /**
     * 使用自定义信息构造异常，便于按书名等条件查找失败时给出更贴切的提示。
     *
     * @param message 面向用户的错误说明
     */
    public BookNotFoundException(String message) {
        // 交给父类保存错误信息
        super(message);
    }
}
