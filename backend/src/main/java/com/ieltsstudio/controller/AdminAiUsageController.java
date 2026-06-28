package com.ieltsstudio.controller;

import com.ieltsstudio.ai.service.AdminAiUsageStatsService;
import com.ieltsstudio.common.Result;
import com.ieltsstudio.dto.ai.AdminAiUsageRecentRecordDto;
import com.ieltsstudio.dto.ai.AdminAiUsageSummaryDto;
import com.ieltsstudio.entity.AdminPermission;
import com.ieltsstudio.security.AuthUser;
import com.ieltsstudio.service.AdminPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端 AI usage 统计接口（Phase 6B-2B）。
 *
 * <p>路径前缀：{@code /admin/ai-usage}（叠加 context-path {@code /api} 后为
 * {@code /api/admin/ai-usage}）。</p>
 *
 * <p><b>鉴权：</b>所有接口必须 ADMIN 角色访问。
 * <ul>
 *   <li>Spring Security 已对 {@code /admin/**} 配置 {@code hasRole("ADMIN")}（见 SecurityConfig）。</li>
 *   <li>Controller 内 {@link #requireAdmin(AuthUser, AdminPermission)} 做防御性二次校验 +
 *       精细权限校验（Phase 8C）。</li>
 *   <li>userId / role 一律从 {@code @AuthenticationPrincipal AuthUser} 取。</li>
 * </ul>
 *
 * <p><b>Phase 8C 精细权限：</b>所有接口要求 {@link AdminPermission#ADMIN_AI_USAGE_VIEW}。
 * 兼容模式（permissions 表为空）下所有 ADMIN 拥有全部权限。</p>
 *
 * <p><b>只读：</b>本 Controller 不 insert / update / delete 任何数据。</p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/ai-usage")
@RequiredArgsConstructor
public class AdminAiUsageController {

    private final AdminAiUsageStatsService statsService;
    private final AdminPermissionService adminPermissionService;

    /**
     * 汇总最近 N 天的 AI usage 统计。
     *
     * <p>权限：{@link AdminPermission#ADMIN_AI_USAGE_VIEW}</p>
     *
     * @param days 统计天数，默认 7，范围 {@code [1, 90]}，越界自动 clamp
     */
    @GetMapping("/summary")
    public Result<AdminAiUsageSummaryDto> summary(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(defaultValue = "7") int days) {
        requireAdmin(authUser, AdminPermission.ADMIN_AI_USAGE_VIEW);
        return Result.success(statsService.summary(days));
    }

    /**
     * 查询最近 N 条 AI usage records，按 createdAt 倒序。
     *
     * <p>权限：{@link AdminPermission#ADMIN_AI_USAGE_VIEW}</p>
     *
     * @param limit 返回数量，默认 50，范围 {@code [1, 100]}，越界自动 clamp
     */
    @GetMapping("/recent")
    public Result<List<AdminAiUsageRecentRecordDto>> recent(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(defaultValue = "50") int limit) {
        requireAdmin(authUser, AdminPermission.ADMIN_AI_USAGE_VIEW);
        return Result.success(statsService.recent(limit));
    }

    /**
     * 防御性 ADMIN 校验 + 精细权限校验（Phase 8C）。
     */
    private void requireAdmin(AuthUser authUser, AdminPermission permission) {
        if (authUser == null || !"ADMIN".equals(authUser.getRole())) {
            throw new AccessDeniedException("ADMIN required");
        }
        adminPermissionService.requirePermission(authUser.getId(), permission);
    }
}
