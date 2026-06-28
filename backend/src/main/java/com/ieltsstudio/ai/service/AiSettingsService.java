package com.ieltsstudio.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ieltsstudio.ai.AiKeyMode;
import com.ieltsstudio.ai.AiProviderType;
import com.ieltsstudio.ai.AiTaskType;
import com.ieltsstudio.ai.config.AiProviderProperties;
import com.ieltsstudio.ai.model.AiCredentials;
import com.ieltsstudio.ai.model.AiProviderPreset;
import com.ieltsstudio.ai.util.AiApiKeyCrypto;
import com.ieltsstudio.entity.UserAiSettings;
import com.ieltsstudio.mapper.UserAiSettingsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * AI 凭据解析服务。
 *
 * <p>支持两种模式：</p>
 * <ul>
 *   <li><b>BUILTIN</b>：从 {@code application-ai.yml} 读取站点内置配置，构造 {@link AiCredentials}。</li>
 *   <li><b>USER</b>：从 {@code user_ai_settings} 读取用户自填配置，解密 API Key 后构造 {@link AiCredentials}。</li>
 * </ul>
 *
 * <p>BUILTIN 解析规则：</p>
 * <ul>
 *   <li>{@link AiTaskType#TEXT} → DeepSeek（{@code ai.deepseek.*}）</li>
 *   <li>{@link AiTaskType#VISION} → 根据 {@code ai.precise.provider} 返回 Qwen 或 MiMO</li>
 * </ul>
 *
 * <p>USER 解析规则：读取用户对应任务类型的 provider / baseUrl / model / encrypted key，
 * 解密后注入 {@link AiCredentials}。任何字段缺失或解密失败都抛出清晰异常，
 * <b>异常信息不包含 key / 密文</b>。</p>
 *
 * <p>降级规则：{@code userId == null}、无用户设置记录、或 {@code keyMode != USER} 时，回退到 BUILTIN。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSettingsService {

    private final AiProviderProperties properties;
    private final AiProviderRegistry registry;
    private final UserAiSettingsMapper userAiSettingsMapper;
    private final AiApiKeyCrypto aiApiKeyCrypto;

    /**
     * 解析某用户在某任务类型下的凭据。
     *
     * @param userId   登录用户 ID（为 null 时回退 BUILTIN）
     * @param taskType 任务类型
     * @return 凭据快照（apiKey 仅存在于后端内存）
     */
    public AiCredentials resolve(Long userId, AiTaskType taskType) {
        if (taskType == null) {
            throw new IllegalArgumentException("AiTaskType must not be null");
        }
        if (userId == null) {
            return resolveBuiltin(null, taskType);
        }
        UserAiSettings settings = findUserSettings(userId);
        if (settings == null || !AiKeyMode.USER.name().equals(settings.getKeyMode())) {
            // 无用户设置或非 USER 模式：回退站点内置配置
            return resolveBuiltin(userId, taskType);
        }
        return resolveUser(userId, taskType, settings);
    }

    private UserAiSettings findUserSettings(Long userId) {
        return userAiSettingsMapper.selectOne(
                new LambdaQueryWrapper<UserAiSettings>()
                        .eq(UserAiSettings::getUserId, userId));
    }

    /**
     * 解析 USER 模式凭据。任何字段缺失 / 解密失败抛 {@link IllegalStateException}，
     * 异常信息<b>不</b>包含 key 或密文。
     */
    private AiCredentials resolveUser(Long userId, AiTaskType taskType, UserAiSettings settings) {
        String providerName;
        String baseUrl;
        String model;
        String encrypted;
        switch (taskType) {
            case TEXT -> {
                providerName = settings.getTextProvider();
                baseUrl = settings.getTextBaseUrl();
                model = settings.getTextModel();
                encrypted = settings.getTextApiKeyEncrypted();
            }
            case VISION -> {
                providerName = settings.getVisionProvider();
                baseUrl = settings.getVisionBaseUrl();
                model = settings.getVisionModel();
                encrypted = settings.getVisionApiKeyEncrypted();
            }
            default -> throw new IllegalArgumentException("Unsupported AiTaskType: " + taskType);
        }

        if (providerName == null || providerName.isBlank()) {
            throw new IllegalStateException(
                    "User AI provider is not configured for taskType=" + taskType);
        }
        AiProviderType type;
        try {
            type = AiProviderType.valueOf(providerName.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "User AI provider is not valid for taskType=" + taskType);
        }
        // 校验 provider 支持当前 taskType（如 TEXT 任务不允许用 Qwen）。
        // 必须在解密 API Key 之前完成，避免对脏数据做无谓解密；异常信息不含 key / 密文。
        if (!registry.supports(type, taskType)) {
            throw new IllegalStateException(
                    "User AI provider does not support taskType=" + taskType);
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "User AI base URL is not configured for taskType=" + taskType);
        }
        if (model == null || model.isBlank()) {
            throw new IllegalStateException(
                    "User AI model is not configured for taskType=" + taskType);
        }
        if (encrypted == null || encrypted.isBlank()) {
            throw new IllegalStateException(
                    "User AI API key is not configured for taskType=" + taskType);
        }
        // 解密失败时 AiApiKeyCrypto 抛通用异常，不含 key / 密文
        String apiKey = aiApiKeyCrypto.decrypt(encrypted);

        // tokenField：优先取 preset；MiMO = max_completion_tokens，其余默认 max_tokens
        String tokenField = registry.getPreset(type)
                .map(AiProviderPreset::getTokenField)
                .orElse("max_tokens");

        AiCredentials creds = AiCredentials.builder()
                .keyMode(AiKeyMode.USER)
                .provider(type)
                .taskType(taskType)
                .baseUrl(baseUrl)
                .model(model)
                .apiKey(apiKey)
                .tokenField(tokenField)
                .build();
        log.debug("Resolved USER {} credentials for user={} provider={}", taskType, userId, type);
        return creds;
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
