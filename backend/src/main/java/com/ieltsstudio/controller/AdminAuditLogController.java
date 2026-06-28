package com.ieltsstudio.controller;

import com.ieltsstudio.common.Result;
import com.ieltsstudio.dto.admin.AdminOperationLogPageDto;
import com.ieltsstudio.entity.AdminPermission;
import com.ieltsstudio.security.AuthUser;
import com.ieltsstudio.service.AdminAuditLogService;
import com.ieltsstudio.service.AdminPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 管理端操作审计日志查询接口（Phase 8D）。
 *
 * <p>路径前缀：{@code /admin/audit-logs}（叠加 context-path {@code /api} 后为
 * {@code /api/admin/audit-logs}）。</p>
 *
 * <p><b>鉴权：</b>所有接口必须 ADMIN 角色访问 + {@link AdminPermission#ADMIN_AUDIT_LOG_VIEW} 权限。
 * <ul>
 *   <li>Spring Security 已对 {@code /admin/**} 配置 {@code hasRole("ADMIN")}。</li>
 *   <li>Controller 内 {@link #requireAdmin(AuthUser, AdminPermission)} 做防御性二次校验 +
 *       精细权限校验（Phase 8C）。</li>
 *   <li>兼容模式（{@code admin_user_permissions} 表为空）下所有 ADMIN 拥有全部权限；
 *       显式模式下只有被分配 {@link AdminPermission#ADMIN_AUDIT_LOG_VIEW} 的 ADMIN 才能访问。</li>
 * </ul>
 *
 * <p><b>安全：</b>本接口只读，不修改审计日志。返回的 DTO 不含 password/apiKey/token 等敏感字段，
 * {@code summary} 已在写入时脱敏。</p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AdminAuditLogService adminAuditLogService;
    private final AdminPermissionService adminPermissionService;

    /**
     * 分页查询审计日志。
     *
     * <p>权限：{@link AdminPermission#ADMIN_AUDIT_LOG_VIEW}</p>
     *
     * <p>支持筛选：actorUserId / targetUserId / action / resourceType / status / dateFrom / dateTo。
     * page 默认 1（最小 1），pageSize 默认 20（范围 1~100）。
     * dateFrom / dateTo 接受 ISO-8601 格式（如 {@code 2026-06-01T00:00:00}）。</p>
     */
    @GetMapping
    public Result<AdminOperationLogPageDto> list(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) Long targetUserId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {
        requireAdmin(authUser, AdminPermission.ADMIN_AUDIT_LOG_VIEW);
        return Result.success(adminAuditLogService.listLogs(
                page, pageSize, actorUserId, targetUserId, action, resourceType, status, dateFrom, dateTo));
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
