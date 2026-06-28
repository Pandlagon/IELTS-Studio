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
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
 * Phase 5C-2：{@link QwenAiParseService} 通过真实 {@link OpenAiCompatibleClient}
 * 调用本地 fake Vision provider 的端到端式测试。
 *
 * <p>使用真实 client + 真实 {@link ObjectMapper}，仅 mock {@link AiSettingsService} /
 * {@link AiUsageGuard}；本地 fake provider 用 JDK {@link HttpServer} 启动，不请求真实外部 AI Provider。</p>
 *
 * <p>测试 Key：{@code sk-local-vision-key}（非真实 Key）。</p>
 */
class QwenAiParseServiceLocalProviderTest {

    private static final Long USER_ID = 7L;
    private static final String TEST_KEY = "sk-local-vision-key";

    private HttpServer server;
    private AiSettingsService aiSettingsService;
    private AiUsageGuard aiUsageGuard;
    private QwenAiParseService service;

    @BeforeEach
    void setUp() {
        aiSettingsService = mock(AiSettingsService.class);
        aiUsageGuard = mock(AiUsageGuard.class);
        ObjectMapper objectMapper = new ObjectMapper();
        service = new QwenAiParseService(objectMapper, aiSettingsService, aiUsageGuard,
                new OpenAiCompatibleClient(objectMapper));
        ReflectionTestUtils.setField(service, "aiMaxTokens", 8192);
        ReflectionTestUtils.setField(service, "mimoMaxTokens", 8192);
        ReflectionTestUtils.setField(service, "httpTimeoutSeconds", 60);
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

    /** 一段最小的 PNG 字节（仅用于让 detectMimeType + Base64 编码能跑通，不真实解码图片）。 */
    private static final byte[] FAKE_PNG_BYTES =
            new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01};

    @Test
    void parseImagesShouldCallLocalVisionProviderWithImageUrl() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<String> capturedAuth = new AtomicReference<>();
        AtomicReference<String> capturedMethod = new AtomicReference<>();
        AtomicReference<String> capturedPath = new AtomicReference<>();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", (HttpExchange exchange) -> {
            capturedMethod.set(exchange.getRequestMethod());
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] resp = openAiResponseJson(
                    "{\"passages\":[\"p\"],\"questions\":[{\"questionNumber\":1,\"type\":\"fill\",\"text\":\"q\",\"answer\":\"a\",\"explanation\":\"e\",\"locatorText\":\"p\"}]}")
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
                .provider(AiProviderType.QWEN)
                .taskType(AiTaskType.VISION)
                .baseUrl(baseUrl)
                .model("qwen-vl-plus")
                .apiKey(TEST_KEY)
                .tokenField("max_tokens")
                .build();
        when(aiSettingsService.resolve(USER_ID, AiTaskType.VISION)).thenReturn(credentials);

        Map<String, Object> result = service.parseImages(USER_ID, List.of(FAKE_PNG_BYTES), List.of("a.png"), "reading");

        // 解析成功
        assertEquals(List.of("p"), result.get("passages"));
        assertEquals(1, ((List<?>) result.get("questions")).size());

        // 本地 server 收到的请求形态
        assertNotNull(capturedBody.get());
        assertEquals("POST", capturedMethod.get());
        assertEquals("/chat/completions", capturedPath.get());
        // 鉴权头携带测试 key（非真实 key）
        assertEquals("Bearer " + TEST_KEY, capturedAuth.get());
        // 请求 body 包含必要字段
        assertTrue(capturedBody.get().contains("messages"), "body should contain messages");
        assertTrue(capturedBody.get().contains("image_url"), "body should contain image_url");
        assertTrue(capturedBody.get().contains("data:image/"), "body should contain data:image/ data URL");
        assertTrue(capturedBody.get().contains("response_format"), "body should contain response_format (json mode)");
        // 请求 body 不含 key（key 只在 Authorization 头），且不含真实 provider 域名
        assertFalse(capturedBody.get().contains(TEST_KEY));
        assertFalse(capturedBody.get().contains("sk-"));
        assertFalse(capturedBody.get().contains("dashscope.aliyuncs.com"),
                "body should not contain real provider domain");

        // usage guard：成功路径
        verify(aiUsageGuard).checkBeforeCall(USER_ID, AiFeature.EXAM_PRECISE_PARSE, AiKeyMode.USER, "QWEN");
        verify(aiUsageGuard).markSuccess(USER_ID, AiFeature.EXAM_PRECISE_PARSE, AiKeyMode.USER, "QWEN");
        verify(aiUsageGuard, never()).markFailure(any(), any(), any(), any(), any());
    }
}
