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
 * 用户 AI 内置额度快照实体，对应 {@code ai_usage_quota} 表。
 *
 * <p>记录每用户当前周期的 credits 发放与已用额度。本阶段仅建表与 Entity/Mapper，
 * 扣费逻辑留待后续阶段（Phase 3B/3C）。</p>
 *
 * <p>无敏感字段，{@link #toString()} 可安全输出全部字段。</p>
 */
@Getter
@Setter
@ToString
@TableName("ai_usage_quota")
public class AiUsageQuota {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private LocalDateTime periodStart;

    private LocalDateTime periodEnd;

    /** 周期发放额度，默认 100 */
    private Integer creditsTotal;

    /** 周期已使用额度 */
    private Integer creditsUsed;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
