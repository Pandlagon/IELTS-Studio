package com.ieltsstudio.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充配置
 *
 * <p>对实体类中标注了 {@code @TableField(fill = FieldFill.INSERT)} 或
 * {@code @TableField(fill = FieldFill.INSERT_UPDATE)} 的字段，
 * 在插入/更新时自动填充当前时间，无需手动赋值。
 *
 * <p>支持的自动填充字段：
 * <ul>
 *   <li>{@code createdAt}  — 插入时填充</li>
 *   <li>{@code updatedAt}  — 插入和更新时填充</li>
 *   <li>{@code submittedAt} — 插入时填充（考试提交时间）</li>
 * </ul>
 */
@Component
public class MyBatisPlusConfig implements MetaObjectHandler {

    /**
     * 插入时自动填充时间字段
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "submittedAt", LocalDateTime.class, now);
    }

    /**
     * 更新时自动刷新 updatedAt 字段
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
