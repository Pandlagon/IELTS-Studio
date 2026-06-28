package com.ieltsstudio.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ieltsstudio.ai.AiKeyMode;
import com.ieltsstudio.ai.AiProviderType;
import com.ieltsstudio.ai.AiTaskType;
import com.ieltsstudio.ai.model.AiProviderPreset;
import com.ieltsstudio.ai.service.AiProviderRegistry;
import com.ieltsstudio.ai.util.AiApiKeyCrypto;
import com.ieltsstudio.dto.ai.AiProviderConfigRequest;
import com.ieltsstudio.dto.ai.AiProviderConfigResponse;
import com.ieltsstudio.dto.ai.AiProviderPresetResponse;
import com.ieltsstudio.dto.ai.AiSettingsResponse;
import com.ieltsstudio.dto.ai.AiSettingsUpdateRequest;
import com.ieltsstudio.entity.UserAiSettings;
import com.ieltsstudio.mapper.UserAiSettingsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户 AI 设置服务。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>获取当前用户 AI 设置（无记录时创建默认 BUILTIN 设置）。</li>
 *   <li>更新当前用户 AI 设置，安全地加密保存 API Key，只返回 masked key。</li>
 *   <li>列出可用 Provider 预设。</li>
 *   <li>向 {@link com.ieltsstudio.ai.service.AiSettingsService} 提供读取用户设置的能力。</li>
 * </ul>
 *
 * <p><b>安全：</b></p>
 * <ul>
 *   <li>Service 日志不输出明文 key。</li>
 *   <li>响应 {@link AiSettingsResponse} 不含 encrypted key，仅含 masked key 与 hasApiKey 标记。</li>
 *   <li>不信任前端传入 userId，userId 由 Controller 通过 {@code @AuthenticationPrincipal} 注入。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAiSettingsService {

    private final UserAiSettingsMapper userAiSettingsMapper;
    private final AiProviderRegistry aiProviderRegistry;
    private final AiApiKeyCrypto aiApiKeyCrypto;

    /**
     * 防御性校验：Controller 正常会传 {@code authUser.getId()}，
     * 但 Service 层自己也应拒绝 null userId，避免后续查询/写入出现 NPE 或脏数据。
     */
    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
    }

    /**
     * 获取当前用户 AI 设置；无记录时创建默认 BUILTIN 设置并返回。
     */
    public AiSettingsResponse getSettings(Long userId) {
        requireUserId(userId);
        return toResponse(getOrCreateEntity(userId));
    }

    /**
     * 更新当前用户 AI 设置，返回更新后的脱敏设置。
     *
     * <p>本阶段允许在 USER 模式下保存不完整配置（如先填 provider/baseUrl/model 暂不填 key）；
     * 真正解析 credentials 时（{@code AiSettingsService.resolve}）才校验 key 是否存在。</p>
     */
    public AiSettingsResponse updateSettings(Long userId, AiSettingsUpdateRequest request) {
        requireUserId(userId);
        AiKeyMode mode = parseKeyMode(request.getKeyMode());

        UserAiSettings entity = getOrCreateEntity(userId);
        entity.setKeyMode(mode.name());

        if (request.getText() != null) {
            applyProviderConfig(entity, request.getText(), AiTaskType.TEXT);
        }
        if (request.getVision() != null) {
            applyProviderConfig(entity, request.getVision(), AiTaskType.VISION);
        }

        userAiSettingsMapper.updateById(entity);
        log.debug("Updated AI settings for user={} keyMode={}", userId, mode);
        return toResponse(entity);
    }

    /**
     * 获取或创建用户设置实体。无记录时插入一条默认 BUILTIN 记录（不含任何 API Key）。
     */
    public UserAiSettings getOrCreateEntity(Long userId) {
        requireUserId(userId);
        UserAiSettings existing = findByUserId(userId);
        if (existing != null) {
            return existing;
        }
        UserAiSettings created = buildDefault(userId);
        userAiSettingsMapper.insert(created);
        log.debug("Created default BUILTIN AI settings for user={}", userId);
        return created;
    }

    /**
     * 列出按任务类型分组的 Provider 预设（含 OPENAI_COMPATIBLE 自定义模板）。
     *
     * @return {@code {"text": [...], "vision": [...]}}
     */
    public java.util.Map<String, List<AiProviderPresetResponse>> listProviderPresets() {
        List<AiProviderPresetResponse> text = new ArrayList<>();
        for (AiProviderPreset p : aiProviderRegistry.listPresetsByTaskType(AiTaskType.TEXT)) {
            text.add(toPresetResponse(p));
        }
        aiProviderRegistry.getPreset(AiProviderType.OPENAI_COMPATIBLE)
                .ifPresent(p -> text.add(toPresetResponse(p)));

        List<AiProviderPresetResponse> vision = new ArrayList<>();
        for (AiProviderPreset p : aiProviderRegistry.listPresetsByTaskType(AiTaskType.VISION)) {
            vision.add(toPresetResponse(p));
        }
        aiProviderRegistry.getPreset(AiProviderType.OPENAI_COMPATIBLE)
                .ifPresent(p -> vision.add(toPresetResponse(p)));

        return java.util.Map.of("text", text, "vision", vision);
    }

    // ─── 内部方法 ────────────────────────────────────────────────────────────

    private UserAiSettings findByUserId(Long userId) {
        return userAiSettingsMapper.selectOne(
                new LambdaQueryWrapper<UserAiSettings>()
                        .eq(UserAiSettings::getUserId, userId));
    }

    private UserAiSettings buildDefault(Long userId) {
        AiProviderPreset deepseek = aiProviderRegistry.getPreset(AiProviderType.DEEPSEEK)
                .orElseThrow(() -> new IllegalStateException("DeepSeek preset not registered"));
        AiProviderPreset qwen = aiProviderRegistry.getPreset(AiProviderType.QWEN)
                .orElseThrow(() -> new IllegalStateException("Qwen preset not registered"));

        UserAiSettings s = new UserAiSettings();
        s.setUserId(userId);
        s.setKeyMode(AiKeyMode.BUILTIN.name());
        s.setTextProvider(AiProviderType.DEEPSEEK.name());
        s.setTextBaseUrl(deepseek.getDefaultBaseUrl());
        s.setTextModel(deepseek.getDefaultModel());
        s.setVisionProvider(AiProviderType.QWEN.name());
        s.setVisionBaseUrl(qwen.getDefaultBaseUrl());
        s.setVisionModel(qwen.getDefaultModel());
        // encrypted / masked 保持 null：默认记录不含任何用户 API Key
        return s;
    }

    private void applyProviderConfig(UserAiSettings entity, AiProviderConfigRequest req, AiTaskType taskType) {
        AiProviderType type = parseProvider(req.getProvider());

        if (!aiProviderRegistry.supports(type, taskType)) {
            throw new IllegalArgumentException(
                    "Provider " + type + " does not support task type " + taskType);
        }

        AiProviderPreset preset = aiProviderRegistry.getPreset(type).orElse(null);
        boolean isCustom = preset != null && preset.isCustom();

        String baseUrl = req.getBaseUrl();
        String model = req.getModel();
        if (isCustom) {
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalArgumentException("baseUrl is required for custom provider");
            }
            if (model == null || model.isBlank()) {
                throw new IllegalArgumentException("model is required for custom provider");
            }
        } else if (preset != null) {
            if (baseUrl == null || baseUrl.isBlank()) baseUrl = preset.getDefaultBaseUrl();
            if (model == null || model.isBlank()) model = preset.getDefaultModel();
        }
        validateBaseUrl(baseUrl);
        // TODO: 完整 SSRF 内网校验（拒绝指向 127.0.0.1/10.x/172.16.x/192.168.x 等保留地址）留待后续阶段

        // ── apiKey 处理：clearApiKey=true 优先；其次非空 apiKey 加密保存；否则保留旧 key ──
        boolean clear = Boolean.TRUE.equals(req.getClearApiKey());
        String encrypted;
        String masked;
        if (clear) {
            encrypted = null;
            masked = null;
        } else if (req.getApiKey() != null && !req.getApiKey().isBlank()) {
            encrypted = aiApiKeyCrypto.encrypt(req.getApiKey());
            masked = aiApiKeyCrypto.mask(req.getApiKey());
        } else {
            encrypted = pick(entity, taskType, true);
            masked = pick(entity, taskType, false);
        }

        if (taskType == AiTaskType.TEXT) {
            entity.setTextProvider(type.name());
            entity.setTextBaseUrl(baseUrl);
            entity.setTextModel(model);
            entity.setTextApiKeyEncrypted(encrypted);
            entity.setTextApiKeyMasked(masked);
        } else {
            entity.setVisionProvider(type.name());
            entity.setVisionBaseUrl(baseUrl);
            entity.setVisionModel(model);
            entity.setVisionApiKeyEncrypted(encrypted);
            entity.setVisionApiKeyMasked(masked);
        }
    }

    /** 读取旧 encrypted (encrypted=true) 或 masked (encrypted=false) */
    private String pick(UserAiSettings entity, AiTaskType taskType, boolean encrypted) {
        if (taskType == AiTaskType.TEXT) {
            return encrypted ? entity.getTextApiKeyEncrypted() : entity.getTextApiKeyMasked();
        }
        return encrypted ? entity.getVisionApiKeyEncrypted() : entity.getVisionApiKeyMasked();
    }

    private void validateBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) return;
        String lower = baseUrl.trim().toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new IllegalArgumentException("baseUrl must start with http:// or https://");
        }
    }

    private AiKeyMode parseKeyMode(String keyMode) {
        if (keyMode == null || keyMode.isBlank()) {
            throw new IllegalArgumentException("keyMode must not be blank");
        }
        try {
            return AiKeyMode.valueOf(keyMode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported keyMode: " + keyMode);
        }
    }

    private AiProviderType parseProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        try {
            return AiProviderType.valueOf(provider.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported provider: " + provider);
        }
    }

    private AiSettingsResponse toResponse(UserAiSettings entity) {
        return AiSettingsResponse.builder()
                .keyMode(entity.getKeyMode())
                .text(toConfigResponse(entity, AiTaskType.TEXT))
                .vision(toConfigResponse(entity, AiTaskType.VISION))
                .build();
    }

    private AiProviderConfigResponse toConfigResponse(UserAiSettings entity, AiTaskType taskType) {
        String providerName = taskType == AiTaskType.TEXT
                ? entity.getTextProvider() : entity.getVisionProvider();
        String baseUrl = taskType == AiTaskType.TEXT
                ? entity.getTextBaseUrl() : entity.getVisionBaseUrl();
        String model = taskType == AiTaskType.TEXT
                ? entity.getTextModel() : entity.getVisionModel();
        String encrypted = taskType == AiTaskType.TEXT
                ? entity.getTextApiKeyEncrypted() : entity.getVisionApiKeyEncrypted();
        String masked = taskType == AiTaskType.TEXT
                ? entity.getTextApiKeyMasked() : entity.getVisionApiKeyMasked();

        String displayName = providerName;
        boolean custom = false;
        if (providerName != null) {
            try {
                AiProviderType type = AiProviderType.valueOf(providerName.trim().toUpperCase());
                AiProviderPreset preset = aiProviderRegistry.getPreset(type).orElse(null);
                if (preset != null) {
                    displayName = preset.getDisplayName();
                    custom = preset.isCustom();
                }
            } catch (IllegalArgumentException ignored) {
                // provider 名无法解析时保留原名展示
            }
        }

        return AiProviderConfigResponse.builder()
                .provider(providerName)
                .displayName(displayName)
                .baseUrl(baseUrl)
                .model(model)
                .hasApiKey(encrypted != null && !encrypted.isBlank())
                .maskedApiKey(masked)
                .custom(custom)
                .build();
    }

    private AiProviderPresetResponse toPresetResponse(AiProviderPreset preset) {
        return AiProviderPresetResponse.builder()
                .provider(preset.getProvider().name())
                .displayName(preset.getDisplayName())
                .taskType(preset.getTaskType() != null ? preset.getTaskType().name() : null)
                .defaultBaseUrl(preset.getDefaultBaseUrl())
                .defaultModel(preset.getDefaultModel())
                .tokenField(preset.getTokenField())
                .supportsVision(preset.isSupportsVision())
                .custom(preset.isCustom())
                .build();
    }
}
