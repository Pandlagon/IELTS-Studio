package com.ieltsstudio.ai.service;

import com.ieltsstudio.ai.AiFeature;
import com.ieltsstudio.ai.AiKeyMode;
import com.ieltsstudio.entity.AiUsageQuota;
import com.ieltsstudio.entity.AiUsageRecord;
import com.ieltsstudio.mapper.AiUsageQuotaMapper;
import com.ieltsstudio.mapper.AiUsageRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AiUsageGuard} 单元测试（Phase 6A + Phase 6B-2C）。
 *
 * <p>不连接真实数据库：{@link AiUsageQuotaMapper} / {@link AiUsageRecordMapper} 用 Mockito mock。
 * 不连接真实 Redis：{@link AiRedisRateLimiter} / {@link ObjectProvider} 用 Mockito mock。
 * 验证 BUILTIN 预扣+回滚、USER 限流（Redis 优先 + 内存降级）、流水写入与 errorMessage 脱敏。</p>
 */
class AiUsageGuardTest {

    private static final Long USER_ID = 1L;

    private AiUsageQuotaMapper quotaMapper;
    private AiUsageRecordMapper recordMapper;
    private AiUsageGuard guard;

    @BeforeEach
    void setUp() {
        quotaMapper = mock(AiUsageQuotaMapper.class);
        recordMapper = mock(AiUsageRecordMapper.class);
        // 默认构造时不注入 Redis limiter（模拟 app.redis.enabled=false），
        // 保持旧用例语义：USER 走单机内存限流。
        guard = newGuardWithRedis(null);
    }

