package com.ieltsstudio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieltsstudio.ai.AiFeature;
import com.ieltsstudio.ai.AiKeyMode;
import com.ieltsstudio.ai.AiProviderType;
import com.ieltsstudio.ai.AiTaskType;
import com.ieltsstudio.ai.client.OpenAiCompatibleClient;
import com.ieltsstudio.ai.model.AiChatMessage;
import com.ieltsstudio.ai.model.AiChatRequest;
import com.ieltsstudio.ai.model.AiChatResponse;
import com.ieltsstudio.ai.model.AiCredentials;
import com.ieltsstudio.ai.service.AiSettingsService;
import com.ieltsstudio.ai.service.AiUsageGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 5C-2：{@link QwenAiParseService} Vision 精准解析接入新 AI Provider 架构的单元测试。
 *
 * <p>不真实访问 Qwen / MiMO / DeepSeek：
 * {@link AiSettingsService} / {@link AiUsageGuard} / {@link OpenAiCompatibleClient}
 * 均用 Mockito mock；{@link ObjectMapper} 用真实实例以便验证 JSON 解析行为。</p>
 *
 * <p>测试字符串：{@code sk-user-test-key-999}（非真实 Key）。</p>
 *
 * <p>说明：{@link QwenAiParseService} 的 {@code @Value} 字段（{@code aiMaxTokens} /
 * {@code mimoMaxTokens} / {@code httpTimeoutSeconds}）在纯 Mockito 测试中不会被 Spring
 * 注入，这里通过 {@link ReflectionTestUtils} 显式设置，使 maxTokens / timeout 行为与生产一致。</p>
 */
class QwenAiParseServiceProviderTest {

    private static final Long USER_ID = 42L;
    private static final String USER_KEY = "sk-user-test-key-999";

    private AiSettingsService aiSettingsService;
    private AiUsageGuard aiUsageGuard;
    private OpenAiCompatibleClient openAiCompatibleClient;
    private QwenAiParseService service;

    @BeforeEach
    void setUp() {
        aiSettingsService = mock(AiSettingsService.class);
        aiUsageGuard = mock(AiUsageGuard.class);
        openAiCompatibleClient = mock(OpenAiCompatibleClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        service = new QwenAiParseService(objectMapper, aiSettingsService, aiUsageGuard, openAiCompatibleClient);
        // 显式注入 @Value 字段（纯 unit test 不会走 Spring 注入）
        ReflectionTestUtils.setField(service, "aiMaxTokens", 8192);
        ReflectionTestUtils.setField(service, "mimoMaxTokens", 8192);
        ReflectionTestUtils.setField(service, "httpTimeoutSeconds", 240);
    }

    private AiCredentials qwenUserCreds() {
        return AiCredentials.builder()
                .keyMode(AiKeyMode.USER)
                .provider(AiProviderType.QWEN)
                .taskType(AiTaskType.VISION)
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .model("qwen-vl-plus")
                .apiKey(USER_KEY)
                .tokenField("max_tokens")
                .build();
    }

    private AiCredentials mimoUserCreds() {
        return AiCredentials.builder()
                .keyMode(AiKeyMode.USER)
                .provider(AiProviderType.MIMO)
                .taskType(AiTaskType.VISION)
                .baseUrl("https://api.mimo.example.com/v1")
                .model("mimo-vl-plus")
                .apiKey(USER_KEY)
                .tokenField("max_completion_tokens")
                .build();
    }

    private AiChatResponse response(String content) {
        return AiChatResponse.builder()
                .content(content)
                .provider("QWEN")
                .model("qwen-vl-plus")
                .statusCode(200)
                .build();
    }

    /** 一段最小的 PNG 字节（仅用于让 detectMimeType + Base64 编码能跑通，不真实解码图片）。 */
    private static final byte[] FAKE_PNG_BYTES =
            new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01};

    private static final String VALID_PARSE_JSON = """
            {"passages":["p"],"questions":[{"questionNumber":1,"type":"fill","text":"q","answer":"a","explanation":"e","locatorText":"p"}]}
            """;

    // ── 1. parseImages 走 VISION credentials 并 markSuccess ───────────────────

