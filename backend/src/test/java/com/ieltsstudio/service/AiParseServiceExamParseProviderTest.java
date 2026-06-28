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
 * Phase 5C-1：{@link AiParseService} 普通文本试卷解析接入新 AI Provider 架构的单元测试。
 *
 * <p>不真实访问 DeepSeek / Qwen / MiMO：
 * {@link AiSettingsService} / {@link AiUsageGuard} / {@link OpenAiCompatibleClient}
 * 均用 Mockito mock；{@link ObjectMapper} 用真实实例以便验证 JSON 解析行为。</p>
 *
 * <p>测试字符串：{@code sk-user-test-key-999}（非真实 Key）。</p>
 */
class AiParseServiceExamParseProviderTest {

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

    private AiChatResponse response(String content) {
        return AiChatResponse.builder()
                .content(content)
                .provider("DEEPSEEK")
                .model("deepseek-chat")
                .statusCode(200)
                .build();
    }

    /** 一段长度 >= 80 的普通文本，足以通过 parseWithAi 的最小长度判断。 */
    private static final String VALID_RAW_TEXT =
            "READING PASSAGE 1\n\n" + "This is a sample IELTS reading passage used only for unit testing. ".repeat(3);

    private static final String VALID_PARSE_JSON = """
            {"passages":["p"],"questions":[{"questionNumber":1,"type":"fill","text":"q","answer":"a","explanation":"e","locatorText":"sample IELTS"}]}
            """;

    // ── 1. parseWithAi 走 USER credentials 并 markSuccess ──────────────────────

