package com.ieltsstudio.controller;

import com.ieltsstudio.common.Result;
import com.ieltsstudio.dto.admin.AdminPermissionDto;
import com.ieltsstudio.dto.admin.AdminUpdatePermissionsRequest;
import com.ieltsstudio.entity.AdminOperationAction;
import com.ieltsstudio.entity.AdminPermission;
import com.ieltsstudio.security.AuthUser;
import com.ieltsstudio.service.AdminAuditLogService;
import com.ieltsstudio.service.AdminPermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端 Admin 权限管理接口（Phase 8C）。
 *
 * <p>路径前缀：{@code /admin/permissions}（叠加 context-path {@code /api} 后为
 * {@code /api/admin/permissions}）。</p>
 *
 * <p><b>鉴权：</b>所有接口必须 ADMIN 角色访问。
 * <ul>
 *   <li>Spring Security 已对 {@code /admin/**} 配置 {@code hasRole("ADMIN")}。</li>
 *   <li>Controller 内 {@link #requireAdmin(AuthUser, AdminPermission)} 做防御性二次校验
 *       + 精细权限校验（要求 {@link AdminPermission#ADMIN_PERMISSIONS_MANAGE}）。</li>
 *   <li>userId 一律从 {@code @AuthenticationPrincipal AuthUser} 取。</li>
 * </ul>
 *
 * <p><b>兼容模式：</b>当 {@code admin_user_permissions} 表为空时，所有 ADMIN 拥有全部权限，
 * 此时任何 ADMIN 都能访问本接口；一旦表非空进入显式模式，只有持有
 * {@link AdminPermission#ADMIN_PERMISSIONS_MANAGE} 的 ADMIN 才能访问。</p>
 *
 * <p><b>安全：</b>返回的 DTO 不含 password / apiKey / encrypted / masked / baseUrl / model。
 * 不修改 USER/ADMIN 基础角色、AI Provider 调用链、quota 扣费、rate limit。</p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/permissions")
@RequiredArgsConstructor
public class AdminPermissionController {

    private final AdminPermissionService adminPermissionService;
    private final AdminAuditLogService adminAuditLogService;

    /**
     * 列出所有合法权限枚举名。
     *
     * <p>需要 {@link AdminPermission#ADMIN_PERMISSIONS_MANAGE} 权限。</p>
     */
    @GetMapping
    public Result<List<String>> listAll(@AuthenticationPrincipal AuthUser authUser) {
        requireAdmin(authUser, AdminPermission.ADMIN_PERMISSIONS_MANAGE);
        return Result.success(adminPermissionService.listAllPermissions());
    }

    /**
     * 查询当前 ADMIN 自身的有效权限（供前端 NavBar 显示/隐藏菜单）。
     *
     * <p>所有 ADMIN 均可访问（无需任何具体权限）——这是为了让被限制了权限的 ADMIN
     * 也能看到自己的权限列表。兼容模式下返回全部权限。</p>
     */
    @GetMapping("/me")
    public Result<AdminPermissionDto> myPermissions(@AuthenticationPrincipal AuthUser authUser) {
        requireAdmin(authUser);
        return Result.success(adminPermissionService.buildPermissionDto(authUser.getId()));
    }

    /**
     * 查询某个用户的有效权限。
     *
     * <p>需要 {@link AdminPermission#ADMIN_PERMISSIONS_MANAGE} 权限。</p>
     *
     * @param userId 被查询用户 ID
     */
    @GetMapping("/users/{userId}")
    public Result<AdminPermissionDto> getUserPermissions(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long userId) {
        requireAdmin(authUser, AdminPermission.ADMIN_PERMISSIONS_MANAGE);
        return Result.success(adminPermissionService.buildPermissionDto(userId));
    }

    /**
     * 更新某个 ADMIN 用户的权限集合（先删后插）。
     *
     * <p>需要 {@link AdminPermission#ADMIN_PERMISSIONS_MANAGE} 权限。
     * 安全规则由 {@link AdminPermissionService#updateUserPermissions} 强制执行。</p>
     *
     * @param userId   被更新用户 ID
     * @param request  新权限集合
     */
    @PutMapping("/users/{userId}")
    public Result<AdminPermissionDto> updateUserPermissions(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long userId,
            @Valid @RequestBody AdminUpdatePermissionsRequest request,
            HttpServletRequest httpRequest) {
        requireAdmin(authUser, AdminPermission.ADMIN_PERMISSIONS_MANAGE);
        AdminPermissionDto dto = adminPermissionService.updateUserPermissions(
                authUser.getId(), userId, request.getPermissions());
        // 权限名不是敏感字段，可以记录；但限制长度（service 内 sanitize 会截断到 1000）
        adminAuditLogService.recordSuccess(authUser, AdminOperationAction.PERMISSION_UPDATE, "PERMISSION",
                userId, userId, "permissions=" + request.getPermissions(), httpRequest);
        return Result.success(dto);
    }

    /**
     * 防御性 ADMIN 校验 + 精细权限校验。
     *
     * <p>Spring Security 已在路由层做 {@code hasRole("ADMIN")} 拦截，
     * 此处做二次校验防止配置遗漏，并要求精细权限以兼容模式/显式模式规则判断。</p>
     */
    private void requireAdmin(AuthUser authUser, AdminPermission permission) {
        requireAdmin(authUser);
        adminPermissionService.requirePermission(authUser.getId(), permission);
    }

    /**
     * 防御性 ADMIN 校验（无精细权限要求）。
     */
    private void requireAdmin(AuthUser authUser) {
        if (authUser == null || !"ADMIN".equals(authUser.getRole())) {
            throw new AccessDeniedException("ADMIN required");
        }
    }
}