    /** 构造 guard 并注入可选的 Redis limiter mock；传 null 模拟 Redis 未启用 */
    @SuppressWarnings("unchecked")
    private AiUsageGuard newGuardWithRedis(AiRedisRateLimiter redisMock) {
        ObjectProvider<AiRedisRateLimiter> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redisMock);
        return new AiUsageGuard(quotaMapper, recordMapper, provider);
    }

    // ─── 1. BUILTIN：当前周期无 quota 时创建并预扣 ─────────────────────────────

    @Test
    void builtinShouldCreateQuotaAndReserveCredits() {
        when(quotaMapper.selectOne(any())).thenReturn(null);
        // 模拟 MyBatis-Plus + IdType.AUTO：insert 后把自增 ID 回填到 entity
        when(quotaMapper.insert(any(AiUsageQuota.class))).thenAnswer(inv -> {
            inv.getArgument(0, AiUsageQuota.class).setId(1L);
            return 1;
        });
        when(quotaMapper.update(any(), any())).thenReturn(1); // 预扣成功

        guard.checkBeforeCall(USER_ID, AiFeature.WRITING_GRADE, AiKeyMode.BUILTIN);

        // 创建 quota：creditsTotal=30, creditsUsed=0
        ArgumentCaptor<AiUsageQuota> quotaCaptor = ArgumentCaptor.forClass(AiUsageQuota.class);
        verify(quotaMapper).insert(quotaCaptor.capture());
        AiUsageQuota created = quotaCaptor.getValue();
        assertEquals(30, created.getCreditsTotal());
        assertEquals(0, created.getCreditsUsed());

        // 原子预扣执行
        verify(quotaMapper).update(any(), any());

        // 未写 REJECTED 流水
        verify(recordMapper, never()).insert(any(AiUsageRecord.class));
    }

    // ─── 1b. BUILTIN：insert 后 id 未回填，reload 当前周期 quota 再预扣 ───────

    @Test
    void builtinShouldReloadQuotaWhenInsertedIdNotReturned() {
        AiUsageQuota reloaded = new AiUsageQuota();
        reloaded.setId(200L);
        reloaded.setUserId(USER_ID);
        reloaded.setCreditsTotal(30);
        reloaded.setCreditsUsed(0);

        when(quotaMapper.selectOne(any()))
                .thenReturn(null)        // 第一次：当前周期无 quota
                .thenReturn(reloaded);   // 第二次：insert 后 reload
        when(quotaMapper.insert(any(AiUsageQuota.class))).thenReturn(1); // 不回填 id
        when(quotaMapper.update(any(), any())).thenReturn(1);

        guard.checkBeforeCall(USER_ID, AiFeature.WRITING_GRADE, AiKeyMode.BUILTIN);

        verify(quotaMapper).insert(any(AiUsageQuota.class));
        verify(quotaMapper, times(2)).selectOne(any()); // 初次查询 + reload
        verify(quotaMapper).update(any(), any());
        verify(recordMapper, never()).insert(any(AiUsageRecord.class));
    }

    // ─── 1c. BUILTIN：insert 后 id 未回填且 reload 仍查不到，抛防御异常 ────────

    @Test
    void builtinShouldThrowWhenInsertedQuotaCannotBeReloaded() {
        when(quotaMapper.selectOne(any())).thenReturn(null); // 初次 + reload 都查不到
        when(quotaMapper.insert(any(AiUsageQuota.class))).thenReturn(1); // 不回填 id

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard.checkBeforeCall(USER_ID, AiFeature.WRITING_GRADE, AiKeyMode.BUILTIN));
        assertTrue(ex.getMessage().contains("AI quota row was created but cannot be reloaded"));

        verify(quotaMapper, times(2)).selectOne(any());
        verify(quotaMapper, never()).update(any(), any());
        verify(recordMapper, never()).insert(any(AiUsageRecord.class));
    }

    // ─── 2. BUILTIN：额度不足拒绝 ──────────────────────────────────────────────

    @Test
    void builtinShouldRejectWhenCreditsInsufficient() {
        AiUsageQuota quota = new AiUsageQuota();
        quota.setId(100L);
        quota.setUserId(USER_ID);
        quota.setCreditsTotal(30);
        quota.setCreditsUsed(30); // 已用完
        when(quotaMapper.selectOne(any())).thenReturn(quota);
        when(quotaMapper.update(any(), any())).thenReturn(0); // 预扣失败

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard.checkBeforeCall(USER_ID, AiFeature.WRITING_GRADE, AiKeyMode.BUILTIN));
        assertTrue(ex.getMessage().contains("额度已用完"));

        // 未创建新 quota
        verify(quotaMapper, never()).insert(any(AiUsageQuota.class));
        // 写 REJECTED 流水
        ArgumentCaptor<AiUsageRecord> recCaptor = ArgumentCaptor.forClass(AiUsageRecord.class);
        verify(recordMapper).insert(recCaptor.capture());
        AiUsageRecord rec = recCaptor.getValue();
        assertEquals("REJECTED", rec.getStatus());
        assertEquals(0, rec.getCost());
        assertEquals("BUILTIN", rec.getKeyMode());
        assertEquals("WRITING_GRADE", rec.getFeature());
        assertEquals("INSUFFICIENT_CREDITS", rec.getErrorMessage());
    }

    // ─── 3. BUILTIN markSuccess：写 SUCCESS 流水，cost = feature cost ──────────

    @Test
    void builtinMarkSuccessShouldWriteSuccessRecord() {
        guard.markSuccess(USER_ID, AiFeature.WRITING_GRADE, AiKeyMode.BUILTIN);

        // 已预扣，此处不再扣费
        verify(quotaMapper, never()).update(any(), any());
        verify(quotaMapper, never()).insert(any(AiUsageQuota.class));

        ArgumentCaptor<AiUsageRecord> recCaptor = ArgumentCaptor.forClass(AiUsageRecord.class);
        verify(recordMapper).insert(recCaptor.capture());
        AiUsageRecord rec = recCaptor.getValue();
        assertEquals("SUCCESS", rec.getStatus());
        assertEquals(AiFeature.WRITING_GRADE.getBuiltinCost(), rec.getCost());
        assertEquals("WRITING_GRADE", rec.getFeature());
        assertEquals("TEXT", rec.getTaskType());
        assertEquals("BUILTIN", rec.getKeyMode());
        assertNull(rec.getErrorMessage());
    }

    // ─── 4. BUILTIN markFailure：回滚 credits + 写 FAILED 流水 ──────────────────

    @Test
    void builtinMarkFailureShouldRefundAndWriteFailedRecord() {
        Exception ex = new RuntimeException("connection timeout");

        guard.markFailure(USER_ID, AiFeature.EXAM_PRECISE_PARSE, AiKeyMode.BUILTIN, ex);

        // 执行了 quota 回滚
        verify(quotaMapper).update(any(), any());

        ArgumentCaptor<AiUsageRecord> recCaptor = ArgumentCaptor.forClass(AiUsageRecord.class);
        verify(recordMapper).insert(recCaptor.capture());
        AiUsageRecord rec = recCaptor.getValue();
        assertEquals("FAILED", rec.getStatus());
        assertEquals(0, rec.getCost()); // 失败不消耗 credits
        assertEquals("EXAM_PRECISE_PARSE", rec.getFeature());
        assertEquals("VISION", rec.getTaskType());
        assertEquals("BUILTIN", rec.getKeyMode());
        assertNotNull(rec.getErrorMessage());
        assertFalse(rec.getErrorMessage().contains("sk-"));
    }

    // ─── 5. USER 模式：不触碰 quota，仅写 SUCCESS 流水（cost=0） ──────────────

    @Test
    void userModeShouldNotTouchQuotaButWriteSuccessRecord() {
        guard.checkBeforeCall(USER_ID, AiFeature.TRANSLATE, AiKeyMode.USER);
        guard.markSuccess(USER_ID, AiFeature.TRANSLATE, AiKeyMode.USER);

        // 完全不触碰 quota
        verify(quotaMapper, never()).selectOne(any());
        verify(quotaMapper, never()).insert(any(AiUsageQuota.class));
        verify(quotaMapper, never()).update(any(), any());

        ArgumentCaptor<AiUsageRecord> recCaptor = ArgumentCaptor.forClass(AiUsageRecord.class);
        verify(recordMapper).insert(recCaptor.capture());
        AiUsageRecord rec = recCaptor.getValue();
        assertEquals("SUCCESS", rec.getStatus());
        assertEquals(0, rec.getCost());
        assertEquals("USER", rec.getKeyMode());
        assertEquals("TRANSLATE", rec.getFeature());
    }

    // ─── 6. USER 限流：超过 20 次/分钟后拒绝 ──────────────────────────────────

    @Test
    void userRateLimitShouldRejectAfterLimit() {
        // 前 20 次通过
        for (int i = 0; i < 20; i++) {
            guard.checkBeforeCall(USER_ID, AiFeature.AI_CHAT, AiKeyMode.USER);
        }
        // 第 21 次被限流
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard.checkBeforeCall(USER_ID, AiFeature.AI_CHAT, AiKeyMode.USER));
        assertTrue(ex.getMessage().contains("请求过于频繁"));

        // 仅第 21 次写了一条 REJECTED 流水
        ArgumentCaptor<AiUsageRecord> recCaptor = ArgumentCaptor.forClass(AiUsageRecord.class);
        verify(recordMapper).insert(recCaptor.capture());
        AiUsageRecord rec = recCaptor.getValue();
        assertEquals("REJECTED", rec.getStatus());
        assertEquals(0, rec.getCost());
        assertEquals("USER", rec.getKeyMode());
        assertEquals("RATE_LIMITED", rec.getErrorMessage());
    }

    // ─── 7. markFailure：errorMessage 脱敏 API Key / Authorization ─────────────

    @Test
    void failureRecordShouldSanitizeApiKeyAndAuthorization() {
        Exception ex = new RuntimeException(
                "Authorization: Bearer sk-test-secret-123 invalid key sk-test-secret-123");

        guard.markFailure(USER_ID, AiFeature.AI_CHAT, AiKeyMode.BUILTIN, ex);

        ArgumentCaptor<AiUsageRecord> recCaptor = ArgumentCaptor.forClass(AiUsageRecord.class);
        verify(recordMapper).insert(recCaptor.capture());
        AiUsageRecord rec = recCaptor.getValue();
        String msg = rec.getErrorMessage();
        assertNotNull(msg);
        assertFalse(msg.contains("sk-test-secret"), "errorMessage 不得包含原始 key");
        assertFalse(msg.contains("Authorization: Bearer sk-test-secret"),
                "errorMessage 不得包含原始 Authorization 头");
        assertTrue(msg.contains("[REDACTED]"), "脱敏后应包含 [REDACTED]");
    }

    // ─── 8. 参数校验：null userId / feature / keyMode 一律拒绝 ─────────────────

    @Test
    void validateShouldRejectNullUserIdFeatureOrKeyMode() {
        assertThrows(IllegalArgumentException.class,
                () -> guard.checkBeforeCall(null, AiFeature.WRITING_GRADE, AiKeyMode.BUILTIN));
        assertThrows(IllegalArgumentException.class,
                () -> guard.checkBeforeCall(USER_ID, null, AiKeyMode.BUILTIN));
        assertThrows(IllegalArgumentException.class,
                () -> guard.checkBeforeCall(USER_ID, AiFeature.WRITING_GRADE, null));
    }

    // ─── 9. Phase 6B-2A：provider-aware overload 写入 provider（REJECTED-BUILTIN） ─

    @Test
    void builtinRejectedRecordShouldIncludeProvider() {
        AiUsageQuota quota = new AiUsageQuota();
        quota.setId(100L);
        quota.setUserId(USER_ID);
        quota.setCreditsTotal(30);
        quota.setCreditsUsed(30); // 已用完
        when(quotaMapper.selectOne(any())).thenReturn(quota);
        when(quotaMapper.update(any(), any())).thenReturn(0); // 预扣失败

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard.checkBeforeCall(USER_ID, AiFeature.WRITING_GRADE, AiKeyMode.BUILTIN, "DEEPSEEK"));
        assertTrue(ex.getMessage().contains("额度已用完"));

        ArgumentCaptor<AiUsageRecord> recCaptor = ArgumentCaptor.forClass(AiUsageRecord.class);
        verify(recordMapper).insert(recCaptor.capture());
        AiUsageRecord rec = recCaptor.getValue();
        assertEquals("REJECTED", rec.getStatus());
        assertEquals("DEEPSEEK", rec.getProvider(), "REJECTED record 必须写入 provider");
        assertEquals("BUILTIN", rec.getKeyMode());
        assertEquals("WRITING_GRADE", rec.getFeature());
        assertEquals("INSUFFICIENT_CREDITS", rec.getErrorMessage());
    }

    // ─── 10. Phase 6B-2A：provider-aware overload 写入 provider（REJECTED-USER） ──

    @Test
    void userRejectedRecordShouldIncludeProvider() {
        // 前 20 次通过
        for (int i = 0; i < 20; i++) {
            guard.checkBeforeCall(USER_ID, AiFeature.AI_CHAT, AiKeyMode.USER, "QWEN");
        }
        // 第 21 次被限流
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard.checkBeforeCall(USER_ID, AiFeature.AI_CHAT, AiKeyMode.USER, "QWEN"));
        assertTrue(ex.getMessage().contains("请求过于频繁"));

        // 仅第 21 次写了一条 REJECTED 流水
        ArgumentCaptor<AiUsageRecord> recCaptor = ArgumentCaptor.forClass(AiUsageRecord.class);
        verify(recordMapper).insert(recCaptor.capture());
        AiUsageRecord rec = recCaptor.getValue();
        assertEquals("REJECTED", rec.getStatus());
        assertEquals("QWEN", rec.getProvider(), "USER rate-limited REJECTED record 必须写入 provider");
        assertEquals("USER", rec.getKeyMode());
        assertEquals("AI_CHAT", rec.getFeature());
        assertEquals("RATE_LIMITED", rec.getErrorMessage());
    }

    // ─── 11. Phase 6B-2A：markSuccess provider-aware 写入 provider ──────────────

    @Test
    void markSuccessShouldWriteProvider() {
        guard.markSuccess(USER_ID, AiFeature.TRANSLATE, AiKeyMode.BUILTIN, "MIMO");

        ArgumentCaptor<AiUsageRecord> recCaptor = ArgumentCaptor.forClass(AiUsageRecord.class);
        verify(recordMapper).insert(recCaptor.capture());
        AiUsageRecord rec = recCaptor.getValue();
        assertEquals("SUCCESS", rec.getStatus());
        assertEquals("MIMO", rec.getProvider(), "markSuccess 必须写入 provider");
        assertEquals("BUILTIN", rec.getKeyMode());
        assertEquals("TRANSLATE", rec.getFeature());
        assertEquals(AiFeature.TRANSLATE.getBuiltinCost(), rec.getCost());
    }

    // ─── 12. Phase 6B-2A：markFailure provider-aware 写入 provider ──────────────

    @Test
    void markFailureShouldWriteProvider() {
        Exception ex = new RuntimeException("connection timeout");

        guard.markFailure(USER_ID, AiFeature.EXAM_PRECISE_PARSE, AiKeyMode.BUILTIN, "OPENAI_COMPATIBLE", ex);

        ArgumentCaptor<AiUsageRecord> recCaptor = ArgumentCaptor.forClass(AiUsageRecord.class);
        verify(recordMapper).insert(recCaptor.capture());
        AiUsageRecord rec = recCaptor.getValue();
        assertEquals("FAILED", rec.getStatus());
        assertEquals("OPENAI_COMPATIBLE", rec.getProvider(), "markFailure 必须写入 provider");
        assertEquals("BUILTIN", rec.getKeyMode());
        assertEquals("EXAM_PRECISE_PARSE", rec.getFeature());
        assertEquals(0, rec.getCost());
        assertNotNull(rec.getErrorMessage());
        // provider 独立于 errorMessage，不被 sanitize 影响
        assertEquals("OPENAI_COMPATIBLE", rec.getProvider());
    }

    // ─── 13. Phase 6B-2A：旧三参数方法仍可用，provider=null ────────────────────

    @Test
    void legacyMethodsShouldStillWriteNullProvider() {
        // 旧 checkBeforeCall + markSuccess（不传 provider）
        guard.checkBeforeCall(USER_ID, AiFeature.TRANSLATE, AiKeyMode.USER);
        guard.markSuccess(USER_ID, AiFeature.TRANSLATE, AiKeyMode.USER);

        ArgumentCaptor<AiUsageRecord> recCaptor = ArgumentCaptor.forClass(AiUsageRecord.class);
        verify(recordMapper).insert(recCaptor.capture());
        AiUsageRecord rec = recCaptor.getValue();
        assertEquals("SUCCESS", rec.getStatus());
        assertNull(rec.getProvider(), "旧三参数方法应写入 provider=null");

        // 旧 markFailure（不传 provider）
        guard.markFailure(USER_ID, AiFeature.AI_CHAT, AiKeyMode.USER, new RuntimeException("err"));
        ArgumentCaptor<AiUsageRecord> failCaptor = ArgumentCaptor.forClass(AiUsageRecord.class);
        verify(recordMapper, times(2)).insert(failCaptor.capture());
        AiUsageRecord failRec = failCaptor.getAllValues().get(1);
        assertEquals("FAILED", failRec.getStatus());
        assertNull(failRec.getProvider(), "旧 markFailure 也应写入 provider=null");
    }

    // ─── 14. Phase 6B-2C：USER 限流优先使用 Redis limiter ───────────────────────

    @Test
    void userRateLimitShouldUseRedisLimiterWhenAvailable() {
        AiRedisRateLimiter redisMock = mock(AiRedisRateLimiter.class);
        when(redisMock.isAllowed(eq(USER_ID), eq(AiFeature.AI_CHAT), anyInt())).thenReturn(true);
        AiUsageGuard g = newGuardWithRedis(redisMock);

        g.checkBeforeCall(USER_ID, AiFeature.AI_CHAT, AiKeyMode.USER, "DEEPSEEK");

        // Redis limiter 被调用一次
        verify(redisMock, times(1)).isAllowed(eq(USER_ID), eq(AiFeature.AI_CHAT), anyInt());
        // 没写 REJECTED 流水（仅 checkBeforeCall，未调 markSuccess，故 recordMapper.insert 总次数为 0）
        verify(recordMapper, never()).insert(any(AiUsageRecord.class));
    }

    // ─── 15. Phase 6B-2C：Redis limiter 拒绝时写 REJECTED 流水，provider 保留 ──

    @Test
    void userRateLimitShouldRejectWhenRedisLimiterRejects() {
        AiRedisRateLimiter redisMock = mock(AiRedisRateLimiter.class);
        when(redisMock.isAllowed(eq(USER_ID), eq(AiFeature.AI_CHAT), anyInt())).thenReturn(false);
        AiUsageGuard g = newGuardWithRedis(redisMock);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> g.checkBeforeCall(USER_ID, AiFeature.AI_CHAT, AiKeyMode.USER, "QWEN"));
        assertTrue(ex.getMessage().contains("请求过于频繁"));
        // 不向用户暴露 Redis 内部细节
        assertFalse(ex.getMessage().toLowerCase().contains("redis"));

        ArgumentCaptor<AiUsageRecord> recCaptor = ArgumentCaptor.forClass(AiUsageRecord.class);
        verify(recordMapper).insert(recCaptor.capture());
        AiUsageRecord rec = recCaptor.getValue();
        assertEquals("REJECTED", rec.getStatus());
        assertEquals("QWEN", rec.getProvider(), "Redis 拒绝路径仍需写入 provider");
        assertEquals("USER", rec.getKeyMode());
        assertEquals("AI_CHAT", rec.getFeature());
        assertEquals(0, rec.getCost());
        assertEquals("RATE_LIMITED", rec.getErrorMessage());
    }

    // ─── 16. Phase 6B-2C：Redis limiter 抛异常时降级到内存限流 ────────────────

    @Test
    void userRateLimitShouldFallbackToMemoryWhenRedisThrows() {
        AiRedisRateLimiter redisMock = mock(AiRedisRateLimiter.class);
        // 每次调用 Redis 都抛异常，模拟 Redis 不可用
        when(redisMock.isAllowed(anyLong(), any(), anyInt()))
                .thenThrow(new RuntimeException("Redis connection refused"));
        AiUsageGuard g = newGuardWithRedis(redisMock);

        // 前 20 次：Redis 抛异常 → fallback 内存 → 内存计数 1..20，全部通过
        for (int i = 0; i < 20; i++) {
            g.checkBeforeCall(USER_ID, AiFeature.AI_CHAT, AiKeyMode.USER, "MIMO");
        }
        // 第 21 次：Redis 抛异常 → fallback 内存 → 内存计数 21 > 20 → 拒绝
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> g.checkBeforeCall(USER_ID, AiFeature.AI_CHAT, AiKeyMode.USER, "MIMO"));
        assertTrue(ex.getMessage().contains("请求过于频繁"));
        // 不向用户暴露 Redis 异常 message
        assertFalse(ex.getMessage().toLowerCase().contains("redis"));
        assertFalse(ex.getMessage().contains("connection refused"));

        // Redis limiter 被调用 21 次（每次都 fallback）
        verify(redisMock, times(21)).isAllowed(anyLong(), any(), anyInt());

        // 仅第 21 次写了一条 REJECTED 流水
        ArgumentCaptor<AiUsageRecord> recCaptor = ArgumentCaptor.forClass(AiUsageRecord.class);
        verify(recordMapper).insert(recCaptor.capture());
        AiUsageRecord rec = recCaptor.getValue();
        assertEquals("REJECTED", rec.getStatus());
        assertEquals("MIMO", rec.getProvider(), "fallback 路径仍需写入 provider");
        assertEquals("USER", rec.getKeyMode());
        assertEquals("RATE_LIMITED", rec.getErrorMessage());
    }

    // ─── 17. Phase 6B-2C：Redis limiter bean 不存在时走内存，保持旧行为 ───────

    @Test
    void userRateLimitShouldFallbackToMemoryWhenNoRedisLimiterBean() {
        // ObjectProvider.getIfAvailable() 返回 null（app.redis.enabled=false 或无 bean）
        // setUp 已经构造了这样的 guard，直接复用

        // 前 20 次通过
        for (int i = 0; i < 20; i++) {
            guard.checkBeforeCall(USER_ID, AiFeature.AI_CHAT, AiKeyMode.USER, "OPENAI_COMPATIBLE");
        }
        // 第 21 次被限流
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard.checkBeforeCall(USER_ID, AiFeature.AI_CHAT, AiKeyMode.USER, "OPENAI_COMPATIBLE"));
        assertTrue(ex.getMessage().contains("请求过于频繁"));

        ArgumentCaptor<AiUsageRecord> recCaptor = ArgumentCaptor.forClass(AiUsageRecord.class);
        verify(recordMapper).insert(recCaptor.capture());
        AiUsageRecord rec = recCaptor.getValue();
        assertEquals("REJECTED", rec.getStatus());
        assertEquals("OPENAI_COMPATIBLE", rec.getProvider(), "无 Redis bean 时仍需写入 provider");
        assertEquals("USER", rec.getKeyMode());
        assertEquals("RATE_LIMITED", rec.getErrorMessage());
    }
}
