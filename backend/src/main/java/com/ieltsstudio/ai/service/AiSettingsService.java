package com.ieltsstudio.ai.service;

import com.ieltsstudio.ai.AiKeyMode;
import com.ieltsstudio.ai.AiProviderType;
import com.ieltsstudio.ai.AiTaskType;
import com.ieltsstudio.ai.config.AiProviderProperties;
import com.ieltsstudio.ai.model.AiCredentials;
import com.ieltsstudio.ai.model.AiProviderPreset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * AI 凭据解析服务。
 *
 * <p>本阶段只做 <b>BUILTIN</b> 模式：从 {@code application-ai.yml} 读取站点内置配置，
 * 构造 {@link AiCredentials}。</p>
 *
 * <p>解析规则：</p>
 * <ul>
 *   <li>{@link AiTaskType#TEXT} → DeepSeek（{@code ai.deepseek.*}）</li>
 *   <li>{@link AiTaskType#VISION} → 根据 {@code ai.precise.provider} 返回 Qwen 或 MiMO</li>
 *   <li>API Key 未配置时抛出清晰异常，<b>不</b>泄露任何 key 片段</li>
 * </ul>
 *
 * <p><b>userId 暂不用于查库</b>，但方法签名保留，方便下一阶段接入 {@code user_ai_settings}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSettingsService {

    private final AiProviderProperties properties;
    private final AiProviderRegistry registry;

    /**
     * 解析某用户在某任务类型下的凭据。
     *
     * @param userId   登录用户 ID（本阶段未使用，预留）
     * @param taskType 任务类型
     * @return 凭据快照（apiKey 仅存在于后端内存）
     */
    public AiCredentials resolve(Long userId, AiTaskType taskType) {
        if (taskType == null) {
            throw new IllegalArgumentException("AiTaskType must not be null");
        }
        // 本阶段固定 BUILTIN；USER 模式留待后续阶段（user_ai_settings 接入）
        return resolveBuiltin(userId, taskType);
    }

    private AiCredentials resolveBuiltin(Long userId, AiTaskType taskType) {
        return switch (taskType) {
            case TEXT -> resolveDeepseek(userId);
            case VISION -> resolveVision(userId);
        };
    }

    private AiCredentials resolveDeepseek(Long userId) {
        AiProviderProperties.Deepseek cfg = properties.getAi().getDeepseek();
        AiProviderPreset preset = requirePreset(AiProviderType.DEEPSEEK);
        requireApiKey(AiProviderType.DEEPSEEK, cfg.getApiKey());

        AiCredentials creds = AiCredentials.builder()
                .keyMode(AiKeyMode.BUILTIN)
                .provider(AiProviderType.DEEPSEEK)
                .taskType(AiTaskType.TEXT)
                .baseUrl(orDefault(cfg.getBaseUrl(), preset.getDefaultBaseUrl()))
                .model(orDefault(cfg.getModel(), preset.getDefaultModel()))
                .apiKey(cfg.getApiKey())
                .tokenField(orDefault(preset.getTokenField(), "max_tokens"))
                .build();
        log.debug("Resolved BUILTIN TEXT credentials for user={} provider={}", userId, AiProviderType.DEEPSEEK);
        return creds;
    }

    private AiCredentials resolveVision(Long userId) {
        String providerName = properties.getAi().getPrecise().getProvider();
        boolean useMimo = providerName != null && providerName.trim().equalsIgnoreCase("mimo");
        AiProviderType type = useMimo ? AiProviderType.MIMO : AiProviderType.QWEN;
        AiProviderPreset preset = requirePreset(type);

        String apiKey;
        String baseUrl;
        String model;
        if (useMimo) {
            AiProviderProperties.Mimo cfg = properties.getMimo();
            apiKey = cfg.getApiKey();
            baseUrl = cfg.getBaseUrl();
            model = cfg.getModel();
        } else {
            AiProviderProperties.Qwen cfg = properties.getQwen();
            apiKey = cfg.getApiKey();
            baseUrl = cfg.getBaseUrl();
            model = cfg.getModel();
        }
        requireApiKey(type, apiKey);

        AiCredentials creds = AiCredentials.builder()
                .keyMode(AiKeyMode.BUILTIN)
                .provider(type)
                .taskType(AiTaskType.VISION)
                .baseUrl(orDefault(baseUrl, preset.getDefaultBaseUrl()))
                .model(orDefault(model, preset.getDefaultModel()))
                .apiKey(apiKey)
                .tokenField(orDefault(preset.getTokenField(), "max_tokens"))
                .build();
        log.debug("Resolved BUILTIN VISION credentials for user={} provider={}", userId, type);
        return creds;
    }

    private AiProviderPreset requirePreset(AiProviderType type) {
        Optional<AiProviderPreset> preset = registry.getPreset(type);
        if (preset.isEmpty()) {
            throw new IllegalStateException("No preset registered for provider=" + type);
        }
        return preset.get();
    }

    /**
     * 校验 API Key 已配置。异常消息<b>不</b>包含 key 本身或任何 key 片段。
     */
    private void requireApiKey(AiProviderType provider, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "AI API Key is not configured for provider=" + provider
                            + ". Please set it in application-ai.yml / application-local.yml.");
        }
    }

    private String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
