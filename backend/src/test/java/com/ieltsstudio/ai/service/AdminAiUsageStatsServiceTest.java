package com.ieltsstudio.ai.service;

import com.ieltsstudio.dto.ai.AdminAiUsageBucketDto;
import com.ieltsstudio.dto.ai.AdminAiUsageRecentRecordDto;
import com.ieltsstudio.dto.ai.AdminAiUsageSummaryDto;
import com.ieltsstudio.entity.AiUsageRecord;
import com.ieltsstudio.mapper.AiUsageRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdminAiUsageStatsService} 单元测试（Phase 6B-2B）。
 *
 * <p>不连接真实数据库：{@link AiUsageRecordMapper} 用 Mockito mock。
 * 验证只读统计聚合、参数 clamp、dailyTrend 补 0、null bucket 归 UNKNOWN、顺序保持。</p>
 */
class AdminAiUsageStatsServiceTest {

    private AiUsageRecordMapper recordMapper;
    private AdminAiUsageStatsService service;

    @BeforeEach
    void setUp() {
        recordMapper = mock(AiUsageRecordMapper.class);
        service = new AdminAiUsageStatsService(recordMapper);
    }

    // ─── 1. 多维度聚合 ─────────────────────────────────────────────────────────

    @Test
    void summaryShouldAggregateByStatusProviderFeatureKeyModeTaskType() {
        // 构造覆盖各维度的 records（均落在最近 7 天内）
        LocalDateTime now = LocalDateTime.now();
        List<AiUsageRecord> records = new ArrayList<>();
        records.add(newRecord(now, 1L, "TEXT", "WRITING_GRADE", 2, "BUILTIN", "DEEPSEEK", "SUCCESS", null));
        records.add(newRecord(now, 1L, "TEXT", "AI_CHAT", 1, "USER", "DEEPSEEK", "SUCCESS", null));
        records.add(newRecord(now, 2L, "VISION", "EXAM_PRECISE_PARSE", 10, "BUILTIN", "QWEN", "FAILED", "RuntimeException: timeout"));
        records.add(newRecord(now, 2L, "TEXT", "TRANSLATE", 0, "USER", "MIMO", "REJECTED", "RATE_LIMITED"));
        records.add(newRecord(now, 3L, "TEXT", "AI_CHAT", 0, "USER", "OPENAI_COMPATIBLE", "REJECTED", "RATE_LIMITED"));
        when(recordMapper.selectList(any())).thenReturn(records);

        AdminAiUsageSummaryDto dto = service.summary(7);

        // 基础汇总
        assertEquals(5, dto.getTotalCalls());
        assertEquals(2, dto.getSuccessCalls());
        assertEquals(1, dto.getFailedCalls());
        assertEquals(2, dto.getRejectedCalls());
        assertEquals(13L, dto.getTotalCost()); // 2+1+10+0+0
        assertEquals(3L, dto.getUniqueUsers()); // 1, 2, 3
        assertEquals(7, dto.getDays());

        // byStatus
        AdminAiUsageBucketDto successBucket = findBucket(dto.getByStatus(), "SUCCESS");
        assertNotNull(successBucket);
        assertEquals(2L, successBucket.getCount());
        assertEquals(3L, successBucket.getCost()); // 2+1
        AdminAiUsageBucketDto failedBucket = findBucket(dto.getByStatus(), "FAILED");
        assertEquals(1L, failedBucket.getCount());
        assertEquals(10L, failedBucket.getCost());
        AdminAiUsageBucketDto rejectedBucket = findBucket(dto.getByStatus(), "REJECTED");
        assertEquals(2L, rejectedBucket.getCount());
        assertEquals(0L, rejectedBucket.getCost());

        // byProvider
        assertEquals(2L, findBucket(dto.getByProvider(), "DEEPSEEK").getCount());
        assertEquals(1L, findBucket(dto.getByProvider(), "QWEN").getCount());
        assertEquals(1L, findBucket(dto.getByProvider(), "MIMO").getCount());
        assertEquals(1L, findBucket(dto.getByProvider(), "OPENAI_COMPATIBLE").getCount());

        // byFeature
        assertEquals(2L, findBucket(dto.getByFeature(), "AI_CHAT").getCount());
        assertEquals(1L, findBucket(dto.getByFeature(), "WRITING_GRADE").getCount());
        assertEquals(1L, findBucket(dto.getByFeature(), "EXAM_PRECISE_PARSE").getCount());
        assertEquals(1L, findBucket(dto.getByFeature(), "TRANSLATE").getCount());

        // byKeyMode
        assertEquals(2L, findBucket(dto.getByKeyMode(), "BUILTIN").getCount());
        assertEquals(3L, findBucket(dto.getByKeyMode(), "USER").getCount());

        // byTaskType
        assertEquals(4L, findBucket(dto.getByTaskType(), "TEXT").getCount());
        assertEquals(1L, findBucket(dto.getByTaskType(), "VISION").getCount());

        // dailyTrend 至少包含今天
        assertNotNull(dto.getDailyTrend());
        assertTrue(dto.getDailyTrend().size() >= 7);
        assertTrue(dto.getDailyTrend().stream()
                .anyMatch(b -> now.toLocalDate().toString().equals(b.getName())));
    }

