package com.ieltsstudio.dto.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 设置 DTO 安全性测试。
 *
 * <p>目标：确认 {@link AiProviderConfigRequest} 与 {@link AiSettingsUpdateRequest}
 * 的 {@code toString()} 不会输出明文 {@code apiKey}（包括嵌套 text / vision 配置），
 * 避免明文 key 被打印到日志或异常栈。</p>
 *
 * <p>测试字符串：{@code sk-test-secret-123456} 与 {@code sk-vision-secret-654321}（均非真实 Key）。</p>
 */
class AiSettingsDtoSecurityTest {

    private static final String TEXT_KEY = "sk-test-secret-123456";
    private static final String VISION_KEY = "sk-vision-secret-654321";

    // ─── 必须测试 1：AiProviderConfigRequest.toString() 不泄露 apiKey ──────────

    @Test
    void providerConfigRequestToStringShouldNotLeakApiKey() {
        AiProviderConfigRequest req = new AiProviderConfigRequest();
        req.setProvider("DEEPSEEK");
        req.setBaseUrl("https://api.deepseek.com");
        req.setModel("deepseek-chat");
        req.setApiKey(TEXT_KEY);

        String s = req.toString();
        assertNotNull(s);
        // 不包含完整 key
        assertFalse(s.contains(TEXT_KEY), "toString 不能泄露完整 apiKey");
        // 不包含敏感片段
        assertFalse(s.contains("test-secret"), "toString 不能包含 apiKey 敏感片段");
        // 可以包含非敏感字段（证明 toString 确实生成了内容）
        assertTrue(s.contains("DEEPSEEK"), "toString 应当包含 provider");
        assertTrue(s.contains("deepseek-chat"), "toString 应当包含 model");
    }

    // ─── 必须测试 2：AiSettingsUpdateRequest.toString() 不间接泄露嵌套 apiKey ─

    @Test
    void settingsUpdateRequestToStringShouldNotLeakNestedApiKeys() {
        AiProviderConfigRequest text = new AiProviderConfigRequest();
        text.setProvider("DEEPSEEK");
        text.setBaseUrl("https://api.deepseek.com");
        text.setModel("deepseek-chat");
        text.setApiKey(TEXT_KEY);

        AiProviderConfigRequest vision = new AiProviderConfigRequest();
        vision.setProvider("QWEN");
        vision.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        vision.setModel("qwen3.6-plus");
        vision.setApiKey(VISION_KEY);

        AiSettingsUpdateRequest req = new AiSettingsUpdateRequest();
        req.setKeyMode("USER");
        req.setText(text);
        req.setVision(vision);

        String s = req.toString();
        assertNotNull(s);
        // 不包含 text / vision 的完整 key
        assertFalse(s.contains(TEXT_KEY), "toString 不能泄露嵌套 text 的明文 apiKey");
        assertFalse(s.contains(VISION_KEY), "toString 不能泄露嵌套 vision 的明文 apiKey");
        // 不包含敏感片段
        assertFalse(s.contains("test-secret"), "toString 不能包含 text apiKey 敏感片段");
        assertFalse(s.contains("vision-secret"), "toString 不能包含 vision apiKey 敏感片段");
        // 可以包含 keyMode 等非敏感字段
        assertTrue(s.contains("USER"), "toString 应当包含 keyMode");
    }

    // ─── 可选测试：排除 apiKey 后 toString 仍能帮助调试 ───────────────────────

    @Test
    void providerConfigRequestToStringShouldStillContainNonSensitiveFields() {
        AiProviderConfigRequest req = new AiProviderConfigRequest();
        req.setProvider("DEEPSEEK");
        req.setBaseUrl("https://api.deepseek.com");
        req.setModel("deepseek-chat");
        req.setApiKey(TEXT_KEY);
        req.setClearApiKey(false);

        String s = req.toString();
        assertNotNull(s);
        // 排除 apiKey 后，provider / baseUrl / model / clearApiKey 仍应保留，便于调试
        assertTrue(s.contains("DEEPSEEK"), "toString 应保留 provider 用于调试");
        assertTrue(s.contains("https://api.deepseek.com"), "toString 应保留 baseUrl 用于调试");
        assertTrue(s.contains("deepseek-chat"), "toString 应保留 model 用于调试");
        assertTrue(s.contains("clearApiKey"), "toString 应保留 clearApiKey 用于调试");
        // 同时仍然不泄露 apiKey
        assertFalse(s.contains(TEXT_KEY));
        assertFalse(s.contains("test-secret"));
    }
}
