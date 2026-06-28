package com.ieltsstudio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieltsstudio.ai.AiFeature;
import com.ieltsstudio.ai.AiKeyMode;
import com.ieltsstudio.ai.AiProviderType;
import com.ieltsstudio.ai.AiTaskType;
import com.ieltsstudio.ai.client.OpenAiCompatibleClient;
import com.ieltsstudio.ai.model.AiChatRequest;
import com.ieltsstudio.ai.model.AiChatResponse;
import com.ieltsstudio.ai.model.AiCredentials;
import com.ieltsstudio.ai.service.AiSettingsService;
import com.ieltsstudio.ai.service.AiUsageGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
 * Phase 5B：{@link ClozeService} 接入新 AI Provider 架构的单元测试。
 *
 * <p>不真实访问 DeepSeek / Qwen / MiMO：
 * {@link AiSettingsService} / {@link AiUsageGuard} / {@link OpenAiCompatibleClient}
 * 均用 Mockito mock；{@link ObjectMapper} 用真实实例以便验证 JSON 解析行为。</p>
 *
 * <p>测试字符串：{@code sk-user-test-key-999}（非真实 Key）。</p>
 */
class ClozeServiceProviderTest {

    private static final Long USER_ID = 42L;
    private static final String USER_KEY = "sk-user-test-key-999";

    private AiSettingsService aiSettingsService;
    private AiUsageGuard aiUsageGuard;
    private OpenAiCompatibleClient openAiCompatibleClient;
    private ClozeService service;

    @BeforeEach
    void setUp() {
        aiSettingsService = mock(AiSettingsService.class);
        aiUsageGuard = mock(AiUsageGuard.class);
        openAiCompatibleClient = mock(OpenAiCompatibleClient.class);
        service = new ClozeService(new ObjectMapper(), aiSettingsService, aiUsageGuard, openAiCompatibleClient);
    }

    private AiCredentials userCreds() {
        return AiCredentials.builder()
                .keyMode(AiKeyMode.USER)
                .provider(AiProviderType.DEEPSEEK)
                .taskType(AiTaskType.TEXT)
                .baseUrl("https://api.deepseek.com")
                .model("deepseek-chat")
                .apiKey(USER_KEY)
                .tokenField("max_tokens")
                .build();
    }

    private AiCredentials builtinCreds() {
        return AiCredentials.builder()
                .keyMode(AiKeyMode.BUILTIN)
                .provider(AiProviderType.DEEPSEEK)
                .taskType(AiTaskType.TEXT)
                .baseUrl("https://api.deepseek.com")
                .model("deepseek-chat")
                .apiKey("sk-builtin-test")
                .tokenField("max_tokens")
                .build();
    }

    private AiChatResponse response(String content) {
        return AiChatResponse.builder()
                .content(content)
                .provider("DEEPSEEK")
                .model("deepseek-chat")
                .statusCode(200)
                .build();
    }

    private static final String VALID_GENERATE_JSON = """
            {
              "title": "Test Passage",
              "passage": "This is a __(1)__ passage.",
              "blanks": [
                {"number": 1, "answer": "sample", "options": {"A":"sample","B":"other"}, "correctOption": "A"}
              ]
            }
            """;

    private static final String VALID_CHECK_JSON = """
            {
              "results": [
                {"number": 1, "correct": true, "correctAnswer": "sample", "userAnswer": "A", "explanation": "正确"}
              ],
              "score": 1,
              "total": 1,
              "summary": "做得不错"
            }
            """;

    // ── 1. generate 走 USER credentials 并 markSuccess ─────────────────────────

