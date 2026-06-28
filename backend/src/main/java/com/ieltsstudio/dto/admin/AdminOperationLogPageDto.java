package com.ieltsstudio.dto.admin;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * 管理端操作审计日志分页响应 DTO（Phase 8D）。
 *
 * <p>与 {@link AdminUserPageDto} / {@link AdminQuotaPageDto} 保持一致的分页结构。</p>
 */
@Getter
@Setter
@ToString
public class AdminOperationLogPageDto {

    /** 当前页记录列表 */
    private List<AdminOperationLogDto> records;

    /** 总记录数 */
    private Long total;

    /** 当前页码（从 1 开始） */
    private Long page;

    /** 每页条数 */
    private Long pageSize;

    /** 总页数 */
    private Long pages;
}
