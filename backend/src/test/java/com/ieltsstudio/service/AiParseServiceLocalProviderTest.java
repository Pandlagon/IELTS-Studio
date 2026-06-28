package com.ieltsstudio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ieltsstudio.ai.AiFeature;
import com.ieltsstudio.ai.AiKeyMode;
import com.ieltsstudio.ai.AiProviderType;
import com.ieltsstudio.ai.AiTaskType;
import com.ieltsstudio.ai.client.OpenAiCompatibleClient;
import com.ieltsstudio.ai.model.AiCredentials;
import com.ieltsstudio.ai.service.AiSettingsService;
import com.ieltsstudio.ai.service.AiUsageGuard;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 5A-local-provider-test：{@link AiParseService} 通过真实 {@link OpenAiCompatibleClient}
 * 调用本地 fake provider 的端到端式测试。
 *
 * <p>使用真实 client + 真实 {@link ObjectMapper}，仅 mock {@link AiSettingsService} /
 * {@link AiUsageGuard}；本地 fake provider 用 JDK {@link HttpServer} 启动，不请求真实外部 AI Provider。</p>
 *
 * <p>测试 Key：{@code sk-local-test-key}（非真实 Key）。</p>
 */
class AiParseServiceLocalProviderTest {

    private static final Long USER_ID = 7L;
    private static final String TEST_KEY = "sk-local-test-key";

    private HttpServer server;
    private AiSettingsService aiSettingsService;
    private AiUsageGuard aiUsageGuard;
    private AiParseService service;

    @BeforeEach
    void setUp() {
        aiSettingsService = mock(AiSettingsService.class);
        aiUsageGuard = mock(AiUsageGuard.class);
        ObjectMapper objectMapper = new ObjectMapper();
        service = new AiParseService(objectMapper, aiSettingsService, aiUsageGuard,
                new OpenAiCompatibleClient(objectMapper));
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    /** 构造 OpenAI-compatible 响应：{"choices":[{"message":{"content":"<content>"}}]} */
    private static String openAiResponseJson(String content) throws IOException {
        ObjectMapper om = new ObjectMapper();
        ObjectNode root = om.createObjectNode();
        ArrayNode choices = om.createArrayNode();
        ObjectNode choice = om.createObjectNode();
        ObjectNode message = om.createObjectNode();
        message.put("content", content);
        choice.set("message", message);
        choices.add(choice);
        root.set("choices", choices);
        return om.writeValueAsString(root);
    }

    @Test
    void gradeWritingShouldCallLocalProviderAndMarkSuccessAfterParsed() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<String> capturedAuth = new AtomicReference<>();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", (HttpExchange exchange) -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] resp = openAiResponseJson("{\"band\":7.0,\"bandDescription\":\"Good\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        AiCredentials credentials = AiCredentials.builder()
                .keyMode(AiKeyMode.USER)
                .provider(AiProviderType.DEEPSEEK)
                .taskType(AiTaskType.TEXT)
                .baseUrl(baseUrl)
                .model("deepseek-chat")
                .apiKey(TEST_KEY)
                .tokenField("max_tokens")
                .build();
        when(aiSettingsService.resolve(USER_ID, AiTaskType.TEXT)).thenReturn(credentials);

        Map<String, Object> result = service.gradeWriting(USER_ID, "Task 2 prompt",
                "This is a test essay with enough content.", 250);

        // 返回 map 中 band = 7.0
        assertEquals(7.0, ((Number) result.get("band")).doubleValue());
        assertEquals("Good", result.get("bandDescription"));

        // local server 确实收到请求
        assertNotNull(capturedBody.get());
        assertFalse(capturedBody.get().isEmpty());
        // 鉴权头携带测试 key（非真实 key）
        assertEquals("Bearer " + TEST_KEY, capturedAuth.get());
        // 请求 body 不含 key（key 只在 Authorization 头），且不含真实 provider 域名
        assertFalse(capturedBody.get().contains(TEST_KEY));
        assertFalse(capturedBody.get().contains("sk-"));
        assertFalse(capturedBody.get().contains("api.deepseek.com"));
        assertTrue(capturedBody.get().contains("deepseek-chat"));

        // usage guard：成功路径
        verify(aiUsageGuard).checkBeforeCall(USER_ID, AiFeature.WRITING_GRADE, AiKeyMode.USER, "DEEPSEEK");
        verify(aiUsageGuard).markSuccess(USER_ID, AiFeature.WRITING_GRADE, AiKeyMode.USER, "DEEPSEEK");
        verify(aiUsageGuard, never()).markFailure(any(), any(), any(), any(), any());
    }
}
