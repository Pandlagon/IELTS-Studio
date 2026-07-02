package com.ieltsstudio.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * {@link AiUsageQuota} 单元测试。
 *
 * <p>轻量测试：字段可正常 set / get，{@code toString()} 安全（无敏感字段）。</p>
 */
class AiUsageQuotaTest {

    @Test
    void shouldSetAndGetFields() {
        AiUsageQuota q = new AiUsageQuota();
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 1, 8, 0, 0);
        LocalDateTime now = LocalDateTime.now();

        q.setId(1L);
        q.setUserId(100L);
        q.setPeriodStart(start);
        q.setPeriodEnd(end);
        q.setCreditsTotal(100);
        q.setCreditsUsed(5);
        q.setCreatedAt(now);
        q.setUpdatedAt(now);

        assertEquals(1L, q.getId());
        assertEquals(100L, q.getUserId());
        assertEquals(start, q.getPeriodStart());
        assertEquals(end, q.getPeriodEnd());
        assertEquals(100, q.getCreditsTotal());
        assertEquals(5, q.getCreditsUsed());
        assertEquals(now, q.getCreatedAt());
        assertEquals(now, q.getUpdatedAt());
    }

    @Test
    void toStringShouldNotCrash() {
        AiUsageQuota q = new AiUsageQuota();
        q.setUserId(100L);
        q.setCreditsTotal(100);
        q.setCreditsUsed(5);
        assertDoesNotThrow(() -> q.toString());
    }
}
