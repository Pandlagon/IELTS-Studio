package com.ieltsstudio.ai.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 统一 AI 调用请求。
 *
 * <p>由业务层组装后交给 {@link com.ieltsstudio.ai.client.OpenAiCompatibleClient} 发送。</p>
 */
@Getter
@Builder
public class AiChatRequest {

    /** 凭据快照（含 baseUrl / model / apiKey / tokenField） */
    private final AiCredentials credentials;

    /** 消息列表（system + user/assistant 轮次） */
    private final List<AiChatMessage> messages;

    /** 最大输出 token 数；null 表示不设置 */
    private final Integer maxTokens;

    /** 采样温度；null 表示不设置 */
    private final Double temperature;

    /** 是否启用 JSON mode（response_format = json_object） */
    private final Boolean jsonMode;

    /** 单次请求超时（秒）；null 表示使用客户端默认值 */
    private final Integer timeoutSeconds;
}
