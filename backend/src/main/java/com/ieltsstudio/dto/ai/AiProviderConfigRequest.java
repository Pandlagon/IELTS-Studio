package com.ieltsstudio.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 保存某一类 Provider（text / vision）配置的请求体。
 *
 * <p>语义：</p>
 * <ul>
 *   <li>{@code provider}：{@code DEEPSEEK} / {@code QWEN} / {@code MIMO} / {@code OPENAI_COMPATIBLE}。</li>
 *   <li>{@code baseUrl} / {@code model}：自定义 Provider 必填；预设 Provider 为空时由后端补默认值。</li>
 *   <li>{@code apiKey}：{@code null} 表示不修改旧 key；非空表示加密保存并更新 masked。</li>
 *   <li>{@code clearApiKey = true}：清空旧 key（忽略 {@code apiKey}）。</li>
 * </ul>
 *
 * <p><b>安全：</b>明文 apiKey 仅在后端内存中短暂存在，立即加密入库；不返回前端。
 * {@link #toString()} 已通过 {@link ToString.Exclude} 排除 {@code apiKey}，
 * 避免明文 key 被无意中打印到日志或异常栈。</p>
 */
@Getter
@Setter
@ToString
public class AiProviderConfigRequest {

    /** Provider 标识，必须能解析为 {@code AiProviderType} */
    @NotBlank(message = "provider 不能为空")
    private String provider;

    /** 自定义 OpenAI-compatible Base URL；预设 Provider 可为空 */
    private String baseUrl;

    /** 模型名；预设 Provider 可为空 */
    private String model;

    /** 明文 API Key：null=不改，非空=加密保存；clearApiKey=true 时忽略。已排除出 toString */
    @ToString.Exclude
    private String apiKey;

    /** 是否清空旧 key */
    private Boolean clearApiKey;
}
