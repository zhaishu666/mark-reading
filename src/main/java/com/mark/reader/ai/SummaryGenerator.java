package com.mark.reader.ai;

/**
 * 将一段文本变成摘要,输入的必须是纯文本,而不是List<Excerpt>
 * <p>
 * 是为了不依赖model包,保持接口干净
 * */
public interface SummaryGenerator {
    String summarize(String text);
}
