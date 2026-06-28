package com.ieltsstudio.ai.service;

import com.ieltsstudio.ai.AiKeyMode;
import com.ieltsstudio.ai.AiProviderType;
import com.ieltsstudio.ai.AiTaskType;
import com.ieltsstudio.ai.config.AiProviderProperties;
import com.ieltsstudio.ai.model.AiCredentials;
import com.ieltsstudio.ai.util.AiApiKeyCrypto;
import com.ieltsstudio.entity.UserAiSettings;
import com.ieltsstudio.mapper.UserAiSettingsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AiSettingsService} USER 模式解析单元测试。
 *
 * <p>不查真实数据库：{@link UserAiSettingsMapper} 用 Mockito mock；
 * 用同一 {@link AiApiKeyCrypto} 实例加密测试 Key，确保 resolve 时能正确解密。</p>
 *
 * <p>测试字符串：{@code sk-user-test-key-999}（非真实 Key）。</p>
 */
class AiSettingsServiceUserModeTest {

    private static final String USER_KEY = "sk-user-test-key-999";

    private AiProviderProperties props;
    private AiProviderRegistry registry;
    private AiApiKeyCrypto crypto;
    private UserAiSettingsMapper mapper;
    private AiSettingsService service;

    @BeforeEach
    void setUp() {
        props = new AiProviderProperties();
        props.getAi().getDeepseek().setApiKey("sk-deepseek-builtin");
        props.getAi().getDeepseek().setBaseUrl("https://api.deepseek.com");
        props.getAi().getDeepseek().setModel("deepseek-chat");
        props.getAi().getPrecise().setProvider("qwen");
        props.getQwen().setApiKey("sk-qwen-builtin");
        props.getQwen().setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        props.getQwen().setModel("qwen3.6-plus");

        registry = new AiProviderRegistry();
        registry.initBuiltinPresets();
        crypto = new AiApiKeyCrypto("usermode-test-secret");
        mapper = mock(UserAiSettingsMapper.class);
        service = new AiSettingsService(props, registry, mapper, crypto);
    }

    private UserAiSettings userSettings() {
        UserAiSettings s = new UserAiSettings();
        s.setId(1L);
        s.setUserId(100L);
        s.setKeyMode(AiKeyMode.USER.name());
        return s;
    }

    // ─── 1. BUILTIN 仍走站点配置 ─────────────────────────────────────────────

    @Test
    void builtinModeShouldResolveSiteConfig() {
        when(mapper.selectOne(any())).thenReturn(null);

        AiCredentials creds = service.resolve(100L, AiTaskType.TEXT);

        assertEquals(AiKeyMode.BUILTIN, creds.getKeyMode());
        assertEquals(AiProviderType.DEEPSEEK, creds.getProvider());
        assertEquals("sk-deepseek-builtin", creds.getApiKey());
    }

    // ─── 2. USER + TEXT 解析 text credentials ────────────────────────────────

    @Test
    void userModeTextShouldResolveTextCredentials() {
        UserAiSettings s = userSettings();
        s.setTextProvider(AiProviderType.DEEPSEEK.name());
        s.setTextBaseUrl("https://api.deepseek.com");
        s.setTextModel("deepseek-chat");
        s.setTextApiKeyEncrypted(crypto.encrypt(USER_KEY));
        when(mapper.selectOne(any())).thenReturn(s);

        AiCredentials creds = service.resolve(100L, AiTaskType.TEXT);

        assertEquals(AiKeyMode.USER, creds.getKeyMode());
        assertEquals(AiProviderType.DEEPSEEK, creds.getProvider());
        assertEquals(AiTaskType.TEXT, creds.getTaskType());
        assertEquals("https://api.deepseek.com", creds.getBaseUrl());
        assertEquals("deepseek-chat", creds.getModel());
        assertEquals(USER_KEY, creds.getApiKey());
        assertEquals("max_tokens", creds.getTokenField());
        assertTrue(creds.hasApiKey());
    }

    // ─── 3. USER + VISION 解析 vision credentials ────────────────────────────

    @Test
    void userModeVisionShouldResolveVisionCredentials() {
        UserAiSettings s = userSettings();
        s.setVisionProvider(AiProviderType.QWEN.name());
        s.setVisionBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        s.setVisionModel("qwen3.6-plus");
        s.setVisionApiKeyEncrypted(crypto.encrypt(USER_KEY));
        when(mapper.selectOne(any())).thenReturn(s);

        AiCredentials creds = service.resolve(100L, AiTaskType.VISION);

        assertEquals(AiKeyMode.USER, creds.getKeyMode());
        assertEquals(AiProviderType.QWEN, creds.getProvider());
        assertEquals(AiTaskType.VISION, creds.getTaskType());
        assertEquals(USER_KEY, creds.getApiKey());
        assertEquals("max_tokens", creds.getTokenField());
    }

