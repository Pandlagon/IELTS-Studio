package com.ieltsstudio.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class SubmitAnswerRequest {

    @NotNull(message = "考试ID不能为空")
    private Long examId;

    @NotNull(message = "答案不能为空")
    private Map<Long, String> answers; // questionId -> userAnswer

    private Integer timeUsed; // seconds spent
}
