package com.ieltsstudio.ai.model;

import com.ieltsstudio.ai.AiProviderType;
import com.ieltsstudio.ai.AiTaskType;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 预设 Provider 描述：内置 Provider 的默认 baseUrl / model / tokenField 等元数据。
 *
 * <p>不含 API Key，Key 由 {@link com.ieltsstudio.ai.service.AiSettingsService} 在运行时注入。</p>
 *
 * <p>{@link #custom} = true 表示这是一个"用户自定义"模板（OPENAI_COMPATIBLE），
 * baseUrl / model 由用户后续填写，仅作为占位预设存在。</p>
 */
@Slf4j
@Getter
@Builder
public class AiProviderPreset {

    /** Provider 类型 */
    private final AiProviderType provider;

    /** 展示名称（中文 / 英文） */
    private final String displayName;

    /**
     * 默认任务类型。对自定义 Provider 可能为 {@code null}，表示由用户在配置时决定。
     */
    private final AiTaskType taskType;

    /** 默认 Base URL（不带尾部 /chat/completions） */
    private final String defaultBaseUrl;

    /** 默认模型名 */
    private final String defaultModel;

    /**
     * Token 字段名：{@code max_tokens} 或 {@code max_completion_tokens}。
     * 不同 OpenAI-compatible provider 对此字段命名有差异（如 MiMO 用 max_completion_tokens）。
     */
    private final String tokenField;

    /** 是否支持多模态（vision / image_url） */
    private final boolean supportsVision;

    /** 是否为用户自定义模板（true = OPENAI_COMPATIBLE 占位） */
    private final boolean custom;

    @Override
    public String toString() {
        // 不含敏感字段；preset 本身不持有 key，安全。
        return "AiProviderPreset{provider=" + provider
                + ", displayName='" + displayName + '\''
                + ", taskType=" + taskType
                + ", defaultBaseUrl='" + defaultBaseUrl + '\''
                + ", defaultModel='" + defaultModel + '\''
                + ", tokenField='" + tokenField + '\''
                + ", supportsVision=" + supportsVision
                + ", custom=" + custom + '}';
    }
}
