package com.ieltsstudio.ai.service;

import com.ieltsstudio.ai.AiFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AiRedisRateLimiter} 单元测试（Phase 6B-2C）。
 *
 * <p>不连接真实 Redis：{@link StringRedisTemplate} / {@link ValueOperations} 用 Mockito mock。
 * 验证固定窗口 INCR + TTL 流程、限流计数、null 返回值保护、key 不含敏感信息。</p>
 */
class AiRedisRateLimiterTest {

    private static final Long USER_ID = 42L;
    private static final int LIMIT = 20;

    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOps;
    private AiRedisRateLimiter limiter;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOps = (ValueOperations<String, String>) mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        limiter = new AiRedisRateLimiter(stringRedisTemplate);
    }

    // ─── 1. 首次调用：INCR 返回 1，设置 TTL，允许通过 ──────────────────────────

    @Test
    void redisLimiterShouldAllowWithinLimit() {
        when(valueOps.increment(any(String.class))).thenReturn(1L);

        boolean allowed = limiter.isAllowed(USER_ID, AiFeature.AI_CHAT, LIMIT);

        assertTrue(allowed);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).increment(keyCaptor.capture());
        String key = keyCaptor.getValue();
        assertTrue(key.contains("user:" + USER_ID));
        assertTrue(key.contains("feature:AI_CHAT"));

        // 首次写入应设置 TTL = 70 秒
        verify(stringRedisTemplate).expire(eq(key), eq(Duration.ofSeconds(70)));
    }

    // ─── 2. 超过限制：INCR 返回 21，拒绝，不要求 expire ─────────────────────────

    @Test
    void redisLimiterShouldRejectWhenCountExceedsLimit() {
        when(valueOps.increment(any(String.class))).thenReturn(21L);

        boolean allowed = limiter.isAllowed(USER_ID, AiFeature.AI_CHAT, LIMIT);

        assertFalse(allowed);

        // 计数 > 1 时不应再设置 TTL（首次 INCR 时已设置）
        verify(stringRedisTemplate, never()).expire(any(String.class), any(Duration.class));
    }

    // ─── 3. INCR 返回 null：抛异常由调用方捕获并降级 ───────────────────────────

    @Test
    void redisLimiterShouldThrowWhenIncrementReturnsNull() {
        when(valueOps.increment(any(String.class))).thenReturn(null);

        assertThrows(IllegalStateException.class,
                () -> limiter.isAllowed(USER_ID, AiFeature.AI_CHAT, LIMIT));

        verify(stringRedisTemplate, never()).expire(any(String.class), any(Duration.class));
    }

    // ─── 4. Key 安全性：不包含任何敏感信息 ─────────────────────────────────────

    @Test
    void redisKeyShouldNotContainSensitiveData() {
        when(valueOps.increment(any(String.class))).thenReturn(1L);

        limiter.isAllowed(USER_ID, AiFeature.WRITING_GRADE, LIMIT);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).increment(keyCaptor.capture());
        String key = keyCaptor.getValue();

        // key 必须包含 userId / feature / minute 标记
        assertTrue(key.contains("user:" + USER_ID), "key 必须包含 userId");
        assertTrue(key.contains("feature:WRITING_GRADE"), "key 必须包含 feature 名");
        assertTrue(key.contains("minute:"), "key 必须包含 minute 段");
        assertTrue(key.startsWith("ai:rate:user:"), "key 应以 ai:rate:user: 前缀开头");

        // key 不得包含任何敏感或可变业务信息
        assertFalse(key.contains("sk-"), "key 不得包含 API Key 片段");
        assertFalse(key.contains("Bearer"), "key 不得包含 Bearer");
        assertFalse(key.toLowerCase().contains("http"), "key 不得包含 http 协议");
        assertFalse(key.toLowerCase().contains("model"), "key 不得包含 model 名");
        assertFalse(key.toLowerCase().contains("provider"), "key 不得包含 provider");
        assertFalse(key.contains("请帮我"), "key 不得包含 prompt 片段");
        assertFalse(key.contains("password"), "key 不得包含 password");
    }
}
