package com.ieltsstudio.dto.admin;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Set;

/**
 * 更新某个 ADMIN 用户权限的请求体（Phase 8C）。
 *
 * <p>对应 {@code PUT /admin/permissions/users/{userId}}。
 * 后端会按 {@link com.ieltsstudio.entity.AdminPermission} 枚举白名单校验每个值，
 * 并执行以下安全规则：
 * <ul>
 *   <li>target 必须是 ADMIN。</li>
 *   <li>不能移除自己的 {@code ADMIN_PERMISSIONS_MANAGE}。</li>
 *   <li>不能移除系统中最后一个 {@code ADMIN_PERMISSIONS_MANAGE}。</li>
 *   <li>从兼容模式进入显式模式后必须至少保留一个 {@code ADMIN_PERMISSIONS_MANAGE}。</li>
 * </ul>
 */
@Getter
@Setter
@ToString
public class AdminUpdatePermissionsRequest {

    /** 权限集合（{@link com.ieltsstudio.entity.AdminPermission} 枚举名）；允许空集合表示移除全部权限 */
    @NotNull(message = "permissions 不能为 null（空集合请传 []）")
    private Set<String> permissions;
}
