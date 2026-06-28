package com.ieltsstudio.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ieltsstudio.dto.ai.AiUsageRecordDto;
import com.ieltsstudio.dto.ai.UserAiUsageDto;
import com.ieltsstudio.entity.AiUsageQuota;
import com.ieltsstudio.entity.AiUsageRecord;
import com.ieltsstudio.entity.UserAiSettings;
import com.ieltsstudio.mapper.AiUsageQuotaMapper;
import com.ieltsstudio.mapper.AiUsageRecordMapper;
import com.ieltsstudio.mapper.UserAiSettingsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;

/**
 * AI 用量查询服务（只读）。
 *
 * <p>用于用户中心展示当前周期 credits 与最近使用记录。</p>
 *
 * <p><b>只读语义：</b>查询不会创建 quota 行，也不会扣费。若当前周期 quota 不存在，
 * 返回默认视图（creditsTotal=30, creditsUsed=0, creditsRemaining=30）。</p>
 *
 * <p>不调用 {@code AiUsageGuard.getOrCreateCurrentQuota(...)}，避免用户仅打开页面就
 * 触发 quota 落库。{@code getOrCreateCurrentQuota} 是扣费路径专用。</p>
 *
 * <p>周期规则与 {@code AiUsageGuard} 保持一致：自然周（周一 00:00 ~ 下周一 00:00）。
 * 后续 Phase 6B-2 可将周期 helper 抽到公共 util。</p>
 */
@Service
@RequiredArgsConstructor
public class AiUsageQueryService {

    private static final int DEFAULT_WEEKLY_CREDITS = 30;
    private static final int RECENT_RECORD_LIMIT = 20;
    private static final String MODE_BUILTIN = "BUILTIN";

    private final AiUsageQuotaMapper quotaMapper;
    private final AiUsageRecordMapper recordMapper;
    private final UserAiSettingsMapper userAiSettingsMapper;

    /**
     * 查询当前用户的 AI 用量视图。
     *
     * @param userId 当前登录用户 ID（来自 AuthUser）
     * @return 只读用量 DTO（含默认视图，永不返回 null）
     */
    public UserAiUsageDto queryForUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        Period period = currentPeriod();
        String keyMode = resolveKeyMode(userId);

        AiUsageQuota quota = quotaMapper.selectOne(
                new LambdaQueryWrapper<AiUsageQuota>()
                        .eq(AiUsageQuota::getUserId, userId)
                        .eq(AiUsageQuota::getPeriodStart, period.start()));

        UserAiUsageDto dto = new UserAiUsageDto();
        dto.setKeyMode(keyMode);
        dto.setPeriodStart(period.start());
        dto.setPeriodEnd(period.end());
        dto.setBuiltinQuotaEnabled(MODE_BUILTIN.equals(keyMode));

        if (quota != null) {
            dto.setCreditsTotal(quota.getCreditsTotal());
            dto.setCreditsUsed(quota.getCreditsUsed());
            dto.setCreditsRemaining(safeRemaining(quota));
        } else {
            // 当前周期无 quota 行：返回默认视图，不 insert
            dto.setCreditsTotal(DEFAULT_WEEKLY_CREDITS);
            dto.setCreditsUsed(0);
            dto.setCreditsRemaining(DEFAULT_WEEKLY_CREDITS);
        }

        dto.setRecentRecords(loadRecentRecords(userId));
        return dto;
    }

    /** 解析用户当前 keyMode；无设置或为空视为 BUILTIN（与扣费路径回退逻辑一致） */
    private String resolveKeyMode(Long userId) {
        UserAiSettings settings = userAiSettingsMapper.selectOne(
                new LambdaQueryWrapper<UserAiSettings>()
                        .eq(UserAiSettings::getUserId, userId));
        if (settings == null || settings.getKeyMode() == null || settings.getKeyMode().isBlank()) {
            return MODE_BUILTIN;
        }
        return settings.getKeyMode();
    }

    /** 查询最近 {@link #RECENT_RECORD_LIMIT} 条 usage records，按 createdAt 倒序 */
    private List<AiUsageRecordDto> loadRecentRecords(Long userId) {
        List<AiUsageRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<AiUsageRecord>()
                        .eq(AiUsageRecord::getUserId, userId)
                        .orderByDesc(AiUsageRecord::getCreatedAt)
                        .last("LIMIT " + RECENT_RECORD_LIMIT));
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        return records.stream().map(this::toDto).toList();
    }

    private AiUsageRecordDto toDto(AiUsageRecord r) {
        AiUsageRecordDto d = new AiUsageRecordDto();
        d.setCreatedAt(r.getCreatedAt());
        d.setTaskType(r.getTaskType());
        d.setFeature(r.getFeature());
        d.setCost(r.getCost());
        d.setKeyMode(r.getKeyMode());
        d.setStatus(r.getStatus());
        d.setErrorMessage(r.getErrorMessage());
        return d;
    }

    /** 计算剩余额度，下限 0，兼容 null 字段 */
    private Integer safeRemaining(AiUsageQuota quota) {
        int total = quota.getCreditsTotal() == null ? 0 : quota.getCreditsTotal();
        int used = quota.getCreditsUsed() == null ? 0 : quota.getCreditsUsed();
        return Math.max(0, total - used);
    }

    /** 当前自然周：周一 00:00:00 到下周一 00:00:00（基于服务器默认时区） */
    private Period currentPeriod() {
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDateTime start = monday.atStartOfDay();
        LocalDateTime end = start.plusWeeks(1);
        return new Period(start, end);
    }

    private record Period(LocalDateTime start, LocalDateTime end) {}
}
