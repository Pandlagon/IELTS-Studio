package com.ieltsstudio.ai.model;

import com.ieltsstudio.ai.AiKeyMode;
import com.ieltsstudio.ai.AiProviderType;
import com.ieltsstudio.ai.AiTaskType;
import lombok.Builder;
import lombok.Getter;

/**
 * 一次 AI 调用的凭据快照。
 *
 * <p>由 {@link com.ieltsstudio.ai.service.AiSettingsService} 在运行时构造：
 * BUILTIN 模式从站点配置注入；USER 模式从 {@code user_ai_settings} 解密后注入。</p>
 *
 * <p><b>安全注意：</b>{@link #apiKey} 仅在后端内存中使用，
 * {@link #toString()} 已手写脱敏，不得通过任何途径返回前端或写入日志。</p>
 */
@Getter
@Builder
public class AiCredentials {

    /** Key 模式：BUILTIN / USER */
    private final AiKeyMode keyMode;

    /** Provider 类型 */
    private final AiProviderType provider;

    /** 任务类型 */
    private final AiTaskType taskType;

    /** Base URL（不带尾部 /chat/completions） */
    private final String baseUrl;

    /** 模型名 */
    private final String model;

    /**
     * API Key 明文，仅存在于后端内存。
     * <b>禁止</b>写入日志、禁止返回前端。
     */
    private final String apiKey;

    /** Token 字段名：{@code max_tokens} 或 {@code max_completion_tokens} */
    private final String tokenField;

    /**
     * 脱敏 toString：只输出 key 是否已配置，不输出 key 本身。
     * 避免被无意中打印到日志或异常栈。
     */
    @Override
    public String toString() {
        boolean keyPresent = apiKey != null && !apiKey.isEmpty();
        return "AiCredentials{keyMode=" + keyMode
                + ", provider=" + provider
                + ", taskType=" + taskType
                + ", baseUrl='" + baseUrl + '\''
                + ", model='" + model + '\''
                + ", apiKey=" + (keyPresent ? "<masked>" : "<empty>")
                + ", tokenField='" + tokenField + '\'' + '}';
    }
}
