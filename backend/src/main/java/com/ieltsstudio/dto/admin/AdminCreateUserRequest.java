package com.ieltsstudio.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 管理端新增用户请求体（Phase 8A 补充）。
 *
 * <p><b>安全：</b>{@code password} 通过 {@link ToString.Exclude} 排除，
 * 避免日志意外打印明文密码。后端使用 {@code PasswordEncoder.encode(...)} 存储 BCrypt 哈希。
 * {@code role} 由 Service 层白名单校验（仅允许 USER / ADMIN），不信任前端传入的其他值。</p>
 */
@Getter
@Setter
@ToString
public class AdminCreateUserRequest {

    @NotBlank(message = "username 不能为空")
    @Size(min = 3, max = 32, message = "用户名长度需在 3~32 之间")
    private String username;

    @NotBlank(message = "email 不能为空")
    @Email(message = "email 格式不正确")
    @Size(max = 128, message = "email 长度不能超过 128")
    private String email;

    @NotBlank(message = "password 不能为空")
    @Size(min = 8, max = 128, message = "密码长度需在 8~128 之间")
    @ToString.Exclude
    private String password;

    @NotBlank(message = "role 不能为空")
    private String role;
}
