package com.ieltsstudio.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 用户 AI 设置实体，对应 {@code user_ai_settings} 表。
 *
 * <p>保存每个用户的 AI 使用模式（{@code BUILTIN} / {@code USER}）与自填 Provider 配置。
 * 一对一关系：一个用户只有一份设置。</p>
 *
 * <p><b>安全注意：</b></p>
 * <ul>
 *   <li>{@link #textApiKeyEncrypted} / {@link #visionApiKeyEncrypted} 仅为加密密文，
 *       {@link #toString()} 已通过 {@link ToString.Exclude} 排除这两个字段，
 *       避免密文被无意中打印到日志或异常栈。</li>
 *   <li>密文<b>禁止</b>返回前端，前端只展示 {@code *_api_key_masked} 脱敏串。</li>
 *   <li>明文 Key 仅在后端内存中短暂存在（解密后即用即弃）。</li>
 * </ul>
 */
@Getter
@Setter
@ToString
@TableName("user_ai_settings")
public class UserAiSettings {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** Key 模式：BUILTIN / USER */
    private String keyMode;

    // ─── Text Provider 配置（USER 模式生效）──────────────────────────────────
    /** 文本 Provider 标识：DEEPSEEK / OPENAI_COMPATIBLE */
    private String textProvider;
    private String textBaseUrl;
    private String textModel;

    /** 文本 API Key 加密密文，禁止返回前端；已排除出 toString */
    @ToString.Exclude
    private String textApiKeyEncrypted;

    /** 文本 API Key 脱敏串，可返回前端展示，如 sk-****abcd */
    private String textApiKeyMasked;

    // ─── Vision Provider 配置（USER 模式生效）────────────────────────────────
    /** 视觉 Provider 标识：QWEN / MIMO / OPENAI_COMPATIBLE */
    private String visionProvider;
    private String visionBaseUrl;
    private String visionModel;

    /** 视觉 API Key 加密密文，禁止返回前端；已排除出 toString */
    @ToString.Exclude
    private String visionApiKeyEncrypted;

    /** 视觉 API Key 脱敏串，可返回前端展示 */
    private String visionApiKeyMasked;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
