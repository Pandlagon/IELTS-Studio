package com.ieltsstudio.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieltsstudio.ai.model.AiChatRequest;
import com.ieltsstudio.ai.model.AiCredentials;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link OpenAiCompatibleClient} 单元测试。
 *
 * <p>采用方案 B：不发真实网络请求，仅验证参数校验在 HTTP 调用前抛出异常。
 * {@code buildRequestBody} 等辅助方法为 private，不为测试强行改可见性。</p>
 */
class OpenAiCompatibleClientTest {

    private final OpenAiCompatibleClient client =
            new OpenAiCompatibleClient(new ObjectMapper());

    @Test
    void chatShouldThrowWhenRequestNull() {
        assertThrows(IllegalArgumentException.class, () -> client.chat(null));
    }

    @Test
    void chatShouldThrowWhenCredentialsNull() {
        AiChatRequest request = AiChatRequest.builder()
                .credentials(null)
                .build();
        assertThrows(IllegalArgumentException.class, () -> client.chat(request));
    }

    @Test
    void chatShouldThrowWhenApiKeyEmpty() {
        AiChatRequest request = AiChatRequest.builder()
                .credentials(AiCredentials.builder()
                        .apiKey("")
                        .build())
                .build();
        // apiKey 为空时在发 HTTP 前即抛异常，不会触达网络
        assertThrows(IllegalStateException.class, () -> client.chat(request));
    }

    @Test
    void chatShouldThrowWhenApiKeyNull() {
        AiChatRequest request = AiChatRequest.builder()
                .credentials(AiCredentials.builder()
                        .apiKey(null)
                        .build())
                .build();
        assertThrows(IllegalStateException.class, () -> client.chat(request));
    }
}
