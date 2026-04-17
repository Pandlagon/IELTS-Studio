package com.ieltsstudio.security;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 认证用户信息载体
 *
 * <p>JWT 过滤器解析 Token 后，将用户信息封装为此对象并存入 SecurityContext。
 * 在 Controller 方法中通过 {@code @AuthenticationPrincipal AuthUser authUser}
 * 注解可直接注入当前登录用户，无需再查数据库。
 */
@Data
@AllArgsConstructor
public class AuthUser {

    /** 用户 ID */
    private Long id;

    /** 用户名 */
    private String username;

    /** 用户角色（如 USER、ADMIN） */
    private String role;
}
