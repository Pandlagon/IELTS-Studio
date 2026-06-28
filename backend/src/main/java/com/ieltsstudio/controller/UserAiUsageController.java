package com.ieltsstudio.controller;

import com.ieltsstudio.ai.service.AiUsageQueryService;
import com.ieltsstudio.common.Result;
import com.ieltsstudio.dto.ai.UserAiUsageDto;
import com.ieltsstudio.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户 AI 用量查询接口。
 *
 * <p>路径前缀：{@code /users/me/ai-usage}（叠加 context-path {@code /api} 后为
 * {@code /api/users/me/ai-usage}）。只读接口，需登录。</p>
 *
 * <p><b>鉴权：</b>统一通过 {@code @AuthenticationPrincipal AuthUser} 注入当前登录用户，
 * 从 {@code authUser.getId()} 取 userId，<b>不信任</b>前端传入的 userId。</p>
 */
@RestController
@RequestMapping("/users/me/ai-usage")
@RequiredArgsConstructor
public class UserAiUsageController {

    private final AiUsageQueryService aiUsageQueryService;

    @GetMapping
    public Result<UserAiUsageDto> getMyAiUsage(@AuthenticationPrincipal AuthUser authUser) {
        return Result.success(aiUsageQueryService.queryForUser(authUser.getId()));
    }
}
