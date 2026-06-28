package com.ieltsstudio.dto.ai;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端 AI usage 汇总统计响应 DTO（Phase 6B-2B）。
 *
 * <p>聚合最近 N 天的 AI usage 数据，包含：
 * <ul>
 *   <li>状态汇总：total / success / failed / rejected</li>
 *   <li>分组聚合：byStatus / byProvider / byFeature / byKeyMode / byTaskType</li>
 *   <li>每日趋势：dailyTrend（含 0 调用日期）</li>
 *   <li>总 cost 与去重用户数</li>
 * </ul>
 *
 * <p>不返回 API Key / encrypted key / masked key / baseUrl / model。</p>
 */
@Data
public class AdminAiUsageSummaryDto {
    /** 统计天数（已 clamp 到 1~90） */
    private Integer days;
    /** 统计起始时间（含） */
    private LocalDateTime from;
    /** 统计结束时间（含） */
    private LocalDateTime to;

    private Long totalCalls;
    private Long successCalls;
    private Long failedCalls;
    private Long rejectedCalls;

    private Long totalCost;
    private Long uniqueUsers;

    private List<AdminAiUsageBucketDto> byStatus;
    private List<AdminAiUsageBucketDto> byProvider;
    private List<AdminAiUsageBucketDto> byFeature;
    private List<AdminAiUsageBucketDto> byKeyMode;
    private List<AdminAiUsageBucketDto> byTaskType;
    private List<AdminAiUsageBucketDto> dailyTrend;
}
