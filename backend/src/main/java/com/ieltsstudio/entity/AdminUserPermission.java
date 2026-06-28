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
 * Admin 用户权限实体，对应 {@code admin_user_permissions} 表（Phase 8C）。
 *
 * <p>记录某个 ADMIN 账号被分配的后台细粒度权限。{@link #permission} 字段存储
 * {@link AdminPermission} 枚举名。{@code (user_id, permission)} 唯一约束防重复。</p>
 *
 * <p>无敏感字段，{@link #toString()} 可安全输出全部字段。</p>
 */
@Getter
@Setter
@ToString
@TableName("admin_user_permissions")
public class AdminUserPermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 权限名，对应 {@link AdminPermission} 枚举的 {@link Enum#name()} */
    private String permission;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
