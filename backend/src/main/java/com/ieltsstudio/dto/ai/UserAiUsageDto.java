package com.ieltsstudio.dto.ai;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 当前用户 AI 用量视图（只读）。
 *
 * <p>用于用户中心展示当前周期 credits、keyMode 与最近使用记录。
 * 不包含任何 API Key / encrypted / masked 字段。</p>
 */
@Data
public class UserAiUsageDto {

    /** Key 模式：BUILTIN / USER */
    private String keyMode;

    /** 当前自然周开始（周一 00:00:00） */
    private LocalDateTime periodStart;

    /** 当前自然周结束（下周一 00:00:00） */
    private LocalDateTime periodEnd;

    /** 周期总额度（默认 100） */
    private Integer creditsTotal;

    /** 周期已用额度 */
    private Integer creditsUsed;

    /** 周期剩余额度（creditsTotal - creditsUsed，下限 0） */
    private Integer creditsRemaining;

    /** 是否为内置 Key 模式（keyMode == BUILTIN） */
    private Boolean builtinQuotaEnabled;

    /** 最近若干条使用记录（最多 20 条，按 createdAt 倒序） */
    private List<AiUsageRecordDto> recentRecords;
}
