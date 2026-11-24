package com.mark.reader.ai;

/**
 * 纯本地的摘要实现，作为降级的最后一道防线。
 * <p>
 * 它不做任何网络调用、不抛异常 —— 一旦它也失败，整条链路就没有兜底了。
 * 产出的是「原文截断」而不是真正的摘要：前缀 {@code 【本地摘要】} 让调用方一眼看出
 * 这次结果是降级得来的，而不是模型给出的。
 */
public class LocalSummaryGenerator implements SummaryGenerator {

    /** 截断阈值：超过这个长度只保留开头部分，避免把整段原文糊到界面上 */
    private static final int MAX_CHARS = 120;

    /** 结果前缀，用于区分「模型摘要」与「本地兜底」 */
    private static final String PREFIX = "【本地摘要】";

    /**
     * 生成本地摘要：原文不超过阈值时原样返回，超过则截断并标注总字数。
     *
     * @param text 待摘要的文本
     * @return 摘要结果，永不为 null，也永不抛异常
     */
    @Override
    public String summarize(String text) {
        // isBlank 而不只是 isEmpty：纯空白的输入同样没有摘要价值
        if (text == null || text.isBlank()) {
            return PREFIX + "无可摘要内容";
        }
        // 没超长就原样给出，不做任何加工
        if (text.length() <= MAX_CHARS) {
            return PREFIX + text;
        }
        // 超长时截断，并把原始总字数带上，让读者知道被砍掉了多少
        return PREFIX + text.substring(0, MAX_CHARS) + "…（共 " + text.length() + " 字）";
    }
}
