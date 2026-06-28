package com.ieltsstudio.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 管理端设置当前周期 {@code creditsTotal} 请求体（Phase 8B）。
 */
@Getter
@Setter
@ToString
public class AdminSetQuotaTotalRequest {

    @NotNull(message = "creditsTotal 不能为空")
    @Min(value = 0, message = "creditsTotal 不能小于 0")
    @Max(value = 100000, message = "creditsTotal 不能超过 100000")
    private Integer creditsTotal;
}
