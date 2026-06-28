package com.ieltsstudio.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 管理端重置用户密码请求体（Phase 8A）。
 *
 * <p><b>安全：</b>{@code newPassword} 通过 {@link ToString.Exclude} 排除，
 * 避免日志意外打印明文密码。后端使用 {@code PasswordEncoder.encode(...)} 存储 BCrypt 哈希，
 * 永远不记录明文密码日志。</p>
 */
@Getter
@Setter
@ToString
public class AdminResetPasswordRequest {

    @NotBlank(message = "newPassword 不能为空")
    @Size(min = 8, max = 128, message = "新密码长度需在 8~128 之间")
    @ToString.Exclude
    private String newPassword;
}
