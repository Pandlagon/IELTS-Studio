package com.ieltsstudio.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 管理端修改用户角色请求体（Phase 8A）。
 *
 * <p>仅允许 {@code USER} / {@code ADMIN} 两个值，service 层会做白名单校验，
 * 拒绝 {@code SUPER_ADMIN} 等非法角色。</p>
 */
@Getter
@Setter
@ToString
public class AdminUpdateUserRoleRequest {

    @NotBlank(message = "role 不能为空")
    private String role;
}
