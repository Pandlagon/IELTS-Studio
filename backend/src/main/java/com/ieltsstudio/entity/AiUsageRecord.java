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
 * AI 调用流水实体，对应 {@code ai_usage_records} 表。
 *
 * <p>用于审计、调试与统计：每次 AI 调用记录任务类型、功能、cost、Key 模式、Provider、状态等。</p>
 *
 * <p><b>安全注意：</b></p>
 * <ul>
 *   <li>{@link #errorMessage} 必须是脱敏后的简短摘要，<b>禁止</b>记录 API Key、Authorization 头
 *       或 provider 完整响应体。</li>
 *   <li>本表不存储任何 API Key 字段。</li>
 * </ul>
 */
@Getter
@Setter
@ToString
@TableName("ai_usage_records")
public class AiUsageRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 任务类型：TEXT / VISION */
    private String taskType;

    /** 功能标识：grade-writing / ai-chat / translate / ... */
    private String feature;

    /** 本次调用消耗的 credits */
    private Integer cost;

    /** Key 模式：BUILTIN / USER */
    private String keyMode;

    /** Provider 标识（可为 null） */
    private String provider;

    /** 状态：SUCCESS / FAILED / REJECTED */
    private String status;

    /** 脱敏后的简短错误摘要，禁止记录 API Key */
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
