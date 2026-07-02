package com.ieltsstudio.ai.service;

import com.ieltsstudio.dto.ai.UserAiUsageDto;
import com.ieltsstudio.entity.AiUsageQuota;
import com.ieltsstudio.entity.AiUsageRecord;
import com.ieltsstudio.entity.UserAiSettings;
import com.ieltsstudio.mapper.AiUsageQuotaMapper;
import com.ieltsstudio.mapper.AiUsageRecordMapper;
import com.ieltsstudio.mapper.UserAiSettingsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AiUsageQueryService} 单元测试（Phase 6B-1）。
 *
 * <p>不连接真实数据库：三个 Mapper 用 Mockito mock。
 * 验证查询接口只读、不创建 quota、正确返回额度与最近记录。</p>
 */
class AiUsageQueryServiceTest {

    private static final Long USER_ID = 1L;

    private AiUsageQuotaMapper quotaMapper;
    private AiUsageRecordMapper recordMapper;
    private UserAiSettingsMapper userAiSettingsMapper;
    private AiUsageQueryService service;

    @BeforeEach
    void setUp() {
        quotaMapper = mock(AiUsageQuotaMapper.class);
        recordMapper = mock(AiUsageRecordMapper.class);
        userAiSettingsMapper = mock(UserAiSettingsMapper.class);
        service = new AiUsageQueryService(quotaMapper, recordMapper, userAiSettingsMapper);
    }

    // ─── 1. 当前周期无 quota、无设置：返回默认视图 ─────────────────────────────

    @Test
    void shouldReturnDefaultQuotaWhenNoQuotaRow() {
        when(quotaMapper.selectOne(any())).thenReturn(null);
        when(userAiSettingsMapper.selectOne(any())).thenReturn(null);
        when(recordMapper.selectList(any())).thenReturn(List.of());

        UserAiUsageDto dto = service.queryForUser(USER_ID);

        assertEquals("BUILTIN", dto.getKeyMode());
        assertEquals(100, dto.getCreditsTotal());
        assertEquals(0, dto.getCreditsUsed());
        assertEquals(100, dto.getCreditsRemaining());
        assertTrue(dto.getBuiltinQuotaEnabled());
        assertNotNull(dto.getRecentRecords());
        assertTrue(dto.getRecentRecords().isEmpty());

        // 只读：不创建 / 不更新 quota
        verify(quotaMapper, never()).insert(any(AiUsageQuota.class));
        verify(quotaMapper, never()).update(any(), any());
    }

    // ─── 2. quota 存在：返回实际额度与剩余 ────────────────────────────────────

    @Test
    void shouldReturnExistingQuotaAndRemainingCredits() {
        AiUsageQuota quota = new AiUsageQuota();
        quota.setId(10L);
        quota.setUserId(USER_ID);
        quota.setCreditsTotal(30);
        quota.setCreditsUsed(12);
        when(quotaMapper.selectOne(any())).thenReturn(quota);
        when(userAiSettingsMapper.selectOne(any())).thenReturn(null);
        when(recordMapper.selectList(any())).thenReturn(List.of());

        UserAiUsageDto dto = service.queryForUser(USER_ID);

        assertEquals(30, dto.getCreditsTotal());
        assertEquals(12, dto.getCreditsUsed());
        assertEquals(18, dto.getCreditsRemaining());
        // periodStart 为周一 00:00，periodEnd = periodStart + 1 周
        assertEquals(dto.getPeriodStart().plusWeeks(1), dto.getPeriodEnd());
        assertEquals(java.time.DayOfWeek.MONDAY, dto.getPeriodStart().getDayOfWeek());
        assertEquals(0, dto.getPeriodStart().toLocalTime().toSecondOfDay());
        assertEquals("BUILTIN", dto.getKeyMode());
    }

    // ─── 3. USER 模式：不消耗站点额度，仍返回额度参考 ─────────────────────────

