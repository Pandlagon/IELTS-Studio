package com.ieltsstudio.ai;

/**
 * AI Provider 类型枚举。
 *
 * <ul>
 *   <li>{@link #DEEPSEEK} —— DeepSeek，预设文本 Provider。</li>
 *   <li>{@link #QWEN} —— 通义千问 VL，预设视觉 Provider。</li>
 *   <li>{@link #MIMO} —— 小米 MiMO，预设视觉 Provider。</li>
 *   <li>{@link #OPENAI_COMPATIBLE} —— 用户自定义 OpenAI-compatible Provider。</li>
 * </ul>
 */
public enum AiProviderType {
    DEEPSEEK,
    QWEN,
    MIMO,
    OPENAI_COMPATIBLE
}