    // ─── 4. USER 模式 encrypted key 缺失抛异常，不含 key/密文 ─────────────────

    @Test
    void userModeMissingKeyShouldThrowWithoutLeakingKey() {
        UserAiSettings s = userSettings();
        s.setTextProvider(AiProviderType.DEEPSEEK.name());
        s.setTextBaseUrl("https://api.deepseek.com");
        s.setTextModel("deepseek-chat");
        s.setTextApiKeyEncrypted(null); // 缺失
        when(mapper.selectOne(any())).thenReturn(s);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.resolve(100L, AiTaskType.TEXT));

        // 异常信息不含 key / 密文
        assertFalse(ex.getMessage().contains(USER_KEY));
        assertFalse(ex.getMessage().contains("sk-"));
        assertFalse(ex.getMessage().contains("cipher"));
        assertTrue(ex.getMessage().contains("not configured"));
    }

    // ─── 5. USER + MiMO tokenField = max_completion_tokens ───────────────────

    @Test
    void userModeMimoShouldUseMaxCompletionTokens() {
        UserAiSettings s = userSettings();
        s.setVisionProvider(AiProviderType.MIMO.name());
        s.setVisionBaseUrl("https://api.xiaomimimo.com/v1");
        s.setVisionModel("mimo-v2.5");
        s.setVisionApiKeyEncrypted(crypto.encrypt(USER_KEY));
        when(mapper.selectOne(any())).thenReturn(s);

        AiCredentials creds = service.resolve(100L, AiTaskType.VISION);

        assertEquals(AiProviderType.MIMO, creds.getProvider());
        assertEquals("max_completion_tokens", creds.getTokenField());
    }

    // ─── 6. USER + OPENAI_COMPATIBLE tokenField 默认 max_tokens ───────────────

    @Test
    void userModeOpenAiCompatibleShouldUseMaxTokens() {
        UserAiSettings s = userSettings();
        s.setTextProvider(AiProviderType.OPENAI_COMPATIBLE.name());
        s.setTextBaseUrl("https://api.openai.com/v1");
        s.setTextModel("gpt-4o");
        s.setTextApiKeyEncrypted(crypto.encrypt(USER_KEY));
        when(mapper.selectOne(any())).thenReturn(s);

        AiCredentials creds = service.resolve(100L, AiTaskType.TEXT);

        assertEquals(AiProviderType.OPENAI_COMPATIBLE, creds.getProvider());
        assertEquals("max_tokens", creds.getTokenField());
        assertEquals(USER_KEY, creds.getApiKey());
    }

    // ─── 7. 无 user settings 记录 fallback 到 BUILTIN ────────────────────────

    @Test
    void noUserSettingsShouldFallbackToBuiltin() {
        when(mapper.selectOne(any())).thenReturn(null);

        AiCredentials creds = service.resolve(100L, AiTaskType.TEXT);

        assertEquals(AiKeyMode.BUILTIN, creds.getKeyMode());
        assertEquals(AiProviderType.DEEPSEEK, creds.getProvider());
        assertEquals("sk-deepseek-builtin", creds.getApiKey());
    }

    @Test
    void nonUserKeyModeShouldFallbackToBuiltin() {
        UserAiSettings s = userSettings();
        s.setKeyMode(AiKeyMode.BUILTIN.name()); // 非 USER
        when(mapper.selectOne(any())).thenReturn(s);

        AiCredentials creds = service.resolve(100L, AiTaskType.TEXT);

        assertEquals(AiKeyMode.BUILTIN, creds.getKeyMode());
        assertEquals("sk-deepseek-builtin", creds.getApiKey());
    }

    // ─── 8. USER 模式 provider 不支持 taskType 必须拒绝（解密前） ──────────────
    //
    // 场景：脏数据 taskType=TEXT 但 provider=QWEN（Qwen 只支持 VISION）。
    // 必须在解密 API Key 之前拒绝，避免对脏数据做无谓解密；异常信息不含 key / 密文。

    @Test
    void userModeShouldRejectProviderThatDoesNotSupportTaskType() {
        UserAiSettings s = userSettings();
        s.setTextProvider(AiProviderType.QWEN.name()); // QWEN 不支持 TEXT
        s.setTextBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        s.setTextModel("qwen3.6-plus");
        String encrypted = crypto.encrypt(USER_KEY);
        s.setTextApiKeyEncrypted(encrypted);
        when(mapper.selectOne(any())).thenReturn(s);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.resolve(100L, AiTaskType.TEXT));

        // 异常信息不含明文 key
        assertFalse(ex.getMessage().contains(USER_KEY));
        assertFalse(ex.getMessage().contains("sk-"));
        // 异常信息不含密文
        assertFalse(ex.getMessage().contains(encrypted));
        // 异常信息提示 taskType 不被支持
        assertTrue(ex.getMessage().contains("taskType=TEXT"));
    }
}
