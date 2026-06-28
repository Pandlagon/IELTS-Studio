package com.ieltsstudio.ai.service;

import com.ieltsstudio.ai.AiKeyMode;
import com.ieltsstudio.ai.AiProviderType;
import com.ieltsstudio.ai.AiTaskType;
import com.ieltsstudio.ai.config.AiProviderProperties;
import com.ieltsstudio.ai.model.AiCredentials;
import com.ieltsstudio.ai.util.AiApiKeyCrypto;
import com.ieltsstudio.mapper.UserAiSettingsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * {@link AiSettingsService} 单元测试（BUILTIN 模式）。
 *
 * <p>不查数据库、不发真实网络请求。{@link UserAiSettingsMapper} 用 Mockito mock，
 * 默认返回 null（无用户设置），使 resolve 回退到 BUILTIN，保持本组测试原有行为。</p>
 */
class AiSettingsServiceTest {

    private AiProviderProperties props;
    private AiProviderRegistry registry;
    private AiSettingsService service;

    @BeforeEach
    void setUp() {
        props = new AiProviderProperties();
        props.getAi().getDeepseek().setApiKey("sk-deepseek-test");
        props.getAi().getDeepseek().setBaseUrl("https://api.deepseek.com");
        props.getAi().getDeepseek().setModel("deepseek-chat");

        props.getAi().getPrecise().setProvider("qwen");

        props.getQwen().setApiKey("sk-qwen-test");
        props.getQwen().setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        props.getQwen().setModel("qwen3.6-plus");

        props.getMimo().setApiKey("sk-mimo-test");
        props.getMimo().setBaseUrl("https://api.xiaomimimo.com/v1");
        props.getMimo().setModel("mimo-v2.5");

        registry = new AiProviderRegistry();
        registry.initBuiltinPresets();

        // mock mapper：selectOne 默认返回 null → resolve 回退 BUILTIN
        UserAiSettingsMapper mapper = mock(UserAiSettingsMapper.class);
        AiApiKeyCrypto crypto = new AiApiKeyCrypto("builtin-test-secret");
        service = new AiSettingsService(props, registry, mapper, crypto);
    }

    @Test
    void shouldResolveDeepSeekForTextTask() {
        AiCredentials creds = service.resolve(1L, AiTaskType.TEXT);

        assertEquals(AiKeyMode.BUILTIN, creds.getKeyMode());
        assertEquals(AiProviderType.DEEPSEEK, creds.getProvider());
        assertEquals(AiTaskType.TEXT, creds.getTaskType());
        assertEquals("deepseek-chat", creds.getModel());
        assertEquals("https://api.deepseek.com", creds.getBaseUrl());
        assertEquals("max_tokens", creds.getTokenField());
        assertTrue(creds.hasApiKey());
    }

    @Test
    void shouldResolveQwenForVisionWhenPreciseProviderIsQwen() {
        AiCredentials creds = service.resolve(1L, AiTaskType.VISION);

        assertEquals(AiKeyMode.BUILTIN, creds.getKeyMode());
        assertEquals(AiProviderType.QWEN, creds.getProvider());
        assertEquals(AiTaskType.VISION, creds.getTaskType());
        assertEquals("qwen3.6-plus", creds.getModel());
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1", creds.getBaseUrl());
        assertEquals("max_tokens", creds.getTokenField());
        assertTrue(creds.hasApiKey());
    }

    @Test
    void shouldResolveMimoForVisionWhenPreciseProviderIsMimo() {
        props.getAi().getPrecise().setProvider("mimo");

        AiCredentials creds = service.resolve(1L, AiTaskType.VISION);

        assertEquals(AiProviderType.MIMO, creds.getProvider());
        assertEquals(AiTaskType.VISION, creds.getTaskType());
        assertEquals("mimo-v2.5", creds.getModel());
        assertEquals("https://api.xiaomimimo.com/v1", creds.getBaseUrl());
        assertEquals("max_completion_tokens", creds.getTokenField());
        assertTrue(creds.hasApiKey());
    }

    @Test
    void shouldThrowWhenTaskTypeNull() {
        assertThrows(IllegalArgumentException.class,
                () -> service.resolve(1L, null));
    }

    @Test
    void shouldThrowWhenDeepSeekApiKeyNotConfigured() {
        props.getAi().getDeepseek().setApiKey("");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.resolve(1L, AiTaskType.TEXT));

        // 异常消息不能包含真实 key 片段
        assertFalse(ex.getMessage().contains("sk-"));
        assertTrue(ex.getMessage().contains("DEEPSEEK"), "exception should mention provider");
    }

    @Test
    void shouldThrowWhenQwenApiKeyNotConfigured() {
        props.getAi().getPrecise().setProvider("qwen");
        props.getQwen().setApiKey("");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.resolve(1L, AiTaskType.VISION));

        assertFalse(ex.getMessage().contains("sk-"));
        assertTrue(ex.getMessage().contains("QWEN"));
    }

    @Test
    void shouldThrowWhenMimoApiKeyNotConfigured() {
        props.getAi().getPrecise().setProvider("mimo");
        props.getMimo().setApiKey("");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.resolve(1L, AiTaskType.VISION));

        assertFalse(ex.getMessage().contains("sk-"));
        assertTrue(ex.getMessage().contains("MIMO"));
    }
}
