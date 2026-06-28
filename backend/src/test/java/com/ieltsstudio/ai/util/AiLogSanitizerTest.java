package com.ieltsstudio.ai.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AiLogSanitizer} 单元测试。
 *
 * <p>重点验证：</p>
 * <ul>
 *   <li>{@code maskApiKey} 保留前缀与尾部 4 位，中间脱敏。</li>
 *   <li>{@code sanitize} 移除 / 脱敏 Authorization 头与 Bearer token。</li>
 *   <li>{@code summarizeProviderError} 截断过长错误体。</li>
 *   <li>任何输出都不包含完整 API Key。</li>
 * </ul>
 */
class AiLogSanitizerTest {

    @Test
    void maskApiKeyShouldKeepPrefixAndSuffixOnly() {
        String masked = AiLogSanitizer.maskApiKey("sk-abcdef123456");
        assertEquals("sk-abc****3456", masked);
        assertFalse(masked.contains("abcdef"));
        assertFalse(masked.contains("1234"));
    }

    @Test
    void maskApiKeyShouldReturnEmptyForNull() {
        assertEquals("", AiLogSanitizer.maskApiKey(null));
    }

    @Test
    void maskApiKeyShouldReturnEmptyForBlank() {
        assertEquals("", AiLogSanitizer.maskApiKey("   "));
    }

    @Test
    void maskApiKeyShouldNotLeakShortKey() {
        // 太短的 key 不保留任何明文片段
        String masked = AiLogSanitizer.maskApiKey("sk-ab");
        assertEquals("****", masked);
        assertFalse(masked.contains("sk-ab"));
    }

    @Test
    void sanitizeShouldRemoveBearerToken() {
        String input = "Authorization: Bearer sk-secret-token-123456";
        String result = AiLogSanitizer.sanitize(input);

        assertFalse(result.contains("sk-secret-token-123456"), "must not contain full bearer token");
        assertFalse(result.contains("Bearer"), "must not contain Bearer token");
    }

    @Test
    void sanitizeShouldMaskKeyLikeFragmentInBody() {
        String input = "error detail: invalid key sk-abcdefghijklmnop reported";
        String result = AiLogSanitizer.sanitize(input);

        assertFalse(result.contains("sk-abcdefghijklmnop"), "must not contain full key-like fragment");
    }

    @Test
    void sanitizeShouldReturnEmptyForNull() {
        assertEquals("", AiLogSanitizer.sanitize(null));
    }

    @Test
    void summarizeProviderErrorShouldTruncateLongBody() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("abcdefghij"); // 10 * 100 = 1000 chars
        }
        String body = sb.toString();
        String result = AiLogSanitizer.summarizeProviderError(body);

        assertTrue(result.length() <= 300 + "...(truncated)".length(),
                "summarized error body must be truncated to ~300 chars");
        assertTrue(result.endsWith("...(truncated)"), "truncated body should end with marker");
    }

    @Test
    void summarizeProviderErrorShouldMaskKeyLikeContent() {
        String body = "error: invalid key sk-abcdefghijklmnop in response";
        String result = AiLogSanitizer.summarizeProviderError(body);

        assertFalse(result.contains("sk-abcdefghijklmnop"), "must not contain full key-like fragment");
    }

    @Test
    void summarizeProviderErrorShouldReturnEmptyForNull() {
        assertEquals("", AiLogSanitizer.summarizeProviderError(null));
    }

    @Test
    void summarizeProviderErrorShouldReturnEmptyForBlank() {
        assertEquals("", AiLogSanitizer.summarizeProviderError("   "));
    }

    @Test
    void summarizeProviderErrorShouldKeepShortBodyIntact() {
        String body = "rate limit exceeded";
        assertEquals("rate limit exceeded", AiLogSanitizer.summarizeProviderError(body));
    }
}
