package com.ieltsstudio.ai.model;

import lombok.Builder;
import lombok.Getter;

/**
 * 统一 AI 调用响应。
 *
 * <p>仅承载业务关心的字段（content / provider / model / statusCode），
 * provider 原始错误体由 {@link com.ieltsstudio.ai.client.OpenAiCompatibleClient} 脱敏后转为异常。</p>
 */
@Getter
@Builder
public class AiChatResponse {

    /** 响应文本（choices[0].message.content） */
    private final String content;

    /** Provider 标识（用于日志 / 审计） */
    private final String provider;

    /** 实际使用的模型名 */
    private final String model;

    /** HTTP 状态码 */
    private final Integer statusCode;
}
