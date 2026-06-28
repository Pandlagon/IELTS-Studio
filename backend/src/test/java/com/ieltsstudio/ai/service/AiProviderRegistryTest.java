package com.ieltsstudio.ai.service;

import com.ieltsstudio.ai.AiProviderType;
import com.ieltsstudio.ai.AiTaskType;
import com.ieltsstudio.ai.model.AiProviderPreset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AiProviderRegistry} 单元测试。
 *
 * <p>不依赖数据库 / Spring 容器，直接构造并触发 {@code @PostConstruct}。</p>
 */
class AiProviderRegistryTest {

    private AiProviderRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AiProviderRegistry();
        registry.initBuiltinPresets();
    }

    @Test
    void shouldGetDeepSeekPreset() {
        Optional<AiProviderPreset> preset = registry.getPreset(AiProviderType.DEEPSEEK);
        assertTrue(preset.isPresent());
        assertEquals("DeepSeek", preset.get().getDisplayName());
    }

    @Test
    void shouldGetQwenPreset() {
        Optional<AiProviderPreset> preset = registry.getPreset(AiProviderType.QWEN);
        assertTrue(preset.isPresent());
        assertEquals("Qwen", preset.get().getDisplayName());
    }

    @Test
    void shouldGetMimoPreset() {
        Optional<AiProviderPreset> preset = registry.getPreset(AiProviderType.MIMO);
        assertTrue(preset.isPresent());
        assertEquals("MiMO", preset.get().getDisplayName());
    }

    @Test
    void shouldReturnEmptyForNullProviderType() {
        assertTrue(registry.getPreset(null).isEmpty());
    }

    @Test
    void deepSeekShouldSupportTextNotVision() {
        assertTrue(registry.supports(AiProviderType.DEEPSEEK, AiTaskType.TEXT));
        assertFalse(registry.supports(AiProviderType.DEEPSEEK, AiTaskType.VISION));
    }

    @Test
    void qwenShouldSupportVision() {
        assertTrue(registry.supports(AiProviderType.QWEN, AiTaskType.VISION));
    }

    @Test
    void mimoShouldSupportVision() {
        assertTrue(registry.supports(AiProviderType.MIMO, AiTaskType.VISION));
    }

    @Test
    void mimoShouldUseMaxCompletionTokens() {
        AiProviderPreset preset = registry.getPreset(AiProviderType.MIMO).orElseThrow();
        assertEquals("max_completion_tokens", preset.getTokenField());
    }

    @Test
    void deepSeekAndQwenShouldUseMaxTokens() {
        assertEquals("max_tokens", registry.getPreset(AiProviderType.DEEPSEEK).orElseThrow().getTokenField());
        assertEquals("max_tokens", registry.getPreset(AiProviderType.QWEN).orElseThrow().getTokenField());
    }

    @Test
    void listPresetsByTaskTypeTextShouldContainDeepSeek() {
        List<AiProviderPreset> textPresets = registry.listPresetsByTaskType(AiTaskType.TEXT);
        assertTrue(textPresets.stream().anyMatch(p -> p.getProvider() == AiProviderType.DEEPSEEK));
    }

    @Test
    void listPresetsByTaskTypeVisionShouldContainQwenAndMimo() {
        List<AiProviderPreset> visionPresets = registry.listPresetsByTaskType(AiTaskType.VISION);
        assertTrue(visionPresets.stream().anyMatch(p -> p.getProvider() == AiProviderType.QWEN));
        assertTrue(visionPresets.stream().anyMatch(p -> p.getProvider() == AiProviderType.MIMO));
    }

    @Test
    void openAiCompatibleShouldSupportBothTaskTypes() {
        // 自定义模板默认支持 TEXT 与 VISION
        assertTrue(registry.supports(AiProviderType.OPENAI_COMPATIBLE, AiTaskType.TEXT));
        assertTrue(registry.supports(AiProviderType.OPENAI_COMPATIBLE, AiTaskType.VISION));
    }

    @Test
    void listPresetsShouldContainAllBuiltinProviders() {
        List<AiProviderPreset> all = registry.listPresets();
        assertTrue(all.size() >= 4);
        assertTrue(all.stream().anyMatch(p -> p.getProvider() == AiProviderType.DEEPSEEK));
        assertTrue(all.stream().anyMatch(p -> p.getProvider() == AiProviderType.QWEN));
        assertTrue(all.stream().anyMatch(p -> p.getProvider() == AiProviderType.MIMO));
        assertTrue(all.stream().anyMatch(p -> p.getProvider() == AiProviderType.OPENAI_COMPATIBLE));
    }
}
