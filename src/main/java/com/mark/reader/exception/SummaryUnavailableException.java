package com.mark.reader.exception;

/**
 * 标记摘要服务不可用,可以降级
 * */
public class SummaryUnavailableException extends RuntimeException {
    public SummaryUnavailableException(String message) {
        super(message);
    }
    public SummaryUnavailableException(String message, Throwable cause){
        super(message, cause);
    }
}