    // ─── 2. days 参数 clamp ─────────────────────────────────────────────────────

    @Test
    void summaryShouldClampDays() {
        when(recordMapper.selectList(any())).thenReturn(List.of());

        AdminAiUsageSummaryDto d0 = service.summary(0);
        assertEquals(AdminAiUsageStatsService.MIN_DAYS, d0.getDays(),
                "days=0 应 clamp 到 MIN_DAYS");

        AdminAiUsageSummaryDto d999 = service.summary(999);
        assertEquals(AdminAiUsageStatsService.MAX_DAYS, d999.getDays(),
                "days=999 应 clamp 到 MAX_DAYS");
    }

    // ─── 3. dailyTrend 补 0 ─────────────────────────────────────────────────────

    @Test
    void summaryShouldFillDailyTrendWithZeroDays() {
        // 构造只有今天和昨天有 records 的数据
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime yesterday = now.minusDays(1);
        List<AiUsageRecord> records = List.of(
                newRecord(now, 1L, "TEXT", "AI_CHAT", 1, "BUILTIN", "DEEPSEEK", "SUCCESS", null),
                newRecord(yesterday, 2L, "TEXT", "AI_CHAT", 2, "BUILTIN", "DEEPSEEK", "SUCCESS", null));
        when(recordMapper.selectList(any())).thenReturn(records);

        AdminAiUsageSummaryDto dto = service.summary(7);

        // 7 天范围内 dailyTrend 应有 8 个日期（from=now-7d, to=now, 含两端）
        // 注意：to.minusDays(7) 到 to 之间包含 8 天
        assertTrue(dto.getDailyTrend().size() >= 8,
                "dailyTrend 应覆盖完整日期范围，实际=" + dto.getDailyTrend().size());
        // 今天与昨天有数据
        AdminAiUsageBucketDto todayBucket = findBucket(dto.getDailyTrend(), now.toLocalDate().toString());
        assertNotNull(todayBucket);
        assertEquals(1L, todayBucket.getCount());
        assertEquals(1L, todayBucket.getCost());
        AdminAiUsageBucketDto yBucket = findBucket(dto.getDailyTrend(), yesterday.toLocalDate().toString());
        assertNotNull(yBucket);
        assertEquals(1L, yBucket.getCount());
        assertEquals(2L, yBucket.getCost());
        // 中间日期（前天）应为 0
        AdminAiUsageBucketDto dayBeforeYesterday = findBucket(
                dto.getDailyTrend(), now.minusDays(2).toLocalDate().toString());
        assertNotNull(dayBeforeYesterday);
        assertEquals(0L, dayBeforeYesterday.getCount(), "缺失日期 count 应为 0");
        assertEquals(0L, dayBeforeYesterday.getCost(), "缺失日期 cost 应为 0");
    }

    // ─── 4. recent clamp 与顺序保持 ─────────────────────────────────────────────

