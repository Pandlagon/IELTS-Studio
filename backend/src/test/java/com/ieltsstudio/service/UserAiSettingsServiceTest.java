package com.ieltsstudio.service;

import com.ieltsstudio.ai.AiProviderType;
import com.ieltsstudio.ai.service.AiProviderRegistry;
import com.ieltsstudio.ai.util.AiApiKeyCrypto;
import com.ieltsstudio.dto.ai.AiProviderConfigRequest;
import com.ieltsstudio.dto.ai.AiSettingsResponse;
import com.ieltsstudio.dto.ai.AiSettingsUpdateRequest;
import com.ieltsstudio.entity.UserAiSettings;
import com.ieltsstudio.mapper.UserAiSettingsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UserAiSettingsService} 单元测试。
 *
 * <p>不查真实数据库：{@link UserAiSettingsMapper} 用 Mockito mock；
 * {@link AiProviderRegistry} 与 {@link AiApiKeyCrypto} 用真实实例。</p>
 *
 * <p>测试字符串：{@code sk-test-secret-123456}（非真实 Key）。</p>
 */
class UserAiSettingsServiceTest {

    private static final String TEST_KEY = "sk-test-secret-123456";

    private UserAiSettingsMapper mapper;
    private AiProviderRegistry registry;
    private AiApiKeyCrypto crypto;
    private UserAiSettingsService service;

    @BeforeEach
    void setUp() {
        mapper = mock(UserAiSettingsMapper.class);
        registry = new AiProviderRegistry();
        registry.initBuiltinPresets();
        crypto = new AiApiKeyCrypto("settings-test-secret");
        service = new UserAiSettingsService(mapper, registry, crypto);
    }

    /** 构造一个已存在的默认 BUILTIN 实体（避免触发 insert） */
    private UserAiSettings existingDefault() {
        UserAiSettings s = new UserAiSettings();
        s.setId(1L);
        s.setUserId(100L);
        s.setKeyMode("BUILTIN");
        s.setTextProvider(AiProviderType.DEEPSEEK.name());
        s.setTextBaseUrl("https://api.deepseek.com");
        s.setTextModel("deepseek-chat");
        s.setVisionProvider(AiProviderType.QWEN.name());
        s.setVisionBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        s.setVisionModel("qwen3.6-plus");
        return s;
    }

    private AiSettingsUpdateRequest updateRequest(String keyMode, AiProviderConfigRequest text, AiProviderConfigRequest vision) {
        AiSettingsUpdateRequest req = new AiSettingsUpdateRequest();
        req.setKeyMode(keyMode);
        req.setText(text);
        req.setVision(vision);
        return req;
    }

    private AiProviderConfigRequest providerConfig(String provider, String baseUrl, String model, String apiKey, Boolean clear) {
        AiProviderConfigRequest c = new AiProviderConfigRequest();
        c.setProvider(provider);
        c.setBaseUrl(baseUrl);
        c.setModel(model);
        c.setApiKey(apiKey);
        c.setClearApiKey(clear);
        return c;
    }

    // ─── getOrCreateEntity ───────────────────────────────────────────────────

    @Test
    void getOrCreateEntityShouldCreateDefaultBuiltinWhenMissing() {
        when(mapper.selectOne(any())).thenReturn(null);

        ArgumentCaptor<UserAiSettings> captor = ArgumentCaptor.forClass(UserAiSettings.class);

        UserAiSettings created = service.getOrCreateEntity(100L);

        verify(mapper).insert(captor.capture());
        UserAiSettings inserted = captor.getValue();
        assertEquals(100L, inserted.getUserId());
        assertEquals("BUILTIN", inserted.getKeyMode());
        assertEquals(AiProviderType.DEEPSEEK.name(), inserted.getTextProvider());
        assertEquals("https://api.deepseek.com", inserted.getTextBaseUrl());
        assertEquals("deepseek-chat", inserted.getTextModel());
        assertEquals(AiProviderType.QWEN.name(), inserted.getVisionProvider());
        assertEquals("qwen3.6-plus", inserted.getVisionModel());
        // 默认记录不含任何 API Key
        assertNull(inserted.getTextApiKeyEncrypted());
        assertNull(inserted.getTextApiKeyMasked());
        assertNull(inserted.getVisionApiKeyEncrypted());
        assertNull(inserted.getVisionApiKeyMasked());
        assertEquals(inserted, created);
    }

    @Test
    void getOrCreateEntityShouldReturnExistingWhenPresent() {
        UserAiSettings existing = existingDefault();
        when(mapper.selectOne(any())).thenReturn(existing);

        UserAiSettings got = service.getOrCreateEntity(100L);

        assertEquals(existing, got);
        // 不应触发 insert
        verify(mapper, org.mockito.Mockito.never()).insert(any(UserAiSettings.class));
    }

    // ─── getSettings 不泄露 encrypted ─────────────────────────────────────────

