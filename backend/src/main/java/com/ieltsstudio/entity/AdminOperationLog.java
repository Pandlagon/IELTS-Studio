package com.ieltsstudio.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 管理端操作审计日志实体，对应 {@code admin_operation_logs} 表（Phase 8D）。
 *
 * <p>记录高风险 Admin 写操作的执行情况，便于后续追溯。</p>
 *
 * <p><b>安全：</b>{@link #summary} 字段必须经过脱敏处理，禁止包含
 * password / API Key / token / Authorization / Bearer / provider body 等敏感信息。
 * {@link #toString()} 输出 summary 时已是脱敏后内容，可安全日志输出。</p>
 *
 * <p>无 {@code @TableLogic} 软删除字段，审计日志不允许删除。</p>
 */
@Getter
@Setter
@ToString
@TableName("admin_operation_logs")
public class AdminOperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作者用户 ID */
    private Long actorUserId;

    /** 操作者用户名快照（便于用户改名后仍能审计） */
    private String actorUsername;

    /** 操作枚举名，对应 {@link AdminOperationAction} */
    private String action;

    /** 资源类型：USER / QUOTA / PERMISSION */
    private String resourceType;

    /** 资源 ID，可为空 */
    private Long resourceId;

    /** 被操作用户 ID，可为空 */
    private Long targetUserId;

    /** 操作状态：SUCCESS / FAILED，对应 {@link AdminOperationStatus} */
    private String status;

    /** 脱敏摘要（≤1000 字符） */
    private String summary;

    /** 请求 IP */
    private String ipAddress;

    /** 请求 User-Agent */
    private String userAgent;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
