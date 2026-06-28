package com.ieltsstudio.ai.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AiApiKeyCrypto} 单元测试。
 *
 * <p>不使用真实 API Key，统一使用测试字符串 {@code sk-test-secret-123456}。</p>
 *
 * <p>重点验证：</p>
 * <ul>
 *   <li>加密后不等于明文，且密文不含明文片段。</li>
 *   <li>解密后还原原始 key。</li>
 *   <li>同一明文两次加密密文不同（IV 随机）。</li>
 *   <li>{@code mask} 不泄露完整 key。</li>
 *   <li>null / blank 安全处理。</li>
 *   <li>解密失败异常信息不泄露 key。</li>
 * </ul>
 */
class AiApiKeyCryptoTest {

    private static final String TEST_KEY = "sk-test-secret-123456";

    private final AiApiKeyCrypto crypto = new AiApiKeyCrypto("unit-test-secret", false);

    @Test
    void encryptShouldNotEqualPlaintext() {
        String encrypted = crypto.encrypt(TEST_KEY);
        assertNotEquals(TEST_KEY, encrypted);
    }

    @Test
    void encryptShouldNotContainPlaintext() {
        String encrypted = crypto.encrypt(TEST_KEY);
        assertFalse(encrypted.contains(TEST_KEY), "ciphertext must not contain plaintext key");
        assertFalse(encrypted.contains("test-secret"), "ciphertext must not contain key fragments");
        assertFalse(encrypted.contains("123456"));
    }

    @Test
    void decryptShouldReturnOriginalKey() {
        String encrypted = crypto.encrypt(TEST_KEY);
        String decrypted = crypto.decrypt(encrypted);
        assertEquals(TEST_KEY, decrypted);
    }

    @Test
    void encryptingSameKeyTwiceShouldProduceDifferentCiphertext() {
        String first = crypto.encrypt(TEST_KEY);
        String second = crypto.encrypt(TEST_KEY);
        assertNotEquals(first, second, "GCM IV must be random; ciphertext must differ");
        // 两次都能正确解密回原文
        assertEquals(TEST_KEY, crypto.decrypt(first));
        assertEquals(TEST_KEY, crypto.decrypt(second));
    }

    @Test
    void maskShouldNotLeakFullKey() {
        String masked = crypto.mask(TEST_KEY);
        assertFalse(masked.contains(TEST_KEY), "masked output must not contain full key");
        assertFalse(masked.contains("test-secret"));
        // 仍保留可识别的脱敏形态（前缀 + **** + 尾部）
        assertTrue(masked.contains("****"));
    }

    @Test
    void encryptShouldReturnNullForNull() {
        assertNull(crypto.encrypt(null));
    }

    @Test
    void encryptShouldReturnNullForBlank() {
        assertNull(crypto.encrypt("   "));
        assertNull(crypto.encrypt(""));
    }

    @Test
    void decryptShouldReturnNullForNull() {
        assertNull(crypto.decrypt(null));
    }

    @Test
    void decryptShouldReturnNullForBlank() {
        assertNull(crypto.decrypt("   "));
        assertNull(crypto.decrypt(""));
    }

    @Test
    void decryptShouldThrowOnTamperedCiphertextWithoutLeakingKey() {
        String encrypted = crypto.encrypt(TEST_KEY);
        // 篡改密文末尾若干字符
        String tampered = encrypted.substring(0, encrypted.length() - 4) + "AAAA";

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> crypto.decrypt(tampered));

        // 异常信息不得包含明文 key 或其片段
        assertFalse(ex.getMessage().contains(TEST_KEY), "exception must not contain plaintext key");
        assertFalse(ex.getMessage().contains("sk-test"), "exception must not contain key fragments");
    }

    @Test
    void decryptShouldThrowOnInvalidBase64WithoutLeakingKey() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> crypto.decrypt("!!!not-base64!!!"));

        assertFalse(ex.getMessage().contains(TEST_KEY));
        assertEquals("Failed to decrypt API key", ex.getMessage());
    }

    @Test
    void shouldRoundTripWithFallbackDevSecret() {
        // 未配置 secret 时回退到开发态密钥，仍可正常加解密
        AiApiKeyCrypto fallbackCrypto = new AiApiKeyCrypto(null, false);
        String encrypted = fallbackCrypto.encrypt(TEST_KEY);
        assertEquals(TEST_KEY, fallbackCrypto.decrypt(encrypted));
    }

    @Test
    void differentSecretsShouldNotCrossDecrypt() {
        // 用 secret A 加密，用 secret B 解密应失败
        AiApiKeyCrypto cryptoA = new AiApiKeyCrypto("secret-A", false);
        AiApiKeyCrypto cryptoB = new AiApiKeyCrypto("secret-B", false);
        String encrypted = cryptoA.encrypt(TEST_KEY);

        assertThrows(IllegalStateException.class, () -> cryptoB.decrypt(encrypted));
    }

    @Test
    void maskShouldDelegateToSanitizerForShortKey() {
        // 太短的 key 不保留任何明文片段
        String masked = crypto.mask("sk-ab");
        assertEquals("****", masked);
    }
}
