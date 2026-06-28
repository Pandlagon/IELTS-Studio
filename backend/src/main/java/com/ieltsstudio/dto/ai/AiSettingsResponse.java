package com.ieltsstudio.dto.ai;

import lombok.Builder;
import lombok.Getter;

/**
 * 当前用户 AI 设置的脱敏响应。
 *
 * <p>包含 keyMode 与 text / vision 两组 Provider 配置（均脱敏）。</p>
 */
@Getter
@Builder
public class AiSettingsResponse {

    /** Key 模式：BUILTIN / USER */
    private String keyMode;

    /** 文本 Provider 配置（脱敏） */
    private AiProviderConfigResponse text;

    /** 视觉 Provider 配置（脱敏） */
    private AiProviderConfigResponse vision;
}
