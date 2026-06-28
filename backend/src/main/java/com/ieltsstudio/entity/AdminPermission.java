package com.ieltsstudio.entity;

/**
 * Admin 后台细粒度权限枚举（Phase 8C）。
 *
 * <p>本阶段保留 USER / ADMIN 两级基础角色不变，仅在 ADMIN 内部做权限细分。
 * 普通用户永远没有后台权限。</p>
 *
 * <p><b>兼容模式：</b>当 {@code admin_user_permissions} 表为空时，
 * 所有 ADMIN 视为拥有全部权限；表非空时进入显式权限模式。</p>
 *
 * <p><b>最高风险权限：</b>{@link #ADMIN_PERMISSIONS_MANAGE} 控制权限管理能力，
 * 必须重点保护（不能移除自己的、不能移除系统中最后一个）。</p>
 */
public enum AdminPermission {

    /** 查看用户列表 / 用户详情 */
    ADMIN_USERS_VIEW,

    /** 创建用户 / 修改角色 / 禁用 / 启用 */
    ADMIN_USERS_MANAGE,

    /** 重置用户密码 */
    ADMIN_USERS_RESET_PASSWORD,

    /** 查看 AI usage 统计后台 */
    ADMIN_AI_USAGE_VIEW,

    /** 查看用户 quota */
    ADMIN_QUOTA_VIEW,

    /** 调整用户 quota（setTotal / grant / resetUsed） */
    ADMIN_QUOTA_MANAGE,

    /** 分配 / 修改其他 ADMIN 的 permissions（最高风险权限） */
    ADMIN_PERMISSIONS_MANAGE;

    /**
     * 判断字符串是否为合法的 {@link AdminPermission} 枚举名。
     *
     * @param value 待校验的字符串
     * @return true=合法权限值；false=非法或 null
     */
    public static boolean isValid(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (AdminPermission p : values()) {
            if (p.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
