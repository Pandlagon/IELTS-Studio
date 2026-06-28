package com.ieltsstudio.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理端 AI usage 分组统计 bucket（Phase 6B-2B）。
 *
 * <p>用于 byStatus / byProvider / byFeature / byKeyMode / byTaskType / dailyTrend。
 * dailyTrend 时 name = yyyy-MM-dd。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminAiUsageBucketDto {
    /** 分组名（状态枚举名 / provider 枚举名 / feature 枚举名 / keyMode / taskType / 日期字符串） */
    private String name;
    /** 该分组的调用次数 */
    private Long count;
    /** 该分组的 cost 总和 */
    private Long cost;
}
