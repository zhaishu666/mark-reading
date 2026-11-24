package com.mark.reader.ai;

import com.mark.reader.exception.SummaryUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FallbackSummaryGenerator 摘要生成器")
class FallbackSummaryGeneratorTest {

    /** 真实的兜底实现，不造假：降级到它时，行为要和线上一致 */
    private final LocalSummaryGenerator local = new LocalSummaryGenerator();
    private final Duration timeout = Duration.ofMillis(200);

    @Test
    @DisplayName("primary 正常时不降级，原样返回它的结果")
    void fallback_prefers_primary_healthy(){
        SummaryGenerator primary = text -> "AI 结果";
        FallbackSummaryGenerator generator = new FallbackSummaryGenerator(primary, local, timeout);
        assertEquals("AI 结果", generator.summarize("随便一段文字"));
    }

    @Test
    @DisplayName("primary 不可用时,降级到本地摘要")
    void fallback_uses_local_when_primary_unavailable(){
        SummaryGenerator primary = text -> {
            throw new SummaryUnavailableException("模型挂了");
        };
        FallbackSummaryGenerator generator = new FallbackSummaryGenerator(primary, local, timeout);
        assertTrue(generator.summarize("随便一段文字").startsWith("【本地摘要】"));
    }

    @Test
    @DisplayName("primary 超时,不等待直接降级到本地摘要")
    void fallback_uses_local_when_primary_times_out(){
        SummaryGenerator primary = text -> {
            try {
                Thread.sleep(2000);
            }catch (InterruptedException e){
                Thread.currentThread().interrupt();
            }
            return "太慢了";
        };
        FallbackSummaryGenerator generator = new FallbackSummaryGenerator(primary, local, timeout);
        String r = assertTimeout(Duration.ofSeconds(1), () -> generator.summarize("正文"));
        assertTrue(r.startsWith("【本地摘要】"));
    }

    @Test
    @DisplayName("实现错误,直接抛出不降级")
    void fallback_does_not_swallow_real_bugs(){
        SummaryGenerator primary = text -> {
            throw new NullPointerException("实现写错了");
        };
        FallbackSummaryGenerator generator = new FallbackSummaryGenerator(primary, local, timeout);
        assertThrows(NullPointerException.class, () -> generator.summarize("正文"));
    }


}