    @Test
    void parseImagesShouldUseVisionCredentialsAndMarkSuccess() throws Exception {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.VISION)).thenReturn(qwenUserCreds());
        when(openAiCompatibleClient.chat(any(AiChatRequest.class)))
                .thenReturn(response(VALID_PARSE_JSON));

        Map<String, Object> result = service.parseImages(USER_ID, List.of(FAKE_PNG_BYTES), List.of("a.png"), "reading");

        assertEquals(List.of("p"), result.get("passages"));
        assertEquals(1, ((List<?>) result.get("questions")).size());
        verify(aiUsageGuard).checkBeforeCall(USER_ID, AiFeature.EXAM_PRECISE_PARSE, AiKeyMode.USER, "QWEN");
        verify(aiUsageGuard).markSuccess(USER_ID, AiFeature.EXAM_PRECISE_PARSE, AiKeyMode.USER, "QWEN");
        verify(aiUsageGuard, never()).markFailure(any(), any(), any(), any(), any());

        // 校验发给 provider 的请求形态
        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(openAiCompatibleClient).chat(captor.capture());
        AiChatRequest sent = captor.getValue();
        assertEquals(AiTaskType.VISION, sent.getCredentials().getTaskType());
        assertEquals(AiProviderType.QWEN, sent.getCredentials().getProvider());
        assertEquals("qwen-vl-plus", sent.getCredentials().getModel());
        assertTrue(Boolean.TRUE.equals(sent.getJsonMode()));
        assertEquals(8192, sent.getMaxTokens());

        // 第二条 message 是 user 多模态消息
        AiChatMessage userMsg = sent.getMessages().get(1);
        assertTrue(userMsg.getContent() instanceof List<?>, "user content should be multimodal List");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) userMsg.getContent();
        boolean hasImageUrl = content.stream()
                .anyMatch(m -> "image_url".equals(m.get("type")));
        assertTrue(hasImageUrl, "user content should contain image_url entry");
    }

    // ── 2. parseImages 使用 MIMO credentials 时 tokenField=max_completion_tokens ─

    @Test
    void parseImagesShouldUseMimoMaxCompletionTokenFieldViaCredentials() throws Exception {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.VISION)).thenReturn(mimoUserCreds());
        when(openAiCompatibleClient.chat(any(AiChatRequest.class)))
                .thenReturn(response(VALID_PARSE_JSON));

        service.parseImages(USER_ID, List.of(FAKE_PNG_BYTES), List.of("a.png"), "reading");

        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(openAiCompatibleClient).chat(captor.capture());
        AiChatRequest sent = captor.getValue();
        assertEquals(AiProviderType.MIMO, sent.getCredentials().getProvider());
        assertEquals("max_completion_tokens", sent.getCredentials().getTokenField());
        verify(aiUsageGuard).markSuccess(USER_ID, AiFeature.EXAM_PRECISE_PARSE, AiKeyMode.USER, "MIMO");
    }

    // ── 3. parseImages 返回非法 JSON 时 markFailure，异常不含 Key ───────────────

    @Test
    void parseImagesShouldMarkFailureWhenResponseJsonInvalid() throws Exception {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.VISION)).thenReturn(qwenUserCreds());
        when(openAiCompatibleClient.chat(any(AiChatRequest.class)))
                .thenReturn(response("not json"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.parseImages(USER_ID, List.of(FAKE_PNG_BYTES), List.of("a.png"), "reading"));

        verify(aiUsageGuard).checkBeforeCall(USER_ID, AiFeature.EXAM_PRECISE_PARSE, AiKeyMode.USER, "QWEN");
        verify(aiUsageGuard).markFailure(eq(USER_ID), eq(AiFeature.EXAM_PRECISE_PARSE), eq(AiKeyMode.USER), eq("QWEN"), any());
        verify(aiUsageGuard, never()).markSuccess(any(), any(), any(), any());
        assertFalse(ex.getMessage().contains(USER_KEY));
        assertFalse(ex.getMessage().contains("sk-"));
        assertTrue(ex.getMessage().contains("AI 精准解析暂时不可用"));
    }

    // ── 4. parseImages client 抛异常（含测试 key）时 markFailure，异常不含 key ──

    @Test
    void parseImagesShouldMarkFailureWhenClientThrows() throws Exception {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.VISION)).thenReturn(qwenUserCreds());
        // 模拟 provider 抛出的异常中意外含 key（确保即便如此也不会透传到前端）
        when(openAiCompatibleClient.chat(any(AiChatRequest.class)))
                .thenThrow(new RuntimeException("provider error body with " + USER_KEY));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.parseImages(USER_ID, List.of(FAKE_PNG_BYTES), List.of("a.png"), "reading"));

        verify(aiUsageGuard).checkBeforeCall(USER_ID, AiFeature.EXAM_PRECISE_PARSE, AiKeyMode.USER, "QWEN");
        verify(aiUsageGuard).markFailure(eq(USER_ID), eq(AiFeature.EXAM_PRECISE_PARSE), eq(AiKeyMode.USER), eq("QWEN"), any());
        verify(aiUsageGuard, never()).markSuccess(any(), any(), any(), any());
        assertFalse(ex.getMessage().contains(USER_KEY));
        assertFalse(ex.getMessage().contains("sk-"));
        assertTrue(ex.getMessage().contains("AI 精准解析暂时不可用"));
    }

    // ── 5. isConfigured(userId) 由 AiSettingsService.resolve(VISION) 决定 ──────

    @Test
    void isConfiguredShouldUseAiSettingsServiceVisionResolve() {
        // resolve 成功 → true
        when(aiSettingsService.resolve(USER_ID, AiTaskType.VISION)).thenReturn(qwenUserCreds());
        assertTrue(service.isConfigured(USER_ID));

        // resolve 抛配置异常 → false
        when(aiSettingsService.resolve(99L, AiTaskType.VISION))
                .thenThrow(new IllegalStateException("User AI provider is not configured for taskType=VISION"));
        assertFalse(service.isConfigured(99L));

        // 不应再依赖旧 qwen.api-key 判断（无参 isConfigured 已 deprecated 返回 false）
        assertFalse(service.isConfigured());
    }
}
