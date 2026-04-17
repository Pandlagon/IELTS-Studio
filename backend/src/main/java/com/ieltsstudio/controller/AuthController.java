package com.ieltsstudio.controller;

import com.ieltsstudio.common.Result;
import com.ieltsstudio.dto.LoginRequest;
import com.ieltsstudio.dto.RegisterRequest;
import com.ieltsstudio.security.AuthUser;
import com.ieltsstudio.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口控制器
 *
 * <p>提供用户注册、登录、获取个人信息和退出登录接口。
 * 接口路径前缀：{@code /auth}，全部在 SecurityConfig 中设为白名单（无需 Token）。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户登录
     * <p>POST /auth/login
     * <p>支持用户名或邮箱登录，返回 JWT Token 和用户基本信息。
     */
    @PostMapping("/login")
    public Result<?> login(@Valid @RequestBody LoginRequest req) {
        return Result.success(authService.login(req));
    }

    /**
     * 用户注册
     * <p>POST /auth/register
     * <p>注册成功后自动颁发 Token，无需再次登录。
     */
    @PostMapping("/register")
    public Result<?> register(@Valid @RequestBody RegisterRequest req) {
        return Result.success(authService.register(req));
    }

    /**
     * 获取当前用户信息
     * <p>GET /auth/profile（需携带 Token）
     */
    @GetMapping("/profile")
    public Result<?> profile(@AuthenticationPrincipal AuthUser authUser) {
        return Result.success(authService.getProfile(authUser.getId()));
    }

    /**
     * 退出登录
     * <p>POST /auth/logout
     * <p>服务端为无状态设计，退出操作由前端清除本地 Token 实现，
     * 此接口仅作语义完整性保留。
     */
    @PostMapping("/logout")
    public Result<?> logout() {
        return Result.success("已退出登录");
    }
}
