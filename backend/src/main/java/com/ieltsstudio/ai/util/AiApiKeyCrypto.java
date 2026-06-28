package com.ieltsstudio.ai.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 用户自填 API Key 的加密 / 解密 / 脱敏工具。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>{@link #encrypt(String)} —— 用 AES/GCM/NoPadding 加密明文 Key，输出 {@code Base64(IV||密文+tag)}。</li>
 *   <li>{@link #decrypt(String)} —— 解密密文还原明文 Key（仅在后端内存中短暂使用）。</li>
 *   <li>{@link #mask(String)} —— 生成 masked key（委托 {@link AiLogSanitizer#maskApiKey(String)}）。</li>
 * </ul>
 *
 * <h2>实现要点</h2>
 * <ul>
 *   <li>使用 JDK 自带加密能力（{@code javax.crypto}），<b>不</b>新增依赖。</li>
 *   <li>算法：AES/GCM/NoPadding，128 位认证标签，12 字节随机 IV。</li>
 *   <li>每次加密 IV 随机生成，<b>同一明文多次加密得到的密文不同</b>。</li>
 *   <li>加密密钥从配置 {@code app.ai.key-encryption-secret} 读取（推荐通过环境变量
 *       {@code AI_KEY_ENCRYPTION_SECRET} 注入）；缺失时使用明确的开发态 fallback，
 *       并打印 WARN 日志提醒生产环境必须设置。</li>
 *   <li>密钥派生：对 secret 做 SHA-256 得到 32 字节，作为 AES-256 key。</li>
 * </ul>
 *
 * <h2>安全要求</h2>
 * <ul>
 *   <li><b>禁止</b>把明文 Key 写入日志。</li>
 *   <li>解密失败抛出通用异常，异常信息<b>不</b>包含密文或明文。</li>
 *   <li>{@code encrypt(null)} / {@code encrypt(空白)} 返回 {@code null}；
 *       {@code decrypt(null)} / {@code decrypt(空白)} 返回 {@code null}。</li>
 * </ul>
 */
@Slf4j
@Component
public class AiApiKeyCrypto {

    /** AES/GCM 算法 */
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    /** AES 密钥算法 */
    private static final String KEY_ALGORITHM = "AES";
    /** GCM IV 长度（字节），NIST 推荐 12 字节 */
    private static final int IV_LENGTH = 12;
    /** GCM 认证标签长度（位） */
    private static final int TAG_LENGTH_BITS = 128;

    /**
     * 开发态 fallback 密钥。
     * <p><b>⚠️ 仅用于本地 / 测试，生产环境必须通过 {@code app.ai.key-encryption-secret} 注入高强度密钥。</b>
     * 一旦使用 fallback，构造时会打印 WARN 日志提醒。</p>
     */
    static final String FALLBACK_DEV_SECRET = "ielts-studio-dev-key-encryption-secret-DO-NOT-USE-IN-PROD";

    private final SecretKey secretKey;
    private final SecureRandom random = new SecureRandom();

    /**
     * Spring 构造注入：从 {@code app.ai.key-encryption-secret} 读取密钥，
     * 缺失时回退到 {@link #FALLBACK_DEV_SECRET}。
     *
     * @param secret 配置中的加密密钥（可为空）
     */
    @Autowired
    public AiApiKeyCrypto(@Value("${app.ai.key-encryption-secret:}") String secret) {
        this(secret, true);
    }

    /**
     * 可测试构造器：允许显式指定 secret 与是否打印 fallback 警告。
     *
     * @param secret         加密密钥；null / 空白时使用 fallback
     * @param warnOnFallback 为 true 且使用 fallback 时打印 WARN 日志
     */
    AiApiKeyCrypto(String secret, boolean warnOnFallback) {
        boolean usingFallback = secret == null || secret.isBlank();
        String effective = usingFallback ? FALLBACK_DEV_SECRET : secret;
        if (usingFallback && warnOnFallback) {
            log.warn("app.ai.key-encryption-secret is not configured; falling back to dev secret. "
                    + "This MUST NOT be used in production. Set AI_KEY_ENCRYPTION_SECRET env var.");
        }
        this.secretKey = deriveKey(effective);
    }

    /**
     * 加密明文 API Key。
     *
     * @param plainApiKey 明文 Key
     * @return {@code Base64(IV||密文+tag)}；null / 空白输入返回 null
     */
    public String encrypt(String plainApiKey) {
        if (plainApiKey == null || plainApiKey.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainApiKey.getBytes(StandardCharsets.UTF_8));

            // IV || cipherText
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            // 异常信息不包含明文 key
            throw new IllegalStateException("Failed to encrypt API key", e);
        }
    }

    /**
     * 解密 API Key 密文。
     *
     * @param encryptedApiKey {@code Base64(IV||密文+tag)} 密文
     * @return 明文 Key；null / 空白输入返回 null
     * @throws IllegalStateException 解密失败（异常信息不含密文 / 明文）
     */
    public String decrypt(String encryptedApiKey) {
        if (encryptedApiKey == null || encryptedApiKey.isBlank()) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedApiKey);
            if (combined.length <= IV_LENGTH) {
                throw new IllegalArgumentException("ciphertext too short");
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            byte[] cipherText = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plain = cipher.doFinal(cipherText);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 通用异常信息，不输出密文或明文
            throw new IllegalStateException("Failed to decrypt API key", e);
        }
    }

    /**
     * 生成 masked key，供前端展示 / 日志记录。
     *
     * <p>委托 {@link AiLogSanitizer#maskApiKey(String)}，行为示例：{@code sk-abcdef123456 → sk-abc****3456}。</p>
     *
     * @param plainApiKey 明文 Key
     * @return 脱敏串；null / 空白返回空串
     */
    public String mask(String plainApiKey) {
        return AiLogSanitizer.maskApiKey(plainApiKey);
    }

    /**
     * 由 secret 派生 AES-256 密钥（SHA-256 → 32 字节）。
     */
    private static SecretKey deriveKey(String secret) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, KEY_ALGORITHM);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive AES key", e);
        }
    }
}
