package com.ieltsstudio.controller;

import com.ieltsstudio.common.Result;
import com.ieltsstudio.dto.ai.AiProviderPresetResponse;
import com.ieltsstudio.dto.ai.AiSettingsResponse;
import com.ieltsstudio.dto.ai.AiSettingsUpdateRequest;
import com.ieltsstudio.security.AuthUser;
import com.ieltsstudio.service.UserAiSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 用户 AI 设置接口控制器。
 *
 * <p>接口路径前缀：{@code /users/me/ai-settings}（叠加 context-path {@code /api} 后完整路径为
 * {@code /api/users/me/ai-settings}）。所有接口均需登录认证。</p>
 *
 * <p><b>鉴权：</b>统一通过 {@code @AuthenticationPrincipal AuthUser} 注入当前登录用户，
 * 从 {@code authUser.getId()} 取 userId，<b>不信任</b>前端传入的 userId。</p>
 *
 * <p><b>安全：</b>响应只返回 masked key，不返回明文 / encrypted key。</p>
 */
@RestController
@RequestMapping("/users/me/ai-settings")
@RequiredArgsConstructor
public class UserAiSettingsController {

    private final UserAiSettingsService userAiSettingsService;

    /**
     * GET /users/me/ai-settings — 获取当前用户 AI 设置（脱敏）。
     *
     * <p>无设置记录时自动创建默认 BUILTIN 设置并返回。</p>
     */
    @GetMapping
    public Result<AiSettingsResponse> getAiSettings(@AuthenticationPrincipal AuthUser authUser) {
        return Result.success(userAiSettingsService.getSettings(authUser.getId()));
    }

    /**
     * PUT /users/me/ai-settings — 更新当前用户 AI 设置，返回更新后的脱敏设置。
     *
     * <p>API Key 在后端加密保存；响应只含 masked key。</p>
     */
    @PutMapping
    public Result<AiSettingsResponse> updateAiSettings(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody AiSettingsUpdateRequest request) {
        return Result.success(userAiSettingsService.updateSettings(authUser.getId(), request));
    }

    /**
     * GET /users/me/ai-settings/providers — 获取可用 Provider 预设列表。
     *
     * <p>返回 {@code {"text": [...], "vision": [...]}}，两组均包含 OPENAI_COMPATIBLE 自定义模板。</p>
     */
    @GetMapping("/providers")
    public Result<Map<String, List<AiProviderPresetResponse>>> getProviders(
            @AuthenticationPrincipal AuthUser authUser) {
        return Result.success(userAiSettingsService.listProviderPresets());
    }
}
