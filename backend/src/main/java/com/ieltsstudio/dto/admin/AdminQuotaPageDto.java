package com.ieltsstudio.dto.admin;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * 管理端用户额度分页结果（Phase 8B）。
 */
@Getter
@Setter
@ToString
public class AdminQuotaPageDto {

    private List<AdminQuotaDto> records;
    private Long total;
    private Long page;
    private Long pageSize;
    private Long pages;
}
