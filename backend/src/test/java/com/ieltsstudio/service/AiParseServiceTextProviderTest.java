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
 * Phase 5A：文本类 AI 功能接入新 Provider 架构的单元测试。
 *
 * <p>不真实访问 DeepSeek / Qwen / MiMO：
 * {@link AiSettingsService} / {@link AiUsageGuard} / {@link OpenAiCompatibleClient}
 * 均用 Mockito mock；{@link ObjectMapper} 用真实实例以便验证 JSON 解析行为。</p>
 *
 * <p>测试字符串：{@code sk-user-test-key-999}（非真实 Key）。</p>
 */
class AiParseServiceTextProviderTest {

    private static final Long USER_ID = 42L;
    private static final String USER_KEY = "sk-user-test-key-999";

    private AiSettingsService aiSettingsService;
    private AiUsageGuard aiUsageGuard;
    private OpenAiCompatibleClient openAiCompatibleClient;
    private AiParseService service;

    @BeforeEach
    void setUp() {
        aiSettingsService = mock(AiSettingsService.class);
        aiUsageGuard = mock(AiUsageGuard.class);
        openAiCompatibleClient = mock(OpenAiCompatibleClient.class);
        service = new AiParseService(new ObjectMapper(), aiSettingsService, aiUsageGuard, openAiCompatibleClient);
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

    // ── 1. 写作评分走 USER credentials 并 markSuccess ──────────────────────────

    @Test
    void gradeWritingShouldUseUserCredentialsAndMarkSuccess() throws Exception {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.TEXT)).thenReturn(userCreds());
        when(openAiCompatibleClient.chat(any(AiChatRequest.class)))
                .thenReturn(response("{\"band\":7.0,\"bandDescription\":\"良好\"}"));

        Map<String, Object> result = service.gradeWriting(USER_ID, "Task 2 prompt",
                "This is a test essay with enough words.", 250);

