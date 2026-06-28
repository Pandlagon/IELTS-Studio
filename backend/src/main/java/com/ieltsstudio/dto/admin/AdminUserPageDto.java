package com.ieltsstudio.dto.admin;

import lombok.Data;

import java.util.List;

/**
 * 管理端用户分页 DTO（Phase 8A）。
 *
 * <p>封装分页用户列表与分页元信息。{@code records} 中的每个 {@link AdminUserDto}
 * 均不包含 password 字段。</p>
 */
@Data
public class AdminUserPageDto {
    private List<AdminUserDto> records;
    private Long total;
    private Long page;
    private Long pageSize;
    private Long pages;
}
