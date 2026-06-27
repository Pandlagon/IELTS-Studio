package com.ieltsstudio.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UserAiSettings} 单元测试。
 *
 * <p>重点验证：</p>
 * <ul>
 *   <li>字段可正常 set / get。</li>
 *   <li>{@code toString()} 不泄露 {@code textApiKeyEncrypted} / {@code visionApiKeyEncrypted}。</li>
 * </ul>
 */
class UserAiSettingsTest {

    private static final String SENSITIVE_CIPHERTEXT = "SUPER-SENSITIVE-CIPHERTEXT-XYZ";

    @Test
    void shouldSetAndGetFields() {
        UserAiSettings s = new UserAiSettings();
        s.setId(1L);
        s.setUserId(100L);
        s.setKeyMode("USER");
        s.setTextProvider("DEEPSEEK");
        s.setTextBaseUrl("https://api.deepseek.com");
        s.setTextModel("deepseek-chat");
        s.setTextApiKeyEncrypted(SENSITIVE_CIPHERTEXT);
        s.setTextApiKeyMasked("sk-****3456");
        s.setVisionProvider("QWEN");
        s.setVisionModel("qwen3.6-plus");
        s.setVisionApiKeyEncrypted("vision-cipher");
        s.setVisionApiKeyMasked("sk-****abcd");
        LocalDateTime now = LocalDateTime.now();
        s.setCreatedAt(now);
        s.setUpdatedAt(now);

        assertEquals(1L, s.getId());
        assertEquals(100L, s.getUserId());
        assertEquals("USER", s.getKeyMode());
        assertEquals("DEEPSEEK", s.getTextProvider());
        assertEquals("https://api.deepseek.com", s.getTextBaseUrl());
        assertEquals("deepseek-chat", s.getTextModel());
        assertEquals(SENSITIVE_CIPHERTEXT, s.getTextApiKeyEncrypted());
        assertEquals("sk-****3456", s.getTextApiKeyMasked());
        assertEquals("QWEN", s.getVisionProvider());
        assertEquals("qwen3.6-plus", s.getVisionModel());
        assertEquals("vision-cipher", s.getVisionApiKeyEncrypted());
        assertEquals("sk-****abcd", s.getVisionApiKeyMasked());
        assertEquals(now, s.getCreatedAt());
        assertEquals(now, s.getUpdatedAt());
    }

    @Test
    void toStringShouldNotLeakEncryptedKeys() {
        UserAiSettings s = new UserAiSettings();
        s.setId(1L);
        s.setUserId(100L);
        s.setTextApiKeyEncrypted(SENSITIVE_CIPHERTEXT);
        s.setVisionApiKeyEncrypted("vision-cipher-abc");

        String str = s.toString();

        assertFalse(str.contains(SENSITIVE_CIPHERTEXT),
                "toString must not contain textApiKeyEncrypted");
        assertFalse(str.contains("vision-cipher-abc"),
                "toString must not contain visionApiKeyEncrypted");
        // 非敏感字段仍可出现，便于排查
        assertTrue(str.contains("UserAiSettings"));
    }

    @Test
    void toStringShouldKeepMaskedFieldVisible() {
        // masked 字段是面向前端展示的脱敏串，可出现在 toString 中
        UserAiSettings s = new UserAiSettings();
        s.setTextApiKeyMasked("sk-****3456");

        String str = s.toString();
        assertTrue(str.contains("sk-****3456"));
    }
}