        assertEquals(7.0, ((Number) result.get("band")).doubleValue());
        verify(aiUsageGuard).checkBeforeCall(USER_ID, AiFeature.WRITING_GRADE, AiKeyMode.USER, "DEEPSEEK");
        verify(aiUsageGuard).markSuccess(USER_ID, AiFeature.WRITING_GRADE, AiKeyMode.USER, "DEEPSEEK");
        verify(aiUsageGuard, never()).markFailure(any(), any(), any(), any(), any());
    }

    // ── 2. 翻译走 BUILTIN credentials 并 markSuccess ───────────────────────────

    @Test
    void translateShouldUseBuiltinCredentialsAndMarkSuccess() throws Exception {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.TEXT)).thenReturn(builtinCreds());
        when(openAiCompatibleClient.chat(any(AiChatRequest.class)))
                .thenReturn(response("{\"translation\":\"测试翻译\",\"notes\":\"\"}"));

        Map<String, Object> result = service.translateWithContext(USER_ID, "some passage", "selected text");

        assertEquals("测试翻译", result.get("translation"));
        verify(aiUsageGuard).checkBeforeCall(USER_ID, AiFeature.TRANSLATE, AiKeyMode.BUILTIN, "DEEPSEEK");
        verify(aiUsageGuard).markSuccess(USER_ID, AiFeature.TRANSLATE, AiKeyMode.BUILTIN, "DEEPSEEK");
        verify(aiUsageGuard, never()).markFailure(any(), any(), any(), any(), any());
    }

    // ── 3. AI Chat 失败时 markFailure，异常 message 不含 API Key ────────────────

    @Test
    void chatFailureShouldMarkFailureAndNotLeakKey() throws Exception {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.TEXT)).thenReturn(userCreds());
        // 模拟 provider 抛出的异常中意外含 key（确保即便如此也不会透传到前端）
        when(openAiCompatibleClient.chat(any(AiChatRequest.class)))
                .thenThrow(new RuntimeException("provider error body with " + USER_KEY));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.chatWithContext(USER_ID, "ctx", "why?" + USER_KEY));

        verify(aiUsageGuard).checkBeforeCall(USER_ID, AiFeature.AI_CHAT, AiKeyMode.USER, "DEEPSEEK");
        verify(aiUsageGuard).markFailure(eq(USER_ID), eq(AiFeature.AI_CHAT), eq(AiKeyMode.USER), eq("DEEPSEEK"), any());
        verify(aiUsageGuard, never()).markSuccess(any(), any(), any(), any());
        // 异常 message 不含 API Key
        assertFalse(ex.getMessage().contains(USER_KEY));
        assertFalse(ex.getMessage().contains("sk-"));
        assertTrue(ex.getMessage().contains("AI 服务暂时不可用"));
    }

    // ── 3b. Phase 5A-polish：provider 返回但内容不可用时不记 markSuccess ─────────
    //
    // provider 调用本身成功，但返回内容不是合法 JSON / 为空时，
    // 应走 markFailure，且不应先 markSuccess（避免一次调用同时记成功与失败、失败仍被扣费）。

    @Test
    void gradeWritingShouldMarkFailureWhenResponseJsonInvalid() throws Exception {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.TEXT)).thenReturn(userCreds());
        when(openAiCompatibleClient.chat(any(AiChatRequest.class)))
                .thenReturn(response("not json"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.gradeWriting(USER_ID, "prompt", "This is a valid essay content.", 250));

        verify(aiUsageGuard).checkBeforeCall(USER_ID, AiFeature.WRITING_GRADE, AiKeyMode.USER, "DEEPSEEK");
        verify(aiUsageGuard).markFailure(eq(USER_ID), eq(AiFeature.WRITING_GRADE), eq(AiKeyMode.USER), eq("DEEPSEEK"), any());
        verify(aiUsageGuard, never()).markSuccess(any(), any(), any(), any());
        // 异常 message 不含 API Key
        assertFalse(ex.getMessage().contains(USER_KEY));
        assertFalse(ex.getMessage().contains("sk-"));
    }

    @Test
    void translateShouldMarkFailureWhenResponseJsonInvalid() throws Exception {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.TEXT)).thenReturn(userCreds());
        when(openAiCompatibleClient.chat(any(AiChatRequest.class)))
                .thenReturn(response("not json"));

        assertThrows(RuntimeException.class,
                () -> service.translateWithContext(USER_ID, "passage", "selected text"));

        verify(aiUsageGuard).checkBeforeCall(USER_ID, AiFeature.TRANSLATE, AiKeyMode.USER, "DEEPSEEK");
        verify(aiUsageGuard).markFailure(eq(USER_ID), eq(AiFeature.TRANSLATE), eq(AiKeyMode.USER), eq("DEEPSEEK"), any());
        verify(aiUsageGuard, never()).markSuccess(any(), any(), any(), any());
    }

    @Test
    void translateShouldMarkFailureWhenResponseContentEmpty() throws Exception {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.TEXT)).thenReturn(userCreds());
        when(openAiCompatibleClient.chat(any(AiChatRequest.class)))
                .thenReturn(response(""));

        assertThrows(RuntimeException.class,
                () -> service.translateWithContext(USER_ID, "passage", "selected text"));

        verify(aiUsageGuard).markFailure(eq(USER_ID), eq(AiFeature.TRANSLATE), eq(AiKeyMode.USER), eq("DEEPSEEK"), any());
        verify(aiUsageGuard, never()).markSuccess(any(), any(), any(), any());
    }

    // ── 4. 输入校验 ────────────────────────────────────────────────────────────

    @Test
    void gradeWritingShouldRejectEmptyEssay() {
        assertThrows(IllegalArgumentException.class,
                () -> service.gradeWriting(USER_ID, "prompt", "", 250));
        assertThrows(IllegalArgumentException.class,
                () -> service.gradeWriting(USER_ID, "prompt", "   ", 250));
        assertThrows(IllegalArgumentException.class,
                () -> service.gradeWriting(USER_ID, "prompt", null, 250));
    }

    @Test
    void gradeWritingShouldRejectOverlongEssay() {
        String essay = "a".repeat(8001);
        assertThrows(IllegalArgumentException.class,
                () -> service.gradeWriting(USER_ID, "prompt", essay, 250));
    }

    @Test
    void translateShouldRejectEmptySelectedText() {
        assertThrows(IllegalArgumentException.class,
                () -> service.translateWithContext(USER_ID, "passage", ""));
        assertThrows(IllegalArgumentException.class,
                () -> service.translateWithContext(USER_ID, "passage", "   "));
        assertThrows(IllegalArgumentException.class,
                () -> service.translateWithContext(USER_ID, "passage", null));
    }

    @Test
    void translateShouldRejectOverlongSelectedText() {
        String s = "x".repeat(1001);
        assertThrows(IllegalArgumentException.class,
                () -> service.translateWithContext(USER_ID, "passage", s));
    }

    @Test
    void chatShouldRejectEmptyQuestion() {
        assertThrows(IllegalArgumentException.class,
                () -> service.chatWithContext(USER_ID, "ctx", ""));
        assertThrows(IllegalArgumentException.class,
                () -> service.chatWithContext(USER_ID, "ctx", "   "));
        assertThrows(IllegalArgumentException.class,
                () -> service.chatWithContext(USER_ID, "ctx", null));
    }

    @Test
    void chatShouldRejectOverlongQuestion() {
        String q = "q".repeat(2001);
        assertThrows(IllegalArgumentException.class,
                () -> service.chatWithContext(USER_ID, "ctx", q));
    }

    // ── 5. resolve 失败（Key 未配置）时不调用 markFailure（调用未发生） ──────────

    @Test
    void gradeWritingShouldNotMarkFailureWhenResolveFails() {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.TEXT))
                .thenThrow(new IllegalStateException("User AI API key is not configured for taskType=TEXT"));

        assertThrows(IllegalStateException.class,
                () -> service.gradeWriting(USER_ID, "prompt", "essay content here", 250));

        verify(aiUsageGuard, never()).markFailure(any(), any(), any(), any(), any());
        verify(aiUsageGuard, never()).markSuccess(any(), any(), any(), any());
    }
}
