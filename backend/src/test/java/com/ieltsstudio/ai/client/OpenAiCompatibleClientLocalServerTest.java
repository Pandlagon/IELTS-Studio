package com.ieltsstudio.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ieltsstudio.ai.AiKeyMode;
import com.ieltsstudio.ai.AiProviderType;
import com.ieltsstudio.ai.AiTaskType;
import com.ieltsstudio.ai.model.AiChatMessage;
import com.ieltsstudio.ai.model.AiChatRequest;
import com.ieltsstudio.ai.model.AiChatResponse;
import com.ieltsstudio.ai.model.AiCredentials;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 5A-local-provider-test：用 JDK 自带 {@link HttpServer} 在本地启动 fake
 * OpenAI-compatible provider，对 {@link OpenAiCompatibleClient} 做接近真实 HTTP 调用链的测试。
 *
 * <p>不访问真实 DeepSeek / Qwen / MiMO / OpenAI；不引入 WireMock / MockWebServer 等新依赖。
 * 端口由系统分配（{@code new InetSocketAddress("127.0.0.1", 0)}），每个测试后 {@code server.stop(0)}。</p>
 *
 * <p>测试 Key：{@code sk-local-test-key}（非真实 Key）。</p>
 */
class OpenAiCompatibleClientLocalServerTest {

    private static final String TEST_KEY = "sk-local-test-key";

    private HttpServer server;
    private final OpenAiCompatibleClient client = new OpenAiCompatibleClient(new ObjectMapper());

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    /** 启动绑定 127.0.0.1 + 随机端口的 fake provider，返回 baseUrl。 */
    private String startServer(HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", handler);
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private AiCredentials creds(String baseUrl, AiProviderType provider, String tokenField) {
        return AiCredentials.builder()
                .keyMode(AiKeyMode.USER)
                .provider(provider)
                .taskType(AiTaskType.TEXT)
                .baseUrl(baseUrl)
                .model("deepseek-chat")
                .apiKey(TEST_KEY)
                .tokenField(tokenField)
                .build();
    }

    /** 读取请求体并返回字符串。 */
    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    /** 发送 JSON 响应。 */
    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
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

    // ── 测试 1：成功请求，验证真实 HTTP 请求格式 ─────────────────────────────────

    @Test
    void chatShouldPostOpenAiCompatibleRequestToLocalServer() throws Exception {
        AtomicReference<String> capturedMethod = new AtomicReference<>();
        AtomicReference<String> capturedAuth = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();

        String baseUrl = startServer(exchange -> {
            capturedMethod.set(exchange.getRequestMethod());
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            capturedBody.set(readBody(exchange));
            sendJson(exchange, 200, openAiResponseJson("{\"ok\":true}"));
        });

        AiChatRequest request = AiChatRequest.builder()
                .credentials(creds(baseUrl, AiProviderType.DEEPSEEK, "max_tokens"))
                .messages(List.of(
                        AiChatMessage.system("you are a grader"),
                        AiChatMessage.user("grade this")))
                .maxTokens(123)
                .temperature(0.2)
                .jsonMode(true)
                .timeoutSeconds(5)
                .build();

        AiChatResponse response = client.chat(request);

        assertEquals("{\"ok\":true}", response.getContent());
        assertEquals(200, response.getStatusCode());
        assertEquals("DEEPSEEK", response.getProvider());
        assertEquals("deepseek-chat", response.getModel());

        assertEquals("POST", capturedMethod.get());
        assertEquals("Bearer " + TEST_KEY, capturedAuth.get());
        String body = capturedBody.get();
        assertTrue(body.contains("\"model\":\"deepseek-chat\""));
        assertTrue(body.contains("\"max_tokens\":123"));
        assertTrue(body.contains("\"temperature\":0.2"));
        assertTrue(body.contains("\"response_format\":{\"type\":\"json_object\"}"));
        assertTrue(body.contains("\"role\":\"system\""));
        assertTrue(body.contains("\"role\":\"user\""));
    }

    // ── 测试 2：MiMO tokenField 使用 max_completion_tokens ───────────────────────

    @Test
    void chatShouldUseMaxCompletionTokensForMimo() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();

        String baseUrl = startServer(exchange -> {
            capturedBody.set(readBody(exchange));
            sendJson(exchange, 200, openAiResponseJson("ok"));
        });

        AiChatRequest request = AiChatRequest.builder()
                .credentials(creds(baseUrl, AiProviderType.MIMO, "max_completion_tokens"))
                .messages(List.of(AiChatMessage.user("hi")))
                .maxTokens(456)
                .temperature(0.1)
                .jsonMode(false)
                .timeoutSeconds(5)
                .build();

        client.chat(request);

        String body = capturedBody.get();
        assertTrue(body.contains("\"max_completion_tokens\":456"));
        // 不应同时出现 max_tokens 字段
        assertFalse(body.contains("\"max_tokens\""));
    }

    // ── 测试 3：Provider 错误不能泄露 body / key ─────────────────────────────────

    @Test
    void chatShouldThrowSanitizedErrorWhenProviderReturnsError() throws Exception {
        // 故意把 key / Authorization 头塞进错误体，确保不会透传到异常 message
        String leakyBody = "invalid key " + TEST_KEY + " Authorization: Bearer " + TEST_KEY;

        String baseUrl = startServer(exchange -> {
            readBody(exchange);
            sendJson(exchange, 401, leakyBody);
        });

        AiChatRequest request = AiChatRequest.builder()
                .credentials(creds(baseUrl, AiProviderType.DEEPSEEK, "max_tokens"))
                .messages(List.of(AiChatMessage.user("hi")))
                .maxTokens(64)
                .timeoutSeconds(5)
                .build();

        RuntimeException ex = assertThrows(RuntimeException.class, () -> client.chat(request));

        String msg = ex.getMessage();
        // 不泄露 key / Authorization 头 / 原始 body
        assertFalse(msg.contains(TEST_KEY));
        assertFalse(msg.contains("Authorization"));
        assertFalse(msg.contains("invalid key"));
        // 可以包含 provider 名、HTTP status、check API Key configuration
        assertTrue(msg.contains("DEEPSEEK"));
        assertTrue(msg.contains("401"));
        assertTrue(msg.contains("check API Key configuration"));
    }
}
