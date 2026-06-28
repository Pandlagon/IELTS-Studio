package com.ieltsstudio.dto.ai;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端 AI usage 最近记录 DTO（Phase 6B-2B）。
 *
 * <p>用于管理端排查最近 AI 调用。errorMessage 已由 {@code AiUsageGuard} 脱敏。
 * 不返回 API Key / encrypted key / masked key / baseUrl / model / provider 原始响应体。</p>
 */
@Data
public class AdminAiUsageRecentRecordDto {
    private LocalDateTime createdAt;
    private Long userId;
    private String taskType;
    private String feature;
    private Integer cost;
    private String keyMode;
    private String provider;
    private String status;
    private String errorMessage;
}
