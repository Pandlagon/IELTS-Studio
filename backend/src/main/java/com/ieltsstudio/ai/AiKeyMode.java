package com.ieltsstudio.ai;

/**
 * AI 调用 Key 模式枚举。
 *
 * <ul>
 *   <li>{@link #BUILTIN} —— 使用站点内置 API Key，未来消耗站点 credits。</li>
 *   <li>{@link #USER} —— 使用用户自填 API Key，未来不消耗站点 credits，但仍限流。</li>
 * </ul>
 */
public enum AiKeyMode {
    BUILTIN,
    USER
}