    @Test
    void generateShouldUseTextCredentialsAndMarkSuccess() throws Exception {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.TEXT)).thenReturn(userCreds());
        when(openAiCompatibleClient.chat(any(AiChatRequest.class)))
                .thenReturn(response(VALID_GENERATE_JSON));

        Map<String, Object> result = service.generate(USER_ID,
                List.of("sample"), List.of("样本"), "medium");

        assertEquals("Test Passage", result.get("title"));
        verify(aiUsageGuard).checkBeforeCall(USER_ID, AiFeature.CLOZE_GENERATE, AiKeyMode.USER, "DEEPSEEK");
        verify(aiUsageGuard).markSuccess(USER_ID, AiFeature.CLOZE_GENERATE, AiKeyMode.USER, "DEEPSEEK");
        verify(aiUsageGuard, never()).markFailure(any(), any(), any(), any(), any());
    }

    // ── 2. generate 返回非法 JSON 时 markFailure，异常不含 Key ─────────────────

    @Test
    void generateShouldMarkFailureWhenResponseJsonInvalid() throws Exception {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.TEXT)).thenReturn(userCreds());
        when(openAiCompatibleClient.chat(any(AiChatRequest.class)))
                .thenReturn(response("not json"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.generate(USER_ID, List.of("sample"), null, "medium"));

        verify(aiUsageGuard).checkBeforeCall(USER_ID, AiFeature.CLOZE_GENERATE, AiKeyMode.USER, "DEEPSEEK");
        verify(aiUsageGuard).markFailure(eq(USER_ID), eq(AiFeature.CLOZE_GENERATE), eq(AiKeyMode.USER), eq("DEEPSEEK"), any());
        verify(aiUsageGuard, never()).markSuccess(any(), any(), any(), any());
        assertFalse(ex.getMessage().contains(USER_KEY));
        assertFalse(ex.getMessage().contains("sk-"));
        assertTrue(ex.getMessage().contains("AI 服务暂时不可用"));
    }

    // ── 3. check 走 BUILTIN credentials 并 markSuccess ─────────────────────────

    @Test
    void checkShouldUseTextCredentialsAndMarkSuccess() throws Exception {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.TEXT)).thenReturn(builtinCreds());
        when(openAiCompatibleClient.chat(any(AiChatRequest.class)))
                .thenReturn(response(VALID_CHECK_JSON));

        Map<String, Object> result = service.check(USER_ID,
                "This is a passage.",
                List.of(Map.of("number", 1, "answer", "sample", "correctOption", "A",
                        "options", Map.of("A", "sample", "B", "other"))),
                Map.of("1", "A"));

        assertEquals(1, ((Number) result.get("score")).intValue());
        verify(aiUsageGuard).checkBeforeCall(USER_ID, AiFeature.CLOZE_CHECK, AiKeyMode.BUILTIN, "DEEPSEEK");
        verify(aiUsageGuard).markSuccess(USER_ID, AiFeature.CLOZE_CHECK, AiKeyMode.BUILTIN, "DEEPSEEK");
        verify(aiUsageGuard, never()).markFailure(any(), any(), any(), any(), any());
    }

    // ── 4. check client 抛异常（含测试 key）时 markFailure，异常不含 key ─────────

    @Test
    void checkShouldMarkFailureWhenClientThrows() throws Exception {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.TEXT)).thenReturn(userCreds());
        // 模拟 provider 抛出的异常中意外含 key（确保即便如此也不会透传到前端）
        when(openAiCompatibleClient.chat(any(AiChatRequest.class)))
                .thenThrow(new RuntimeException("provider error body with " + USER_KEY));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.check(USER_ID,
                        "This is a passage with " + USER_KEY,
                        List.of(Map.of("number", 1)),
                        Map.of("1", "A")));

        verify(aiUsageGuard).checkBeforeCall(USER_ID, AiFeature.CLOZE_CHECK, AiKeyMode.USER, "DEEPSEEK");
        verify(aiUsageGuard).markFailure(eq(USER_ID), eq(AiFeature.CLOZE_CHECK), eq(AiKeyMode.USER), eq("DEEPSEEK"), any());
        verify(aiUsageGuard, never()).markSuccess(any(), any(), any(), any());
        assertFalse(ex.getMessage().contains(USER_KEY));
        assertFalse(ex.getMessage().contains("sk-"));
        assertTrue(ex.getMessage().contains("AI 服务暂时不可用"));
    }

    // ── 5. 输入校验 ────────────────────────────────────────────────────────────

    @Test
    void generateShouldRejectEmptyWords() {
        assertThrows(IllegalArgumentException.class,
                () -> service.generate(USER_ID, null, null, "medium"));
        assertThrows(IllegalArgumentException.class,
                () -> service.generate(USER_ID, List.of(), null, "medium"));
    }

    @Test
    void generateShouldRejectTooManyWords() {
        List<String> words = List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k");
        assertThrows(IllegalArgumentException.class,
                () -> service.generate(USER_ID, words, null, "medium"));
    }

    @Test
    void checkShouldRejectEmptyPassage() {
        assertThrows(IllegalArgumentException.class,
                () -> service.check(USER_ID, null,
                        List.of(Map.of("number", 1)), Map.of("1", "A")));
        assertThrows(IllegalArgumentException.class,
                () -> service.check(USER_ID, "   ",
                        List.of(Map.of("number", 1)), Map.of("1", "A")));
    }

    @Test
    void checkShouldRejectOverlongPassage() {
        String passage = "x".repeat(6001);
        assertThrows(IllegalArgumentException.class,
                () -> service.check(USER_ID, passage,
                        List.of(Map.of("number", 1)), Map.of("1", "A")));
    }

    @Test
    void checkShouldRejectEmptyBlanks() {
        assertThrows(IllegalArgumentException.class,
                () -> service.check(USER_ID, "passage", null, Map.of("1", "A")));
        assertThrows(IllegalArgumentException.class,
                () -> service.check(USER_ID, "passage", List.of(), Map.of("1", "A")));
    }

    @Test
    void checkShouldRejectEmptyUserAnswers() {
        assertThrows(IllegalArgumentException.class,
                () -> service.check(USER_ID, "passage",
                        List.of(Map.of("number", 1)), null));
        assertThrows(IllegalArgumentException.class,
                () -> service.check(USER_ID, "passage",
                        List.of(Map.of("number", 1)), Map.of()));
    }

    // ── 6. check options 缺失时不 NPE，仍能正常调用 ─────────────────────────────

    @Test
    void checkShouldHandleMissingOptionsSafely() throws Exception {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.TEXT)).thenReturn(builtinCreds());
        when(openAiCompatibleClient.chat(any(AiChatRequest.class)))
                .thenReturn(response(VALID_CHECK_JSON));

        // blank 没有 options 字段，不应 NPE
        Map<String, Object> result = service.check(USER_ID,
                "This is a passage.",
                List.of(Map.of("number", 1, "answer", "sample", "correctOption", "A")),
                Map.of("1", "A"));

        assertEquals(1, ((Number) result.get("score")).intValue());
        verify(aiUsageGuard).markSuccess(USER_ID, AiFeature.CLOZE_CHECK, AiKeyMode.BUILTIN, "DEEPSEEK");
    }

    // ── 7. generate difficulty 非法时 fallback medium（不拒绝）──────────────────

    @Test
    void generateShouldFallbackDifficultyToMediumWhenInvalid() throws Exception {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.TEXT)).thenReturn(userCreds());
        when(openAiCompatibleClient.chat(any(AiChatRequest.class)))
                .thenReturn(response(VALID_GENERATE_JSON));

        // 非法 difficulty 不应抛异常，应 fallback 为 medium
        Map<String, Object> result = service.generate(USER_ID,
                List.of("sample"), null, "extreme");
        assertEquals("Test Passage", result.get("title"));
        verify(aiUsageGuard).markSuccess(USER_ID, AiFeature.CLOZE_GENERATE, AiKeyMode.USER, "DEEPSEEK");
    }

    // ── 8. resolve 失败时不调用 markFailure（调用未发生） ──────────────────────

    @Test
    void generateShouldNotMarkFailureWhenResolveFails() {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.TEXT))
                .thenThrow(new IllegalStateException("User AI API key is not configured for taskType=TEXT"));

        assertThrows(IllegalStateException.class,
                () -> service.generate(USER_ID, List.of("sample"), null, "medium"));

        verify(aiUsageGuard, never()).markFailure(any(), any(), any(), any(), any());
        verify(aiUsageGuard, never()).markSuccess(any(), any(), any(), any());
    }
}
