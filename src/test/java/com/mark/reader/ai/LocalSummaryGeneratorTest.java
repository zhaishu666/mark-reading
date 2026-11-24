package com.mark.reader.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalSummaryGeneratorTest {

    @Test
    @DisplayName("测试本地摘要的各项功能")
    void local_truncates_long_text() {
        LocalSummaryGenerator generator = new LocalSummaryGenerator();

        String s = generator.summarize("字".repeat(300));
        assertTrue(s.startsWith("【本地摘要】"));
        assertTrue(s.length() < 200);
        assertTrue(s.contains("共 300 字"));
    }
}
