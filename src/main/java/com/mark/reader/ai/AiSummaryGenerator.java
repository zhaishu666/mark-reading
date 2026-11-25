package com.mark.reader.ai;

import com.mark.reader.exception.SummaryUnavailableException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;

import java.util.Objects;

public class AiSummaryGenerator implements SummaryGenerator {

    private final ChatModel chatModel;

    private static final String INSTRUCTION = """
        你是读书笔记助手。请把下面的书摘整理成 3-5 句话的摘要，
        保留作者的核心观点，不要编造书摘里没有的内容。

        """;

    public AiSummaryGenerator(ChatModel chatModel) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel 不能为null");
    }

    @Override
    public String summarize(String text) {
        try {
            return chatModel.call(INSTRUCTION + text);
        } catch (NonTransientAiException | TransientAiException e) {
            throw new SummaryUnavailableException("模型调用失败 ", e);
        }
    }
}

