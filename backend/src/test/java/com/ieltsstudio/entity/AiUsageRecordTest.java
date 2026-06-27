package com.ieltsstudio.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * {@link AiUsageRecord} 单元测试。
 *
 * <p>轻量测试：字段可正常 set / get，{@code toString()} 安全。</p>
 */
class AiUsageRecordTest {

    @Test
    void shouldSetAndGetFields() {
        AiUsageRecord r = new AiUsageRecord();
        LocalDateTime now = LocalDateTime.now();

        r.setId(1L);
        r.setUserId(100L);
        r.setTaskType("TEXT");
        r.setFeature("grade-writing");
        r.setCost(2);
        r.setKeyMode("BUILTIN");
        r.setProvider("DEEPSEEK");
        r.setStatus("SUCCESS");
        r.setErrorMessage("rate limit exceeded");
        r.setCreatedAt(now);

        assertEquals(1L, r.getId());
        assertEquals(100L, r.getUserId());
        assertEquals("TEXT", r.getTaskType());
        assertEquals("grade-writing", r.getFeature());
        assertEquals(2, r.getCost());
        assertEquals("BUILTIN", r.getKeyMode());
        assertEquals("DEEPSEEK", r.getProvider());
        assertEquals("SUCCESS", r.getStatus());
        assertEquals("rate limit exceeded", r.getErrorMessage());
        assertEquals(now, r.getCreatedAt());
    }

    @Test
    void toStringShouldNotCrash() {
        AiUsageRecord r = new AiUsageRecord();
        r.setUserId(100L);
        r.setFeature("ai-chat");
        r.setStatus("FAILED");
        assertDoesNotThrow(() -> r.toString());
    }
}
