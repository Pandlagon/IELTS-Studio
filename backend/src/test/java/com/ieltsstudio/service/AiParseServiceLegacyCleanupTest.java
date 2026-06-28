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
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
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
 * Phase 5C-3：Legacy AI 调用清理后的回归测试。
 *
 * <p>覆盖：</p>
 * <ol>
 *   <li>{@code generateWritingGuidance(userId, ...)} 走新 Provider 架构 + markSuccess</li>
 *   <li>{@code generateWritingGuidance} JSON 解析失败时 markFailure，不 markSuccess</li>
 *   <li>{@code extractHeadingsWithAi}（经 {@code postProcess(userId, ...)} 触发）走新架构 + markSuccess</li>
 *   <li>{@code extractHeadingsWithAi} 失败时 markFailure，返回空 map 不抛异常，不泄露 key</li>
 *   <li>旧 {@code postProcess(parsed, rawText)}（无 userId）不触发 AI fallback</li>
 * </ol>
 *
 * <p>不真实访问 DeepSeek / Qwen / MiMO：
 * {@link AiSettingsService} / {@link AiUsageGuard} / {@link OpenAiCompatibleClient}
 * 均用 Mockito mock；{@link ObjectMapper} 用真实实例。</p>
 *
 * <p>测试 Key：{@code sk-user-test-key-999}（非真实 Key）。</p>
 */
class AiParseServiceLegacyCleanupTest {

    private static final Long USER_ID = 99L;
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

    private AiChatResponse response(String content) {
        return AiChatResponse.builder()
                .content(content)
                .provider("DEEPSEEK")
                .model("deepseek-chat")
                .statusCode(200)
                .build();
    }

    /** 构造一段足够长的写作题原文（>= 40 字符）。 */
    private static final String WRITING_TEXT = """
            WRITING TASK 2
            You should spend about 40 minutes on this task.
            Write about the following topic:
            Some people believe that universities should focus on academic subjects.
            Discuss both views and give your opinion.
            Write at least 250 words.""";

    // ── 1. generateWritingGuidance 走 USER credentials 并 markSuccess ─────────

