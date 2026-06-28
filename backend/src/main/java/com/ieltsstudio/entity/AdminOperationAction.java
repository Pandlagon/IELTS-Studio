package com.ieltsstudio.entity;

/**
 * 管理端高风险写操作的 action 枚举（Phase 8D 审计日志）。
 *
 * <p>本枚举仅覆盖管理端写操作；普通列表/详情查询不记录审计日志。
 * 每个枚举值对应一类高风险 Admin 操作，写入 {@code admin_operation_logs.action} 字段。</p>
 *
 * <p><b>resourceType 约定：</b>
 * <ul>
 *   <li>{@code USER_*} → resourceType = {@code USER}</li>
 *   <li>{@code QUOTA_*} → resourceType = {@code QUOTA}</li>
 *   <li>{@code PERMISSION_*} → resourceType = {@code PERMISSION}</li>
 * </ul>
 * </p>
 */
public enum AdminOperationAction {

    /** 创建用户 */
    USER_CREATE,

    /** 修改用户角色 */
    USER_UPDATE_ROLE,

    /** 禁用用户（deleted=1） */
    USER_DISABLE,

    /** 启用用户（deleted=0） */
    USER_ENABLE,

    /** 重置用户密码（不记录密码内容） */
    USER_RESET_PASSWORD,

    /** 设置当前周期 creditsTotal */
    QUOTA_SET_TOTAL,

    /** 给当前周期增加 creditsTotal */
    QUOTA_GRANT,

    /** 重置当前周期 creditsUsed=0 */
    QUOTA_RESET_USED,

    /** 更新某 ADMIN 的权限集合 */
    PERMISSION_UPDATE
}