    @Test
    void getSettingsShouldNotReturnEncryptedKey() {
        UserAiSettings existing = existingDefault();
        existing.setTextApiKeyEncrypted("SUPER-SENSITIVE-CIPHER");
        existing.setTextApiKeyMasked("sk-****3456");
        when(mapper.selectOne(any())).thenReturn(existing);

        AiSettingsResponse resp = service.getSettings(100L);

        // 响应结构上无 encrypted 字段；只能拿到 hasApiKey + masked
        assertTrue(resp.getText().isHasApiKey());
        assertEquals("sk-****3456", resp.getText().getMaskedApiKey());
        // 确认响应对象本身不含密文（toString 默认无敏感字段，但额外断言字段值）
        assertFalse(resp.getText().getMaskedApiKey().contains("SUPER-SENSITIVE-CIPHER"));
    }

    // ─── 保存新 apiKey ───────────────────────────────────────────────────────

    @Test
    void updateSettingsShouldEncryptNewTextApiKeyAndNotLeakPlaintext() {
        when(mapper.selectOne(any())).thenReturn(existingDefault());

        AiProviderConfigRequest text = providerConfig("DEEPSEEK", null, null, TEST_KEY, null);
        service.updateSettings(100L, updateRequest("USER", text, null));

        ArgumentCaptor<UserAiSettings> captor = ArgumentCaptor.forClass(UserAiSettings.class);
        verify(mapper).updateById(captor.capture());
        UserAiSettings saved = captor.getValue();

        // encrypted 不等于明文，且不含明文
        assertNotNull(saved.getTextApiKeyEncrypted());
        assertNotEquals(TEST_KEY, saved.getTextApiKeyEncrypted());
        assertFalse(saved.getTextApiKeyEncrypted().contains(TEST_KEY));
        assertFalse(saved.getTextApiKeyEncrypted().contains("test-secret"));
        // masked 有值且不含明文
        assertNotNull(saved.getTextApiKeyMasked());
        assertNotEquals(TEST_KEY, saved.getTextApiKeyMasked());
        assertFalse(saved.getTextApiKeyMasked().contains("test-secret"));
        // 响应也不含明文
        // （updateSettings 返回的 response 复用同一 toResponse，masked 非 null）
        assertEquals("USER", saved.getKeyMode());
    }

    @Test
    void updateSettingsResponseShouldNotContainPlaintextKey() {
        when(mapper.selectOne(any())).thenReturn(existingDefault());

        AiProviderConfigRequest text = providerConfig("DEEPSEEK", null, null, TEST_KEY, null);
        AiSettingsResponse resp = service.updateSettings(100L, updateRequest("USER", text, null));

        // mask 形如 sk-tes****3456：保留首尾片段，中间 ****，绝不含明文敏感片段
        String masked = resp.getText().getMaskedApiKey();
        assertTrue(masked.contains("****"));
        assertTrue(masked.endsWith("3456"));
        assertTrue(resp.getText().isHasApiKey());
        assertFalse(masked.contains("test-secret"));
        assertNotEquals(TEST_KEY, masked);
    }

    // ─── clearApiKey ─────────────────────────────────────────────────────────

    @Test
    void clearApiKeyShouldClearEncryptedAndMasked() {
        UserAiSettings existing = existingDefault();
        existing.setTextApiKeyEncrypted("old-cipher");
        existing.setTextApiKeyMasked("sk-****old");
        when(mapper.selectOne(any())).thenReturn(existing);

        AiProviderConfigRequest text = providerConfig("DEEPSEEK", null, null, null, true);
        service.updateSettings(100L, updateRequest("USER", text, null));

        ArgumentCaptor<UserAiSettings> captor = ArgumentCaptor.forClass(UserAiSettings.class);
        verify(mapper).updateById(captor.capture());
        UserAiSettings saved = captor.getValue();

        assertNull(saved.getTextApiKeyEncrypted());
        assertNull(saved.getTextApiKeyMasked());
    }

    // ─── apiKey=null 保留旧 key ──────────────────────────────────────────────

    @Test
    void nullApiKeyShouldKeepOldKey() {
        UserAiSettings existing = existingDefault();
        existing.setTextApiKeyEncrypted("old-cipher");
        existing.setTextApiKeyMasked("sk-****old");
        when(mapper.selectOne(any())).thenReturn(existing);

        AiProviderConfigRequest text = providerConfig("DEEPSEEK", null, null, null, null);
        service.updateSettings(100L, updateRequest("USER", text, null));

        ArgumentCaptor<UserAiSettings> captor = ArgumentCaptor.forClass(UserAiSettings.class);
        verify(mapper).updateById(captor.capture());
        UserAiSettings saved = captor.getValue();

        assertEquals("old-cipher", saved.getTextApiKeyEncrypted());
        assertEquals("sk-****old", saved.getTextApiKeyMasked());
    }

    // ─── 校验拒绝 ────────────────────────────────────────────────────────────

