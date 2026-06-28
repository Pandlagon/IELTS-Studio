package com.ieltsstudio.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ieltsstudio.dto.ai.AdminAiUsageBucketDto;
import com.ieltsstudio.dto.ai.AdminAiUsageRecentRecordDto;
import com.ieltsstudio.dto.ai.AdminAiUsageSummaryDto;
import com.ieltsstudio.entity.AiUsageRecord;
import com.ieltsstudio.mapper.AiUsageRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端 AI usage 统计服务（Phase 6B-2B）。
 *
 * <p>只读服务：不 insert / update / delete 任何数据。
 * 查询时间范围内全部 records 后在 Java 内 stream 聚合，避免写复杂 SQL。
 * 本阶段面向管理端低频统计，{@code days} 最大 90 天。</p>
 *
 * <p>安全约定：
 * <ul>
 *   <li>不返回 API Key / encrypted key / masked key / baseUrl / model。</li>
 *   <li>errorMessage 已由 {@code AiUsageGuard} 在写入时脱敏。</li>
 *   <li>权限校验由 Controller 层负责（{@code requireAdmin(AuthUser)}）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAiUsageStatsService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String UNKNOWN = "UNKNOWN";

    /** days 参数上下限 */
    public static final int MIN_DAYS = 1;
    public static final int MAX_DAYS = 90;
    /** recent limit 参数上下限 */
    public static final int MIN_LIMIT = 1;
    public static final int MAX_LIMIT = 100;

    private final AiUsageRecordMapper recordMapper;

    /**
     * 汇总最近 {@code days} 天的 AI usage 统计。
     *
     * @param days 统计天数，将被 clamp 到 {@code [MIN_DAYS, MAX_DAYS]}
     */
    public AdminAiUsageSummaryDto summary(int days) {
        int clamped = clamp(days, MIN_DAYS, MAX_DAYS);
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusDays(clamped);

        List<AiUsageRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<AiUsageRecord>()
                        .ge(AiUsageRecord::getCreatedAt, from)
                        .le(AiUsageRecord::getCreatedAt, to));

        AdminAiUsageSummaryDto dto = new AdminAiUsageSummaryDto();
        dto.setDays(clamped);
        dto.setFrom(from);
        dto.setTo(to);
        dto.setTotalCalls((long) records.size());
        dto.setSuccessCalls(records.stream().filter(r -> "SUCCESS".equals(r.getStatus())).count());
        dto.setFailedCalls(records.stream().filter(r -> "FAILED".equals(r.getStatus())).count());
        dto.setRejectedCalls(records.stream().filter(r -> "REJECTED".equals(r.getStatus())).count());
        dto.setTotalCost(records.stream().mapToLong(r -> r.getCost() == null ? 0L : r.getCost()).sum());
        dto.setUniqueUsers(records.stream().map(AiUsageRecord::getUserId).distinct().count());

        dto.setByStatus(groupBy(records, AiUsageRecord::getStatus));
        dto.setByProvider(groupBy(records, r -> nullIfBlank(r.getProvider())));
        dto.setByFeature(groupBy(records, r -> nullIfBlank(r.getFeature())));
        dto.setByKeyMode(groupBy(records, r -> nullIfBlank(r.getKeyMode())));
        dto.setByTaskType(groupBy(records, r -> nullIfBlank(r.getTaskType())));
        dto.setDailyTrend(buildDailyTrend(records, from.toLocalDate(), to.toLocalDate()));
        return dto;
    }

    /**
     * 查询最近 {@code limit} 条 AI usage records，按 createdAt 倒序。
     *
     * @param limit 返回数量，将被 clamp 到 {@code [MIN_LIMIT, MAX_LIMIT]}
     */
    public List<AdminAiUsageRecentRecordDto> recent(int limit) {
        int clamped = clamp(limit, MIN_LIMIT, MAX_LIMIT);
        List<AiUsageRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<AiUsageRecord>()
                        .orderByDesc(AiUsageRecord::getCreatedAt)
                        .last("LIMIT " + clamped));
        return records.stream().map(this::toRecentDto).collect(Collectors.toList());
    }

    // ─── 内部辅助 ───────────────────────────────────────────────────────────────

    private List<AdminAiUsageBucketDto> groupBy(List<AiUsageRecord> records,
                                                 Function<AiUsageRecord, String> keyExtractor) {
        // 使用 LinkedHashMap 保留首次出现顺序；null key 归入 UNKNOWN
        Map<String, long[]> agg = new LinkedHashMap<>();
        for (AiUsageRecord r : records) {
            String key = keyExtractor.apply(r);
            String name = key == null ? UNKNOWN : key;
            long[] entry = agg.computeIfAbsent(name, k -> new long[]{0L, 0L});
            entry[0] += 1;
            entry[1] += r.getCost() == null ? 0L : r.getCost();
        }
        List<AdminAiUsageBucketDto> buckets = new ArrayList<>(agg.size());
        agg.forEach((name, entry) ->
                buckets.add(new AdminAiUsageBucketDto(name, entry[0], entry[1])));
        return buckets;
    }

    private List<AdminAiUsageBucketDto> buildDailyTrend(List<AiUsageRecord> records,
                                                         LocalDate from, LocalDate to) {
        // 初始化所有日期为 0，确保缺失日期也返回
        Map<String, long[]> agg = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            agg.put(d.format(DATE_FMT), new long[]{0L, 0L});
        }
        for (AiUsageRecord r : records) {
            if (r.getCreatedAt() == null) continue;
            String day = r.getCreatedAt().toLocalDate().format(DATE_FMT);
            long[] entry = agg.get(day);
            if (entry == null) continue; // 超出范围（理论上不会发生）
            entry[0] += 1;
            entry[1] += r.getCost() == null ? 0L : r.getCost();
        }
        List<AdminAiUsageBucketDto> trend = new ArrayList<>(agg.size());
        agg.forEach((day, entry) ->
                trend.add(new AdminAiUsageBucketDto(day, entry[0], entry[1])));
        return trend;
    }

    private AdminAiUsageRecentRecordDto toRecentDto(AiUsageRecord r) {
        AdminAiUsageRecentRecordDto d = new AdminAiUsageRecentRecordDto();
        d.setCreatedAt(r.getCreatedAt());
        d.setUserId(r.getUserId());
        d.setTaskType(r.getTaskType());
        d.setFeature(r.getFeature());
        d.setCost(r.getCost());
        d.setKeyMode(r.getKeyMode());
        d.setProvider(r.getProvider());
        d.setStatus(r.getStatus());
        d.setErrorMessage(r.getErrorMessage());
        return d;
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
