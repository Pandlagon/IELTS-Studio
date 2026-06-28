package com.ieltsstudio.controller;

import com.ieltsstudio.common.Result;
import com.ieltsstudio.dto.admin.AdminCreateUserRequest;
import com.ieltsstudio.dto.admin.AdminResetPasswordRequest;
import com.ieltsstudio.dto.admin.AdminUpdateUserRoleRequest;
import com.ieltsstudio.dto.admin.AdminUserDto;
import com.ieltsstudio.dto.admin.AdminUserPageDto;
import com.ieltsstudio.entity.AdminPermission;
import com.ieltsstudio.security.AuthUser;
import com.ieltsstudio.service.AdminPermissionService;
import com.ieltsstudio.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端用户管理接口（Phase 8A）。
 *
 * <p>路径前缀：{@code /admin/users}（叠加 context-path {@code /api} 后为
 * {@code /api/admin/users}）。</p>
 *
 * <p><b>鉴权：</b>所有接口必须 ADMIN 角色访问。
 * <ul>
 *   <li>Spring Security 已对 {@code /admin/**} 配置 {@code hasRole("ADMIN")}（见 SecurityConfig）。</li>
 *   <li>Controller 内 {@link #requireAdmin(AuthUser, AdminPermission)} 做防御性二次校验 +
 *       精细权限校验（Phase 8C）。</li>
 *   <li>userId / role 一律从 {@code @AuthenticationPrincipal AuthUser} 取。</li>
 * </ul>
 *
 * <p><b>Phase 8C 精细权限：</b>
 * <ul>
 *   <li>{@code GET /admin/users} / {@code GET /admin/users/{id}} → {@link AdminPermission#ADMIN_USERS_VIEW}</li>
 *   <li>{@code POST /admin/users} / {@code PUT /admin/users/{id}/role} /
 *       {@code PUT /admin/users/{id}/disable} / {@code PUT /admin/users/{id}/enable} →
 *       {@link AdminPermission#ADMIN_USERS_MANAGE}</li>
 *   <li>{@code POST /admin/users/{id}/reset-password} →
 *       {@link AdminPermission#ADMIN_USERS_RESET_PASSWORD}</li>
 * </ul>
 * 兼容模式（permissions 表为空）下所有 ADMIN 拥有全部权限。</p>
 *
 * <p><b>安全：</b>所有返回的 DTO 均不包含 password 字段；重置密码使用 BCrypt 加密；
 * 不记录明文密码日志。</p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final AdminPermissionService adminPermissionService;

    /**
     * 用户列表（分页 + 筛选）。
     *
     * <p>权限：{@link AdminPermission#ADMIN_USERS_VIEW}</p>
     */
    @GetMapping
    public Result<AdminUserPageDto> list(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status) {
        requireAdmin(authUser, AdminPermission.ADMIN_USERS_VIEW);
        return Result.success(adminUserService.listUsers(page, pageSize, keyword, role, status));
    }

    /**
     * 新增用户。
     *
     * <p>权限：{@link AdminPermission#ADMIN_USERS_MANAGE}</p>
     *
     * <p>规则：role 白名单（USER/ADMIN）、username/email 唯一性、密码 BCrypt 加密、不返回 password。
     * userId / role 一律以服务端处理为准，不信任前端。</p>
     */
    @PostMapping
    public Result<AdminUserDto> create(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody AdminCreateUserRequest request) {
        requireAdmin(authUser, AdminPermission.ADMIN_USERS_MANAGE);
        return Result.success(adminUserService.createUser(request));
    }

    /**
     * 用户详情。
     *
     * <p>权限：{@link AdminPermission#ADMIN_USERS_VIEW}</p>
     */
    @GetMapping("/{id}")
    public Result<AdminUserDto> detail(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id) {
        requireAdmin(authUser, AdminPermission.ADMIN_USERS_VIEW);
        return Result.success(adminUserService.getUser(id));
    }

    /**
     * 修改用户角色。
     *
     * <p>权限：{@link AdminPermission#ADMIN_USERS_MANAGE}</p>
     *
     * <p>保护：不能降级自己、不能降级最后一个 ADMIN、role 只能是 USER / ADMIN。</p>
     */
    @PutMapping("/{id}/role")
    public Result<AdminUserDto> updateRole(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @Valid @RequestBody AdminUpdateUserRoleRequest request) {
        requireAdmin(authUser, AdminPermission.ADMIN_USERS_MANAGE);
        return Result.success(adminUserService.updateRole(authUser.getId(), id, request.getRole()));
    }

    /**
     * 禁用用户（deleted = 1）。
     *
     * <p>权限：{@link AdminPermission#ADMIN_USERS_MANAGE}</p>
     *
     * <p>保护：不能禁用自己、不能禁用最后一个 ADMIN。</p>
     */
    @PutMapping("/{id}/disable")
    public Result<AdminUserDto> disable(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id) {
        requireAdmin(authUser, AdminPermission.ADMIN_USERS_MANAGE);
        return Result.success(adminUserService.disableUser(authUser.getId(), id));
    }

    /**
     * 启用用户（deleted = 0）。不改变其 role。
     *
     * <p>权限：{@link AdminPermission#ADMIN_USERS_MANAGE}</p>
     */
    @PutMapping("/{id}/enable")
    public Result<AdminUserDto> enable(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id) {
        requireAdmin(authUser, AdminPermission.ADMIN_USERS_MANAGE);
        return Result.success(adminUserService.enableUser(authUser.getId(), id));
    }

    /**
     * 重置用户密码。
     *
     * <p>权限：{@link AdminPermission#ADMIN_USERS_RESET_PASSWORD}</p>
     *
     * <p>安全：使用 BCrypt 加密存储，不返回 password，不记录明文密码日志。
     * 允许管理员重置自己的密码（token 仍由当前登录流程处理）。</p>
     */
    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id,
            @Valid @RequestBody AdminResetPasswordRequest request) {
        requireAdmin(authUser, AdminPermission.ADMIN_USERS_RESET_PASSWORD);
        adminUserService.resetPassword(id, request.getNewPassword());
        return Result.success();
    }

    /**
     * 防御性 ADMIN 校验 + 精细权限校验（Phase 8C）。
     *
     * <p>Spring Security 已在路由层做 {@code hasRole("ADMIN")} 拦截，
     * 此处做二次校验防止配置遗漏，并要求精细权限以兼容模式/显式模式规则判断。</p>
     */
    private void requireAdmin(AuthUser authUser, AdminPermission permission) {
        if (authUser == null || !"ADMIN".equals(authUser.getRole())) {
            throw new AccessDeniedException("ADMIN required");
        }
        adminPermissionService.requirePermission(authUser.getId(), permission);
    }
}
