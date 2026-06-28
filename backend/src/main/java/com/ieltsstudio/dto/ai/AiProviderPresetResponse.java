package com.ieltsstudio.dto.ai;

import lombok.Builder;
import lombok.Getter;

/**
 * Provider 预设描述，用于前端展示可选 Provider 列表。
 *
 * <p>来自 {@link com.ieltsstudio.ai.model.AiProviderPreset}，不含任何 API Key。</p>
 */
@Getter
@Builder
public class AiProviderPresetResponse {

    /** Provider 标识，如 DEEPSEEK / QWEN / MIMO / OPENAI_COMPATIBLE */
    private String provider;

    /** 展示名称 */
    private String displayName;

    /** 默认任务类型：TEXT / VISION；自定义模板为 null */
    private String taskType;

    /** 默认 Base URL；自定义模板为 null */
    private String defaultBaseUrl;

    /** 默认模型名；自定义模板为 null */
    private String defaultModel;

    /** Token 字段名：max_tokens / max_completion_tokens */
    private String tokenField;

    /** 是否支持多模态（vision） */
    private boolean supportsVision;

    /** 是否为用户自定义模板（OPENAI_COMPATIBLE） */
    private boolean custom;
}
