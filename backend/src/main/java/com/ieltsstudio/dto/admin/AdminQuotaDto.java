package com.ieltsstudio.dto.admin;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 管理端用户当前周期 AI 额度视图（Phase 8B）。
 *
 * <p><b>安全：</b>本 DTO 不包含 {@code password} / {@code apiKey} / {@code encryptedKey} /
 * {@code maskedKey} / {@code baseUrl} / {@code model} 等敏感字段。
 * 当 {@link #quotaRowExists} 为 {@code false} 时，{@code credits*} 字段返回虚拟默认视图
 * （30/0/30），表示当前周期尚无 quota 行。</p>
 */
@Getter
@Setter
@ToString
public class AdminQuotaDto {

    private Long userId;
    private String username;
    private String email;
    private String role;
    private Integer deleted;

    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private Integer creditsTotal;
    private Integer creditsUsed;
    private Integer creditsRemaining;

    /** true=数据库存在当前周期 quota 行；false=虚拟默认视图（无 quota 行） */
    private Boolean quotaRowExists;
}