    @Test
    void generateWritingGuidanceShouldUseTextCredentialsAndMarkSuccess() throws Exception {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.TEXT)).thenReturn(userCreds());
        when(openAiCompatibleClient.chat(any(AiChatRequest.class)))
                .thenReturn(response("{\"taskType\":\"Task2\",\"wordLimit\":250,"
                        + "\"answer\":\"立场：I believe...\","
                        + "\"explanation\":\"TA: 覆盖论点.\","
                        + "\"locatorText\":\"universities should focus\"}"));

        Map<String, Object> result = service.generateWritingGuidance(USER_ID, WRITING_TEXT);

        // 返回结果包含 taskType / answer
        assertEquals("Task2", result.get("taskType"));
        assertEquals(250, ((Number) result.get("wordLimit")).intValue());
        assertTrue(((String) result.get("answer")).contains("立场"));

        // 走 USER credentials
        verify(aiSettingsService).resolve(USER_ID, AiTaskType.TEXT);
        // checkBeforeCall + markSuccess，不 markFailure
        verify(aiUsageGuard).checkBeforeCall(USER_ID, AiFeature.WRITING_GUIDANCE, AiKeyMode.USER, "DEEPSEEK");
        verify(aiUsageGuard).markSuccess(USER_ID, AiFeature.WRITING_GUIDANCE, AiKeyMode.USER, "DEEPSEEK");
        verify(aiUsageGuard, never()).markFailure(any(), any(), any(), any(), any());

        // 请求体携带 USER credentials（jsonMode=true, maxTokens=600）
        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(openAiCompatibleClient).chat(captor.capture());
        AiChatRequest req = captor.getValue();
        assertEquals(AiKeyMode.USER, req.getCredentials().getKeyMode());
        assertEquals(600, req.getMaxTokens());
        assertTrue(Boolean.TRUE.equals(req.getJsonMode()));
    }

    // ── 2. generateWritingGuidance JSON 解析失败时 markFailure ────────────────

    @Test
    void generateWritingGuidanceShouldMarkFailureWhenJsonInvalid() throws Exception {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.TEXT)).thenReturn(userCreds());
        when(openAiCompatibleClient.chat(any(AiChatRequest.class)))
                .thenReturn(response("not json"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.generateWritingGuidance(USER_ID, WRITING_TEXT));

        // markFailure，不 markSuccess
        verify(aiUsageGuard).markFailure(eq(USER_ID), eq(AiFeature.WRITING_GUIDANCE),
                eq(AiKeyMode.USER), eq("DEEPSEEK"), any());
        verify(aiUsageGuard, never()).markSuccess(any(), any(), any(), any());

        // 异常 message 不含 key（provider 错误已脱敏）
        String msg = ex.getMessage() == null ? "" : ex.getMessage();
        assertFalse(msg.contains(USER_KEY), "exception message must not contain key");
        assertFalse(msg.contains("sk-"), "exception message must not contain key fragment");
    }

    // ── 3. extractHeadingsWithAi 经 postProcess(userId, ...) 触发，markSuccess ─

    @Test
    void extractHeadingsWithAiShouldUseTextCredentialsAndReturnMap() throws Exception {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.TEXT)).thenReturn(userCreds());
        when(openAiCompatibleClient.chat(any(AiChatRequest.class)))
                .thenReturn(response("{\"i\":\"A successful exercise\","
                        + "\"ii\":\"Avoiding overcrowding\","
                        + "\"iii\":\"A new approach\"}"));

        // 构造 parsed：mcq 题目 options 为范围字符串 "i-viii"
        Map<String, Object> parsed = new LinkedHashMap<>();
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("questionNumber", 1);
        q.put("type", "mcq");
        q.put("options", "i-viii");
        parsed.put("questions", List.of(q));
        // rawText 不含 HEADING_LINE_PATTERN 能匹配的行 → regex 抽取 <3，触发 AI fallback
        String rawText = "Questions 1-5\nChoose the correct heading for each paragraph.";

        service.postProcess(USER_ID, parsed, rawText);

        // options 应被替换为完整 heading map
        @SuppressWarnings("unchecked")
        Map<String, Object> opts = (Map<String, Object>) q.get("options");
        assertTrue(opts.containsKey("i"));
        assertEquals("A successful exercise", opts.get("i"));

        // 走新架构：resolve + checkBeforeCall + markSuccess，不 markFailure
        verify(aiSettingsService).resolve(USER_ID, AiTaskType.TEXT);
        verify(aiUsageGuard).checkBeforeCall(USER_ID, AiFeature.HEADING_EXTRACT, AiKeyMode.USER, "DEEPSEEK");
        verify(aiUsageGuard).markSuccess(USER_ID, AiFeature.HEADING_EXTRACT, AiKeyMode.USER, "DEEPSEEK");
        verify(aiUsageGuard, never()).markFailure(any(), any(), any(), any(), any());
    }

    // ── 4. extractHeadingsWithAi 失败时 markFailure，返回空 map 不泄露 key ─────

    @Test
    void extractHeadingsWithAiFailureShouldReturnLocalFallbackWithoutLeakingKey() throws Exception {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.TEXT)).thenReturn(userCreds());
        // client 抛异常（异常 message 中含测试 key，模拟 provider 错误体泄露）
        when(openAiCompatibleClient.chat(any(AiChatRequest.class)))
                .thenThrow(new RuntimeException("provider error: " + USER_KEY + " unauthorized"));

        // 构造同上：触发 AI fallback
        Map<String, Object> parsed = new LinkedHashMap<>();
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("questionNumber", 1);
        q.put("type", "mcq");
        q.put("options", "i-viii");
        parsed.put("questions", List.of(q));
        String rawText = "Questions 1-5\nChoose the correct heading for each paragraph.";

        // 不应抛出 provider 原始异常（extractHeadingsWithAi 失败时返回 Map.of()）
        service.postProcess(USER_ID, parsed, rawText);

        // markFailure，不 markSuccess
        verify(aiUsageGuard).markFailure(eq(USER_ID), eq(AiFeature.HEADING_EXTRACT),
                eq(AiKeyMode.USER), eq("DEEPSEEK"), any());
        verify(aiUsageGuard, never()).markSuccess(any(), any(), any(), any());

        // 最终 options 不含 key（fallback 后 options 可能仍是字符串 "i-viii"，
        // 也可能被替换为 map —— 失败时 headingMap 为空，fixRangeOptions 不替换）
        Object optsObj = q.get("options");
        // 关键断言：任何情况下 options 都不含 key
        if (optsObj instanceof Map<?, ?> optsMap) {
            for (Object v : optsMap.values()) {
                String s = String.valueOf(v);
                assertFalse(s.contains(USER_KEY), "options must not contain key");
                assertFalse(s.contains("sk-"), "options must not contain key fragment");
            }
        } else if (optsObj != null) {
            String s = String.valueOf(optsObj);
            assertFalse(s.contains(USER_KEY), "options must not contain key");
            assertFalse(s.contains("sk-"), "options must not contain key fragment");
        }
    }

    // ── 5. 旧 postProcess(parsed, rawText) 不触发 AI fallback ─────────────────

    @Test
    void oldPostProcessShouldNotTriggerAiFallback() throws Exception {
        // 不 stub aiSettingsService.resolve / openAiCompatibleClient.chat
        // 如果旧 postProcess 误触发 AI fallback，会因 mock 返回 null 而抛 NPE，或被 verify never 捕获

        Map<String, Object> parsed = new LinkedHashMap<>();
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("questionNumber", 1);
        q.put("type", "mcq");
        q.put("options", "i-viii");
        parsed.put("questions", List.of(q));
        String rawText = "Questions 1-5\nChoose the correct heading for each paragraph.";

        // 旧 postProcess（无 userId）应只做 regex / local fixes，不调用 AI
        service.postProcess(parsed, rawText);

        verify(aiSettingsService, never()).resolve(any(), any());
        verify(openAiCompatibleClient, never()).chat(any());
        verify(aiUsageGuard, never()).checkBeforeCall(any(), any(), any());
        verify(aiUsageGuard, never()).markSuccess(any(), any(), any(), any());
        verify(aiUsageGuard, never()).markFailure(any(), any(), any(), any(), any());
    }
}
