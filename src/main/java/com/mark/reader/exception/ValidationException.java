package com.mark.reader.exception;

/**
 * 输入校验异常。
 * <p>
 * 当用户输入不满足业务规则时抛出，例如书名为空白、页码为负数、书摘内容为空等。
 * 继承 RuntimeException 而非受检异常，这样业务层方法签名不必被 throws 污染，
 * 由最外层的交互层统一捕获并转换成友好的提示信息。
 */
public class ValidationException extends RuntimeException {

    /** 序列化版本号，继承自 RuntimeException 的类按惯例声明 */
    private static final long serialVersionUID = 1L;

    /**
     * 使用提示信息构造异常。
     *
     * @param message 面向用户的错误说明
     */
    public ValidationException(String message) {
        // 交给父类保存错误信息，后续可用 getMessage() 取出
        super(message);
    }
}
