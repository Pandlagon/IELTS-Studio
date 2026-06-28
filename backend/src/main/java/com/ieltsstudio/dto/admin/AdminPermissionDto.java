package com.ieltsstudio.dto.admin;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Set;

/**
 * Admin 权限视图 DTO（Phase 8C）。
 *
 * <p>用于 {@code GET /admin/permissions/users/{userId}} 与
 * {@code GET /admin/permissions/me} 返回某个 ADMIN 的有效权限集合。</p>
 *
 * <p><b>安全：</b>本 DTO 不包含 {@code password} / {@code apiKey} /
 * {@code encryptedKey} / {@code maskedKey} / {@code baseUrl} / {@code model} 等敏感字段。</p>
 *
 * <p><b>explicitMode 含义：</b>
 * <ul>
 *   <li>{@code false}：表为空，所有 ADMIN 拥有全部权限，{@code permissions} 返回全量枚举。</li>
 *   <li>{@code true}：表非空，{@code permissions} 返回该用户在表中的显式权限。</li>
 * </ul>
 */
@Getter
@Setter
@ToString
public class AdminPermissionDto {

    private Long userId;

    private String username;

    /** 用户角色（USER / ADMIN）。USER 永远没有后台权限。 */
    private String role;

    /** 是否处于显式权限模式（表非空时为 true） */
    private Boolean explicitMode;

    /** 当前用户的有效权限集合（枚举名） */
    private Set<String> permissions;
}
