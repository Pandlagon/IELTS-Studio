package com.ieltsstudio.dto.admin;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 管理端操作审计日志响应 DTO（Phase 8D）。
 *
 * <p><b>安全：</b>本 DTO 不包含 password / newPassword / apiKey / Authorization /
 * Bearer / token / JWT / encrypted key / masked key / provider body 等敏感字段。
 * {@link #summary} 由后端脱敏后写入数据库，前端直接展示服务端脱敏后的内容。</p>
 */
@Getter
@Setter
@ToString
public class AdminOperationLogDto {

    private Long id;

    /** 操作者用户 ID */
    private Long actorUserId;

    /** 操作者用户名快照 */
    private String actorUsername;

    /** 操作枚举名（{@link com.ieltsstudio.entity.AdminOperationAction}） */
    private String action;

    /** 资源类型：USER / QUOTA / PERMISSION */
    private String resourceType;

    /** 资源 ID，可为空 */
    private Long resourceId;

    /** 被操作用户 ID，可为空 */
    private Long targetUserId;

    /** 操作状态：SUCCESS / FAILED */
    private String status;

    /** 脱敏摘要（≤1000 字符） */
    private String summary;

    /** 请求 IP */
    private String ipAddress;

    /** 请求 User-Agent */
    private String userAgent;

    /** 操作时间 */
    private LocalDateTime createdAt;
}