    @Test
    void customProviderMissingBaseUrlShouldBeRejected() {
        when(mapper.selectOne(any())).thenReturn(existingDefault());

        AiProviderConfigRequest text = providerConfig("OPENAI_COMPATIBLE", null, "some-model", null, null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateSettings(100L, updateRequest("USER", text, null)));
        assertTrue(ex.getMessage().contains("baseUrl"));
    }

    @Test
    void customProviderMissingModelShouldBeRejected() {
        when(mapper.selectOne(any())).thenReturn(existingDefault());

        AiProviderConfigRequest text = providerConfig("OPENAI_COMPATIBLE", "https://api.example.com", null, null, null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateSettings(100L, updateRequest("USER", text, null)));
        assertTrue(ex.getMessage().contains("model"));
    }

    @Test
    void invalidProviderShouldBeRejected() {
        when(mapper.selectOne(any())).thenReturn(existingDefault());

        AiProviderConfigRequest text = providerConfig("NOT_A_PROVIDER", null, null, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> service.updateSettings(100L, updateRequest("USER", text, null)));
    }

    @Test
    void nonHttpBaseUrlShouldBeRejected() {
        when(mapper.selectOne(any())).thenReturn(existingDefault());

        AiProviderConfigRequest text = providerConfig("OPENAI_COMPATIBLE", "ftp://api.example.com", "m", null, null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateSettings(100L, updateRequest("USER", text, null)));
        assertTrue(ex.getMessage().contains("http"));
    }

    @Test
    void invalidKeyModeShouldBeRejected() {
        when(mapper.selectOne(any())).thenReturn(existingDefault());
        assertThrows(IllegalArgumentException.class,
                () -> service.updateSettings(100L, updateRequest("WEIRD", null, null)));
    }

    // ─── USER 模式同时保存 text + vision ─────────────────────────────────────

    @Test
    void updateUserModeShouldSaveTextAndVisionProviders() {
        when(mapper.selectOne(any())).thenReturn(existingDefault());

        AiProviderConfigRequest text = providerConfig("DEEPSEEK", null, null, TEST_KEY, null);
        AiProviderConfigRequest vision = providerConfig("MIMO", null, null, TEST_KEY, null);
        service.updateSettings(100L, updateRequest("USER", text, vision));

        ArgumentCaptor<UserAiSettings> captor = ArgumentCaptor.forClass(UserAiSettings.class);
        verify(mapper).updateById(captor.capture());
        UserAiSettings saved = captor.getValue();

        assertEquals("USER", saved.getKeyMode());
        assertEquals(AiProviderType.DEEPSEEK.name(), saved.getTextProvider());
        assertEquals(AiProviderType.MIMO.name(), saved.getVisionProvider());
        // MiMO 是预设 Provider，baseUrl/model 用 preset 默认值补齐
        assertEquals("https://api.xiaomimimo.com/v1", saved.getVisionBaseUrl());
        assertEquals("mimo-v2.5", saved.getVisionModel());
        assertNotNull(saved.getTextApiKeyEncrypted());
        assertNotNull(saved.getVisionApiKeyEncrypted());
    }

    // ─── providers 预设列表 ──────────────────────────────────────────────────

    @Test
    void listProviderPresetsShouldContainTextAndVisionGroups() {
        var presets = service.listProviderPresets();

        assertTrue(presets.containsKey("text"));
        assertTrue(presets.containsKey("vision"));
        // text 至少含 DEEPSEEK + OPENAI_COMPATIBLE
        assertTrue(presets.get("text").stream().anyMatch(p -> "DEEPSEEK".equals(p.getProvider())));
        assertTrue(presets.get("text").stream().anyMatch(p -> "OPENAI_COMPATIBLE".equals(p.getProvider())));
        // vision 至少含 QWEN + MIMO + OPENAI_COMPATIBLE
        assertTrue(presets.get("vision").stream().anyMatch(p -> "QWEN".equals(p.getProvider())));
        assertTrue(presets.get("vision").stream().anyMatch(p -> "MIMO".equals(p.getProvider())));
        assertTrue(presets.get("vision").stream().anyMatch(p -> "OPENAI_COMPATIBLE".equals(p.getProvider())));
    }

    // ─── requireUserId 防御性校验 ───────────────────────────────────────────
    //
    // Controller 正常会传 authUser.getId()，但 Service 层自己也应拒绝 null userId，
    // 避免后续查询/写入出现 NPE 或脏数据。

    @Test
    void getSettingsShouldRejectNullUserId() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.getSettings(null));
        assertTrue(ex.getMessage().contains("userId"));
    }

    @Test
    void updateSettingsShouldRejectNullUserId() {
        AiSettingsUpdateRequest req = updateRequest("USER",
                providerConfig("DEEPSEEK", null, null, TEST_KEY, null), null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateSettings(null, req));
        assertTrue(ex.getMessage().contains("userId"));
    }

    @Test
    void getOrCreateEntityShouldRejectNullUserId() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.getOrCreateEntity(null));
        assertTrue(ex.getMessage().contains("userId"));
    }
}
