package com.ieltsstudio.ai.service;

import com.ieltsstudio.ai.AiProviderType;
import com.ieltsstudio.ai.AiTaskType;
import com.ieltsstudio.ai.model.AiProviderPreset;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Provider 预设注册表。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>维护内置 Provider preset（DeepSeek / Qwen / MiMO / OPENAI_COMPATIBLE 模板）。</li>
 *   <li>按 {@link AiProviderType} 查找默认配置。</li>
 *   <li>校验 Provider 是否支持对应 {@link AiTaskType}。</li>
 *   <li>为后续用户自定义 Provider 留接口（{@link #register(AiProviderPreset)}）。</li>
 * </ul>
 *
 * <p>本类不读取 API Key，Key 由 {@link AiSettingsService} 在调用时注入。</p>
 */
@Slf4j
@Component
public class AiProviderRegistry {

    private final Map<AiProviderType, AiProviderPreset> presets = new EnumMap<>(AiProviderType.class);

    @PostConstruct
    public void initBuiltinPresets() {
        register(AiProviderPreset.builder()
                .provider(AiProviderType.DEEPSEEK)
                .displayName("DeepSeek")
                .taskType(AiTaskType.TEXT)
                .defaultBaseUrl("https://api.deepseek.com")
                .defaultModel("deepseek-chat")
                .tokenField("max_tokens")
                .supportsVision(false)
                .custom(false)
                .build());

        register(AiProviderPreset.builder()
                .provider(AiProviderType.QWEN)
                .displayName("Qwen")
                .taskType(AiTaskType.VISION)
                .defaultBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .defaultModel("qwen3.6-plus")
                .tokenField("max_tokens")
                .supportsVision(true)
                .custom(false)
                .build());

        register(AiProviderPreset.builder()
                .provider(AiProviderType.MIMO)
                .displayName("MiMO")
                .taskType(AiTaskType.VISION)
                .defaultBaseUrl("https://api.xiaomimimo.com/v1")
                .defaultModel("mimo-v2.5")
                .tokenField("max_completion_tokens")
                .supportsVision(true)
                .custom(false)
                .build());

        // 自定义 OpenAI-compatible 模板：baseUrl / model 由用户后续填写
        register(AiProviderPreset.builder()
                .provider(AiProviderType.OPENAI_COMPATIBLE)
                .displayName("OpenAI-compatible (custom)")
                .taskType(null)
                .defaultBaseUrl(null)
                .defaultModel(null)
                .tokenField("max_tokens")
                .supportsVision(true)
                .custom(true)
                .build());

        log.info("AiProviderRegistry initialized with {} presets", presets.size());
    }

    /**
     * 注册一个 preset。本阶段主要为内置 preset 服务；后续阶段可由用户自定义 Provider 注入。
     */
    public void register(AiProviderPreset preset) {
        if (preset == null || preset.getProvider() == null) {
            throw new IllegalArgumentException("preset and preset.provider must not be null");
        }
        presets.put(preset.getProvider(), preset);
    }

    /**
     * 根据 Provider 类型查找 preset。
     *
     * @return preset；不存在时返回 {@link Optional#empty()}
     */
    public Optional<AiProviderPreset> getPreset(AiProviderType providerType) {
        if (providerType == null) return Optional.empty();
        return Optional.ofNullable(presets.get(providerType));
    }

    /**
     * 列出所有 preset（含自定义模板）。
     */
    public List<AiProviderPreset> listPresets() {
        return List.copyOf(presets.values());
    }

    /**
     * 按任务类型筛选 preset。
     *
     * <p>规则：taskType 为 null 的自定义模板（OPENAI_COMPATIBLE）不会被返回，
     * 因为它的任务类型由用户配置时决定，不属于某一固定任务类型的预设。</p>
     */
    public List<AiProviderPreset> listPresetsByTaskType(AiTaskType taskType) {
        if (taskType == null) return List.of();
        return presets.values().stream()
                .filter(p -> taskType.equals(p.getTaskType()))
                .toList();
    }

    /**
     * 校验某 Provider 是否支持给定任务类型。
     *
     * <p>规则：</p>
     * <ul>
     *   <li>自定义模板（OPENAI_COMPATIBLE）默认支持 TEXT 与 VISION（具体能力由用户配置的模型决定）。</li>
     *   <li>其它 Provider：当 preset.taskType == taskType，或 preset.supportsVision && taskType==VISION 时支持。</li>
     *   <li>未注册的 Provider 返回 false。</li>
     * </ul>
     */
    public boolean supports(AiProviderType providerType, AiTaskType taskType) {
        if (providerType == null || taskType == null) return false;
        AiProviderPreset preset = presets.get(providerType);
        if (preset == null) return false;
        if (preset.isCustom()) return true;
        if (taskType.equals(preset.getTaskType())) return true;
        return taskType == AiTaskType.VISION && preset.isSupportsVision();
    }
}
