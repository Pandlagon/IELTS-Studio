package com.ieltsstudio.dto.admin;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端用户详情 DTO（Phase 8A）。
 *
 * <p><b>安全：</b>本 DTO <b>不包含</b> {@code password} 字段，密码永远不会通过本类返回前端。
 * {@code deleted} 字段用于管理端展示用户启用/禁用状态（0=启用，1=禁用）。</p>
 */
@Data
public class AdminUserDto {
    private Long id;
    private String username;
    private String email;
    private String avatar;
    private String role;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
