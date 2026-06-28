package com.ieltsstudio.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieltsstudio.ai.model.AiChatMessage;
import com.ieltsstudio.ai.model.AiChatRequest;
import com.ieltsstudio.ai.model.AiChatResponse;
import com.ieltsstudio.ai.model.AiCredentials;
import com.ieltsstudio.ai.util.AiLogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一 OpenAI-compatible chat completions 客户端。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>统一发送 {@code POST /chat/completions} 请求。</li>
 *   <li>支持文本消息（content 为 String）与多模态消息（content 为 List/Map，含 image_url）。</li>
 *   <li>支持 JSON mode（{@code response_format: {"type":"json_object"}}）。</li>
 *   <li>根据 {@link AiCredentials#getTokenField()} 决定使用 {@code max_tokens} 或 {@code max_completion_tokens}。</li>
 *   <li>使用 {@code Authorization: Bearer <apiKey>} 头。</li>
 *   <li>provider 错误脱敏，不把完整 response body 直接返回给调用方 / 用户。</li>
 *   <li>日志中<b>不</b>输出 API Key 或 Authorization 头。</li>
 * </ul>
 *
 * <p>实现使用项目现有风格的 {@link HttpClient}，不新增依赖。</p>
 *
 * <p><b>SSRF TODO：</b>当前阶段对自定义 Base URL 仅做协议校验（http/https），
 * 完整的内网 / 保留地址校验留给后续阶段。详见 {@code docs/ai-provider-architecture.md} §5。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleClient {

    private static final HttpClient SHARED_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /** 默认请求超时（秒），仅当 request 未指定时使用 */
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;

    private final ObjectMapper objectMapper;

    /**
     * 发送一次 chat completions 请求。
     *
     * @param request 统一请求（含凭据、消息、参数）
     * @return 统一响应（content / provider / model / statusCode）
     * @throws Exception 网络 / 超时 / provider 错误（已脱敏）
     */
    public AiChatResponse chat(AiChatRequest request) throws Exception {
        if (request == null) {
            throw new IllegalArgumentException("AiChatRequest must not be null");
        }
        AiCredentials credentials = request.getCredentials();
        if (credentials == null) {
            throw new IllegalArgumentException("AiChatRequest.credentials must not be null");
        }
        if (credentials.getApiKey() == null || credentials.getApiKey().isEmpty()) {
            // 不输出 key 本身，只提示未配置
            throw new IllegalStateException(
                    "AI API Key is not configured for provider=" + credentials.getProvider());
        }

        String baseUrl = normalizeBaseUrl(credentials.getBaseUrl());
        // SSRF 基础校验：仅放行 http/https。完整内网/保留地址校验留待后续阶段。
        ensureHttpScheme(baseUrl, credentials.getProvider());

        Map<String, Object> body = buildRequestBody(credentials, request);
        String requestJson = objectMapper.writeValueAsString(body);

        // 注意：日志中严禁出现 Authorization 头 / key；只记 provider、model、消息数、是否 json mode
        log.info("AI chat request: provider={}, model={}, messages={}, jsonMode={}, timeoutSec={}",
                credentials.getProvider(),
                credentials.getModel(),
                request.getMessages() == null ? 0 : request.getMessages().size(),
                request.getJsonMode(),
                request.getTimeoutSeconds() == null ? DEFAULT_TIMEOUT_SECONDS : request.getTimeoutSeconds());

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + credentials.getApiKey()) // 不入日志
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(request.getTimeoutSeconds() == null
                        ? DEFAULT_TIMEOUT_SECONDS
                        : Math.max(1, request.getTimeoutSeconds())))
                .build();

        HttpResponse<String> response;
        try {
            response = SHARED_HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException te) {
            log.warn("AI chat timeout: provider={}, model={}", credentials.getProvider(), credentials.getModel());
            throw new RuntimeException("AI service timed out, please retry later", te);
        }

        int statusCode = response.statusCode();
        if (statusCode >= 400) {
            // 脱敏后的错误体只进 debug 日志，不进异常 message
            String sanitized = AiLogSanitizer.summarizeProviderError(response.body());
            log.debug("AI provider error: provider={}, model={}, status={}, body={}",
                    credentials.getProvider(), credentials.getModel(), statusCode, sanitized);
            // 给调用方的 message 不含原始 body
            throw new RuntimeException(
                    "AI provider " + credentials.getProvider() + " returned HTTP " + statusCode
                            + (statusCode == 401 || statusCode == 403
                                    ? " (check API Key configuration)"
                                    : ""));
        }

        JsonNode root = objectMapper.readTree(response.body());
        String content = root.path("choices").path(0).path("message").path("content").asText("");

        log.info("AI chat success: provider={}, model={}, status={}, contentLen={}",
                credentials.getProvider(), credentials.getModel(), statusCode, content.length());

        return AiChatResponse.builder()
                .content(content)
                .provider(String.valueOf(credentials.getProvider()))
                .model(credentials.getModel())
                .statusCode(statusCode)
                .build();
    }

    private Map<String, Object> buildRequestBody(AiCredentials credentials, AiChatRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", credentials.getModel());

        // 根据 tokenField 决定字段名：max_tokens 或 max_completion_tokens
        String tokenField = credentials.getTokenField();
        if (tokenField == null || tokenField.isEmpty()) {
            tokenField = "max_tokens";
        }
        if (request.getMaxTokens() != null) {
            body.put(tokenField, request.getMaxTokens());
        }
        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }
        if (Boolean.TRUE.equals(request.getJsonMode())) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        // messages：role + content（content 可为 String 或 List/Map）
        List<AiChatMessage> messages = request.getMessages();
        if (messages != null && !messages.isEmpty()) {
            List<Map<String, Object>> msgList = new java.util.ArrayList<>(messages.size());
            for (AiChatMessage m : messages) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("role", m.getRole());
                entry.put("content", m.getContent());
                msgList.add(entry);
            }
            body.put("messages", msgList);
        }
        return body;
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("AI base URL is empty");
        }
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private void ensureHttpScheme(String baseUrl, com.ieltsstudio.ai.AiProviderType provider) {
        String lower = baseUrl.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            // TODO(后续阶段): 完整 SSRF 校验 —— 禁止内网/保留地址，需配置白名单
            throw new IllegalStateException(
                    "AI base URL must start with http:// or https:// (provider=" + provider + ")");
        }
    }
}
