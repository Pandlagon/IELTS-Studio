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
 * Phase 5B：{@link AiParseService#generateWordEntries(Long, String)} 接入新 AI Provider 架构的单元测试。
 *
 * <p>不真实访问 DeepSeek / Qwen / MiMO：
 * {@link AiSettingsService} / {@link AiUsageGuard} / {@link OpenAiCompatibleClient}
 * 均用 Mockito mock；{@link ObjectMapper} 用真实实例以便验证 JSON 解析行为。</p>
 *
 * <p>测试字符串：{@code sk-user-test-key-999}（非真实 Key）。</p>
 */
class AiParseServiceWordGenerationProviderTest {

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

    private static final String VALID_WORD_ARRAY_JSON = """
            [
              {"word":"inspection","phonetic":"/ɪnˈspekʃn/","pos":"n.","posType":"n","meaning":"n. 检查；视察","example":"\\"Safety inspection.\\"","exampleTranslation":"安全检查。","rootMemory":"in-+spec-+ -tion"}
            ]
            """;

    // ── 1. 走 USER credentials 并 markSuccess ──────────────────────────────────

    @Test
    void generateWordEntriesShouldUseTextCredentialsAndMarkSuccess() throws Exception {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.TEXT)).thenReturn(userCreds());
        when(openAiCompatibleClient.chat(any(AiChatRequest.class)))
                .thenReturn(response(VALID_WORD_ARRAY_JSON));

        List<Map<String, Object>> result = service.generateWordEntries(USER_ID, "inspection");

        assertEquals(1, result.size());
        assertEquals("inspection", result.get(0).get("word"));
        verify(aiUsageGuard).checkBeforeCall(USER_ID, AiFeature.WORD_GENERATE, AiKeyMode.USER, "DEEPSEEK");
        verify(aiUsageGuard).markSuccess(USER_ID, AiFeature.WORD_GENERATE, AiKeyMode.USER, "DEEPSEEK");
        verify(aiUsageGuard, never()).markFailure(any(), any(), any(), any(), any());
    }

    // ── 2. 返回非法 JSON 时 markFailure，不 markSuccess ────────────────────────

    @Test
    void generateWordEntriesShouldMarkFailureWhenJsonInvalid() throws Exception {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.TEXT)).thenReturn(builtinCreds());
        when(openAiCompatibleClient.chat(any(AiChatRequest.class)))
                .thenReturn(response("not json"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.generateWordEntries(USER_ID, "inspection"));

        verify(aiUsageGuard).checkBeforeCall(USER_ID, AiFeature.WORD_GENERATE, AiKeyMode.BUILTIN, "DEEPSEEK");
        verify(aiUsageGuard).markFailure(eq(USER_ID), eq(AiFeature.WORD_GENERATE), eq(AiKeyMode.BUILTIN), eq("DEEPSEEK"), any());
        verify(aiUsageGuard, never()).markSuccess(any(), any(), any(), any());
        // 异常 message 不含 API Key
        assertFalse(ex.getMessage().contains(USER_KEY));
        assertFalse(ex.getMessage().contains("sk-"));
    }

    // ── 3. 空输入拒绝 ──────────────────────────────────────────────────────────

    @Test
    void generateWordEntriesShouldRejectEmptyInput() {
        assertThrows(IllegalArgumentException.class,
                () -> service.generateWordEntries(USER_ID, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.generateWordEntries(USER_ID, ""));
        assertThrows(IllegalArgumentException.class,
                () -> service.generateWordEntries(USER_ID, "   "));

        // 输入校验失败时不应进入 provider 调用链
        verify(aiSettingsService, never()).resolve(any(), any());
        verify(aiUsageGuard, never()).markSuccess(any(), any(), any(), any());
        verify(aiUsageGuard, never()).markFailure(any(), any(), any(), any(), any());
    }

    // ── 4. 超长输入截断到 3000 字符 ─────────────────────────────────────────────

    @Test
    void generateWordEntriesShouldTruncateLongInput() throws Exception {
        when(aiSettingsService.resolve(USER_ID, AiTaskType.TEXT)).thenReturn(userCreds());
        when(openAiCompatibleClient.chat(any(AiChatRequest.class)))
                .thenReturn(response(VALID_WORD_ARRAY_JSON));

        // 构造超过 3000 字符的输入，并在末尾放一个唯一标记
        String marker = "UNIQUE_MARKER_ZZZ";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3500; i++) {
            sb.append('x');
        }
        sb.append(marker);
        String longInput = sb.toString();
        assertTrue(longInput.length() > 3000);

        service.generateWordEntries(USER_ID, longInput);

        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(openAiCompatibleClient).chat(captor.capture());
        AiChatRequest sent = captor.getValue();

        // 找到 user 消息内容
        String userContent = null;
        for (AiChatMessage m : sent.getMessages()) {
            if ("user".equals(m.getRole()) && m.getContent() instanceof String s) {
                userContent = s;
                break;
            }
        }
        assertTrue(userContent != null, "user message should be present");
        // 截断后 user message 不应包含原输入全文（>3000 字符）
        assertFalse(userContent.contains(marker),
                "user message 不应包含被截断掉的部分（marker 位于 3500 字符之后）");
        assertTrue(userContent.length() <= 3000,
                "user message 应被截断到 3000 字符以内，实际=" + userContent.length());
    }
}