    @Test
    void parseWithAiShouldUseTextCredentialsAndMarkSuccess() throws Exception {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.TEXT)).thenReturn(userCreds());
        when(openAiCompatibleClient.chat(any(AiChatRequest.class)))
                .thenReturn(response(VALID_PARSE_JSON));

        Map<String, Object> result = service.parseWithAi(USER_ID, VALID_RAW_TEXT);

        assertEquals(List.of("p"), result.get("passages"));
        assertEquals(1, ((List<?>) result.get("questions")).size());
        verify(aiUsageGuard).checkBeforeCall(USER_ID, AiFeature.EXAM_PARSE, AiKeyMode.USER, "DEEPSEEK");
        verify(aiUsageGuard).markSuccess(USER_ID, AiFeature.EXAM_PARSE, AiKeyMode.USER, "DEEPSEEK");
        verify(aiUsageGuard, never()).markFailure(any(), any(), any(), any(), any());
    }

    // ── 2. parseWithAi 返回非法 JSON 时 markFailure，异常不含 Key ───────────────

    @Test
    void parseWithAiShouldMarkFailureWhenJsonInvalid() throws Exception {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.TEXT)).thenReturn(userCreds());
        when(openAiCompatibleClient.chat(any(AiChatRequest.class)))
                .thenReturn(response("not json"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.parseWithAi(USER_ID, VALID_RAW_TEXT));

        verify(aiUsageGuard).checkBeforeCall(USER_ID, AiFeature.EXAM_PARSE, AiKeyMode.USER, "DEEPSEEK");
        verify(aiUsageGuard).markFailure(eq(USER_ID), eq(AiFeature.EXAM_PARSE), eq(AiKeyMode.USER), eq("DEEPSEEK"), any());
        verify(aiUsageGuard, never()).markSuccess(any(), any(), any(), any());
        assertFalse(ex.getMessage().contains(USER_KEY));
        assertFalse(ex.getMessage().contains("sk-"));
        assertTrue(ex.getMessage().contains("AI 服务暂时不可用"));
    }

    // ── 3. workflowStep1B 使用 EXAM_PARSE 并在 JSON 解析成功后 markSuccess ──────

    @Test
    void workflowStep1BShouldUseExamParseFeature() throws Exception {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.TEXT)).thenReturn(userCreds());
        when(openAiCompatibleClient.chat(any(AiChatRequest.class)))
                .thenReturn(response("""
                        {"questionGroups":[{"range":"1-3","type":"fill","questions":["q1","q2","q3"]}]}
                        """));

        Map<String, Object> result = service.workflowStep1B(USER_ID, VALID_RAW_TEXT);

        assertEquals(1, ((List<?>) result.get("questionGroups")).size());
        verify(aiUsageGuard).checkBeforeCall(USER_ID, AiFeature.EXAM_PARSE, AiKeyMode.USER, "DEEPSEEK");
        verify(aiUsageGuard).markSuccess(USER_ID, AiFeature.EXAM_PARSE, AiKeyMode.USER, "DEEPSEEK");
        verify(aiUsageGuard, never()).markFailure(any(), any(), any(), any(), any());
    }

    // ── 4. workflowStep2 client 抛异常（含测试 key）时 markFailure，异常不含 key ─

    @Test
    void workflowStep2ShouldMarkFailureWhenClientThrows() throws Exception {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.TEXT)).thenReturn(userCreds());
        // 模拟 provider 抛出的异常中意外含 key（确保即便如此也不会透传到前端）
        when(openAiCompatibleClient.chat(any(AiChatRequest.class)))
                .thenThrow(new RuntimeException("provider error body with " + USER_KEY));

        Map<String, Object> group = new LinkedHashMap<>();
        group.put("range", "1-3");
        group.put("type", "fill");
        group.put("questions", List.of("q1", "q2", "q3"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.workflowStep2(USER_ID, "passage text " + USER_KEY, group));

        verify(aiUsageGuard).checkBeforeCall(USER_ID, AiFeature.EXAM_PARSE, AiKeyMode.USER, "DEEPSEEK");
        verify(aiUsageGuard).markFailure(eq(USER_ID), eq(AiFeature.EXAM_PARSE), eq(AiKeyMode.USER), eq("DEEPSEEK"), any());
        verify(aiUsageGuard, never()).markSuccess(any(), any(), any(), any());
        assertFalse(ex.getMessage().contains(USER_KEY));
        assertFalse(ex.getMessage().contains("sk-"));
        assertTrue(ex.getMessage().contains("AI 服务暂时不可用"));
    }

    // ── 5. parseWithAi 文本过短时拒绝，不调用 resolve / chat ───────────────────

    @Test
    void parseWithAiShouldRejectTooShortText() throws Exception {
        String tooShort = "short text";

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.parseWithAi(USER_ID, tooShort));

        verify(aiSettingsService, never()).resolve(any(), any());
        verify(openAiCompatibleClient, never()).chat(any(AiChatRequest.class));
        verify(aiUsageGuard, never()).checkBeforeCall(any(), any(), any());
        verify(aiUsageGuard, never()).markSuccess(any(), any(), any(), any());
        verify(aiUsageGuard, never()).markFailure(any(), any(), any(), any(), any());
        assertTrue(ex.getMessage().contains("PDF文字提取内容过少"));
    }

    // ── 6. parseWithAi 超长文本会被截断（user message 不含截断后的尾部 marker）──

    @Test
    void parseWithAiShouldTruncateLongText() throws Exception {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.TEXT)).thenReturn(userCreds());
        when(openAiCompatibleClient.chat(any(AiChatRequest.class)))
                .thenReturn(response(VALID_PARSE_JSON));

        // 构造一段长度超过 TEXT_MAX_CHARS(12000) 的文本，并在尾部放置唯一 marker
        // 截断后尾部 marker 不应出现在发给 provider 的 user message 中
        String tailMarker = "UNIQUE_TAIL_MARKER_SHOULD_BE_TRUNCATED_AWAY";
        StringBuilder sb = new StringBuilder();
        // 头部加上 READING PASSAGE 1 让 findTestContent 能识别
        sb.append("READING PASSAGE 1\n\n");
        while (sb.length() < 12000) {
            sb.append("This is filler content for the long text truncation test. ");
        }
        sb.append(tailMarker);
        String longText = sb.toString();
        assertTrue(longText.length() > 12000, "test fixture should exceed TEXT_MAX_CHARS");

        service.parseWithAi(USER_ID, longText);

        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(openAiCompatibleClient).chat(captor.capture());
        AiChatRequest sent = captor.getValue();
        // 第二条 message 是 user message
        AiChatMessage userMsg = sent.getMessages().get(1);
        Object contentObj = userMsg.getContent();
        String userContent = contentObj == null ? "" : contentObj.toString();
        assertFalse(userContent.contains(tailMarker),
                "truncated tail marker must not be sent to provider");
        assertTrue(userContent.contains("...[截断]"),
                "truncated input should end with the truncation marker");
    }
}
