package com.ieltsstudio.dto.ai;

import lombok.Builder;
import lombok.Getter;

/**
 * 单个 Provider 配置的脱敏响应。
 *
 * <p><b>安全：</b>不返回明文 apiKey，也不返回 encryptedApiKey；
 * 只返回 maskedApiKey（如 {@code sk-****abcd}）与 {@code hasApiKey} 标记。</p>
 */
@Getter
@Builder
public class AiProviderConfigResponse {

    /** Provider 标识，如 DEEPSEEK / QWEN / OPENAI_COMPATIBLE */
    private String provider;

    /** 展示名称，如 DeepSeek / Qwen / OpenAI-compatible (custom) */
    private String displayName;

    /** Base URL（不带尾部 /chat/completions） */
    private String baseUrl;

    /** 模型名 */
    private String model;

    /** 是否已配置 API Key（基于 encrypted 字段是否非空判断） */
    private boolean hasApiKey;

    /** 脱敏后的 API Key，如 sk-****abcd；未配置时为 null */
    private String maskedApiKey;

    /** 是否为自定义（OPENAI_COMPATIBLE）Provider */
    private boolean custom;
}