    @Test
    void shouldReturnUserModeWithoutConsumingQuota() {
        UserAiSettings settings = new UserAiSettings();
        settings.setUserId(USER_ID);
        settings.setKeyMode("USER");
        when(userAiSettingsMapper.selectOne(any())).thenReturn(settings);
        when(quotaMapper.selectOne(any())).thenReturn(null);
        when(recordMapper.selectList(any())).thenReturn(List.of());

        UserAiUsageDto dto = service.queryForUser(USER_ID);

        assertEquals("USER", dto.getKeyMode());
        assertFalse(dto.getBuiltinQuotaEnabled());
        // 站点额度仍返回默认参考值
        assertEquals(100, dto.getCreditsTotal());
        assertEquals(0, dto.getCreditsUsed());
        assertEquals(100, dto.getCreditsRemaining());
        // 只读
        verify(quotaMapper, never()).insert(any(AiUsageQuota.class));
        verify(quotaMapper, never()).update(any(), any());
    }

    // ─── 4. 最近记录：顺序保持，字段正确，无 key 字段，含 provider ─────────────

    @Test
    void shouldReturnRecentRecords() {
        AiUsageRecord r1 = newRecord("TEXT", "WRITING_GRADE", 2, "BUILTIN", "SUCCESS", null, "DEEPSEEK");
        AiUsageRecord r2 = newRecord("VISION", "EXAM_PRECISE_PARSE", 0, "BUILTIN", "FAILED", "RuntimeException: timeout", "QWEN");
        AiUsageRecord r3 = newRecord("TEXT", "AI_CHAT", 0, "USER", "REJECTED", "RATE_LIMITED", "MIMO");
        when(quotaMapper.selectOne(any())).thenReturn(null);
        when(userAiSettingsMapper.selectOne(any())).thenReturn(null);
        when(recordMapper.selectList(any())).thenReturn(List.of(r1, r2, r3));

        UserAiUsageDto dto = service.queryForUser(USER_ID);

        assertEquals(3, dto.getRecentRecords().size());
        // 顺序保持 mapper 返回顺序
        assertEquals("WRITING_GRADE", dto.getRecentRecords().get(0).getFeature());
        assertEquals("EXAM_PRECISE_PARSE", dto.getRecentRecords().get(1).getFeature());
        assertEquals("AI_CHAT", dto.getRecentRecords().get(2).getFeature());
        // 字段正确
        assertEquals(2, dto.getRecentRecords().get(0).getCost());
        assertEquals("SUCCESS", dto.getRecentRecords().get(0).getStatus());
        assertEquals("TEXT", dto.getRecentRecords().get(0).getTaskType());
        assertEquals("FAILED", dto.getRecentRecords().get(1).getStatus());
        assertEquals("REJECTED", dto.getRecentRecords().get(2).getStatus());
        assertEquals("RATE_LIMITED", dto.getRecentRecords().get(2).getErrorMessage());
        // Phase 6B-2A：provider 字段必须透传到 DTO
        assertEquals("DEEPSEEK", dto.getRecentRecords().get(0).getProvider());
        assertEquals("QWEN", dto.getRecentRecords().get(1).getProvider());
        assertEquals("MIMO", dto.getRecentRecords().get(2).getProvider());
        // AiUsageRecordDto 类不含任何 apiKey/encrypted/masked 字段（结构保证），此处不额外断言
    }

    // ─── 5. 查询绝不创建/更新 quota ──────────────────────────────────────────

    @Test
    void shouldNotCreateQuotaOnQuery() {
        when(quotaMapper.selectOne(any())).thenReturn(null);
        when(userAiSettingsMapper.selectOne(any())).thenReturn(null);
        when(recordMapper.selectList(any())).thenReturn(List.of());

        service.queryForUser(USER_ID);

        verify(quotaMapper, never()).insert(any(AiUsageQuota.class));
        verify(quotaMapper, never()).update(any(), any());
        // 也未调用 1-arg update
        verify(quotaMapper, never()).update(any());
    }

    @Test
    void queryForUserShouldRejectNullUserId() {
        assertThrows(IllegalArgumentException.class,
                () -> service.queryForUser(null));
    }

    private AiUsageRecord newRecord(String taskType, String feature, int cost,
                                    String keyMode, String status, String errorMessage,
                                    String provider) {
        AiUsageRecord r = new AiUsageRecord();
        r.setCreatedAt(LocalDateTime.now());
        r.setTaskType(taskType);
        r.setFeature(feature);
        r.setCost(cost);
        r.setKeyMode(keyMode);
        r.setProvider(provider);
        r.setStatus(status);
        r.setErrorMessage(errorMessage);
        return r;
    }
}
