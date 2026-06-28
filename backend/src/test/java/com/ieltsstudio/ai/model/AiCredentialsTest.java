package com.ieltsstudio.ai.model;

import com.ieltsstudio.ai.AiKeyMode;
import com.ieltsstudio.ai.AiProviderType;
import com.ieltsstudio.ai.AiTaskType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AiCredentials} 单元测试。
 *
 * <p>重点验证：</p>
 * <ul>
 *   <li>{@code toString()} 不泄露真实 API Key。</li>
 *   <li>{@code hasApiKey()} 行为正确。</li>
 *   <li>空 / null key 不抛异常。</li>
 * </ul>
 */
class AiCredentialsTest {

    private AiCredentials buildWithKey(String apiKey) {
        return AiCredentials.builder()
                .keyMode(AiKeyMode.BUILTIN)
                .provider(AiProviderType.DEEPSEEK)
                .taskType(AiTaskType.TEXT)
                .baseUrl("https://api.deepseek.com")
                .model("deepseek-chat")
                .apiKey(apiKey)
                .tokenField("max_tokens")
                .build();
    }

    @Test
    void toStringShouldNotLeakApiKey() {
        AiCredentials creds = buildWithKey("sk-test-secret-123456");
        String s = creds.toString();

        assertFalse(s.contains("sk-test-secret-123456"), "toString must not contain full API key");
        assertFalse(s.contains("secret"), "toString must not contain key fragments");
        assertTrue(s.contains("DEEPSEEK"), "toString should contain non-sensitive provider info");
        assertTrue(s.contains("deepseek-chat"), "toString should contain non-sensitive model info");
    }

    @Test
    void toStringShouldIndicateMaskedKeyWhenPresent() {
        AiCredentials creds = buildWithKey("sk-test-secret-123456");
        String s = creds.toString();
        assertTrue(s.contains("<masked>"), "toString should mark key as <masked> when present");
    }

    @Test
    void toStringShouldNotCrashOnNullKey() {
        AiCredentials creds = buildWithKey(null);
        assertDoesNotThrow(() -> creds.toString());
        String s = creds.toString();
        assertTrue(s.contains("<empty>"), "toString should mark key as <empty> when null");
        assertFalse(s.contains("sk-"));
    }

    @Test
    void toStringShouldNotCrashOnEmptyKey() {
        AiCredentials creds = buildWithKey("");
        assertDoesNotThrow(() -> creds.toString());
        assertTrue(creds.toString().contains("<empty>"));
    }

    @Test
    void hasApiKeyShouldReturnTrueWhenKeyPresent() {
        assertTrue(buildWithKey("sk-test-123").hasApiKey());
    }

    @Test
    void hasApiKeyShouldReturnFalseWhenKeyNull() {
        assertFalse(buildWithKey(null).hasApiKey());
    }

    @Test
    void hasApiKeyShouldReturnFalseWhenKeyBlank() {
        assertFalse(buildWithKey("   ").hasApiKey());
    }

    @Test
    void hasApiKeyShouldReturnFalseWhenKeyEmpty() {
        assertFalse(buildWithKey("").hasApiKey());
    }
}