    @Test
    void recentShouldClampLimitAndKeepOrder() {
        // 构造 3 条 records，模拟 mapper 倒序返回
        LocalDateTime now = LocalDateTime.now();
        List<AiUsageRecord> records = List.of(
                newRecord(now, 1L, "TEXT", "AI_CHAT", 0, "USER", "DEEPSEEK", "SUCCESS", null),
                newRecord(now.minusMinutes(1), 2L, "TEXT", "AI_CHAT", 0, "USER", "DEEPSEEK", "SUCCESS", null),
                newRecord(now.minusMinutes(2), 3L, "TEXT", "AI_CHAT", 0, "USER", "DEEPSEEK", "SUCCESS", null));
        when(recordMapper.selectList(any())).thenReturn(records);

        // limit=999 应 clamp 到 MAX_LIMIT=100；mock 返回 3 条用于验证顺序
        List<AdminAiUsageRecentRecordDto> result = service.recent(999);
        assertEquals(3, result.size());
        // 顺序保持 mapper 返回顺序
        assertEquals(1L, result.get(0).getUserId());
        assertEquals(2L, result.get(1).getUserId());
        assertEquals(3L, result.get(2).getUserId());
        // service 确实调用了 mapper.selectList 一次（验证 clamp 后走查询，不抛异常）
        verify(recordMapper, times(1)).selectList(any());

        // limit=0 应 clamp 到 MIN_LIMIT=1；mock 返回 1 条
        when(recordMapper.selectList(any())).thenReturn(records.subList(0, 1));
        List<AdminAiUsageRecentRecordDto> r0 = service.recent(0);
        assertEquals(1, r0.size());
    }

    // ─── 5. null bucket 归 UNKNOWN ──────────────────────────────────────────────

    @Test
    void summaryShouldTreatNullBucketAsUnknown() {
        LocalDateTime now = LocalDateTime.now();
        List<AiUsageRecord> records = List.of(
                // 全部字段为 null
                newRecord(now, 1L, null, null, 0, null, null, "SUCCESS", null),
                newRecord(now, 2L, "TEXT", "AI_CHAT", 1, "BUILTIN", "DEEPSEEK", "SUCCESS", null));
        when(recordMapper.selectList(any())).thenReturn(records);

        AdminAiUsageSummaryDto dto = service.summary(7);

        // 各分组都应包含 UNKNOWN bucket
        assertNotNull(findBucket(dto.getByProvider(), "UNKNOWN"), "null provider 应归入 UNKNOWN");
        assertNotNull(findBucket(dto.getByFeature(), "UNKNOWN"), "null feature 应归入 UNKNOWN");
        assertNotNull(findBucket(dto.getByKeyMode(), "UNKNOWN"), "null keyMode 应归入 UNKNOWN");
        assertNotNull(findBucket(dto.getByTaskType(), "UNKNOWN"), "null taskType 应归入 UNKNOWN");

        // UNKNOWN bucket 数量正确
        assertEquals(1L, findBucket(dto.getByProvider(), "UNKNOWN").getCount());
        assertEquals(1L, findBucket(dto.getByFeature(), "UNKNOWN").getCount());
        assertEquals(1L, findBucket(dto.getByKeyMode(), "UNKNOWN").getCount());
        assertEquals(1L, findBucket(dto.getByTaskType(), "UNKNOWN").getCount());
    }

    // ─── 辅助 ───────────────────────────────────────────────────────────────────

    private AiUsageRecord newRecord(LocalDateTime createdAt, Long userId, String taskType,
                                     String feature, int cost, String keyMode, String provider,
                                     String status, String errorMessage) {
        AiUsageRecord r = new AiUsageRecord();
        r.setCreatedAt(createdAt);
        r.setUserId(userId);
        r.setTaskType(taskType);
        r.setFeature(feature);
        r.setCost(cost);
        r.setKeyMode(keyMode);
        r.setProvider(provider);
        r.setStatus(status);
        r.setErrorMessage(errorMessage);
        return r;
    }

    private AdminAiUsageBucketDto findBucket(List<AdminAiUsageBucketDto> buckets, String name) {
        return buckets.stream().filter(b -> name.equals(b.getName())).findFirst().orElse(null);
    }
}
