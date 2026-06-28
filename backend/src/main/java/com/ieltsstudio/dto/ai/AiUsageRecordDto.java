package com.ieltsstudio.dto.ai;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单条 AI 使用记录的脱敏视图。
 *
 * <p>{@link #errorMessage} 已由 {@code AiUsageGuard} 在写入时脱敏，
 * 查询接口不再加工；本 DTO 不包含任何 API Key / Authorization 字段。</p>
 */
@Data
public class AiUsageRecordDto {

    /** 记录创建时间 */
    private LocalDateTime createdAt;

    /** 任务类型：TEXT / VISION */
    private String taskType;

    /** AI 功能标识：WRITING_GRADE / AI_CHAT / TRANSLATE 等 */
    private String feature;

    /** 本次消耗 credits（失败/拒绝为 0） */
    private Integer cost;

    /** Key 模式：BUILTIN / USER */
    private String keyMode;

    /** Provider 枚举名：DEEPSEEK / QWEN / MIMO / OPENAI_COMPATIBLE，可能为 null */
    private String provider;

    /** 状态：SUCCESS / FAILED / REJECTED */
    private String status;

    /** 脱敏后的错误摘要（可能为 null） */
    private String errorMessage;
}
