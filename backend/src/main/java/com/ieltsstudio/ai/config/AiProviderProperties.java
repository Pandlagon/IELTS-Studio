package com.ieltsstudio.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI Provider 配置绑定，读取 {@code application-ai.yml} 中的现有站点配置。
 *
 * <p>本阶段只读不写，且<b>不删除</b>任何现有配置项（旧 Service 仍在直接使用 {@code @Value}）。
 * 该类仅为新的 AI 抽象层（{@link com.ieltsstudio.ai.service.AiSettingsService}）提供集中入口。</p>
 *
 * <p>配置前缀与 {@code application-ai.yml} 对应：</p>
 * <ul>
 *   <li>{@code ai.deepseek.*} → {@link #getDeepseek()}</li>
 *   <li>{@code ai.precise.provider} → {@link #getPrecise()}</li>
 *   <li>{@code qwen.*} → {@link #getQwen()}</li>
 *   <li>{@code mimo.*} → {@link #getMimo()}</li>
 * </ul>
 *
 * <p>注意：因 {@code spring-boot-configuration-processor} 未引入（不改动依赖管理），
 * 此处通过 {@code @Configuration + @ConfigurationProperties} 在运行时注册，无 IDE 元数据也能工作。</p>
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "")
public class AiProviderProperties {

    /** {@code ai.*} 段 */
    private Ai ai = new Ai();

    /** {@code qwen.*} 段 */
    private Qwen qwen = new Qwen();

    /** {@code mimo.*} 段 */
    private Mimo mimo = new Mimo();

    @Getter
    @Setter
    public static class Ai {
        private Deepseek deepseek = new Deepseek();
        private Precise precise = new Precise();
    }

    @Getter
    @Setter
    public static class Deepseek {
        /** DeepSeek API Key（敏感，不入日志） */
        private String apiKey;
        private String baseUrl = "https://api.deepseek.com";
        private String model = "deepseek-chat";
    }

    @Getter
    @Setter
    public static class Precise {
        /** 精准解析 Provider 选择：qwen / mimo */
        private String provider = "qwen";
    }

    @Getter
    @Setter
    public static class Qwen {
        /** Qwen API Key（敏感，不入日志） */
        private String apiKey;
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        private String model = "qwen3.6-plus";
    }

    @Getter
    @Setter
    public static class Mimo {
        /** MiMO API Key（敏感，不入日志） */
        private String apiKey;
        private String baseUrl = "https://api.xiaomimimo.com/v1";
        private String model = "mimo-v2.5";
    }
}
