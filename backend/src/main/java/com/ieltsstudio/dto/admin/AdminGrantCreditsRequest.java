package com.ieltsstudio.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 管理端给当前周期增加 {@code creditsTotal} 请求体（Phase 8B）。
 */
@Getter
@Setter
@ToString
public class AdminGrantCreditsRequest {

    @NotNull(message = "credits 不能为空")
    @Min(value = 1, message = "credits 必须 > 0")
    @Max(value = 100000, message = "credits 不能超过 100000")
    private Integer credits;
}
