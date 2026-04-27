package com.ieltsstudio.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("exam_collection_items")
public class ExamCollectionItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long collectionId;

    private Long examId;

    /** Display order within the collection (0-based) */
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** Populated by JOIN, not stored */
    @TableField(exist = false)
    private String examTitle;

    @TableField(exist = false)
    private String examType;

    @TableField(exist = false)
    private String examStatus;

    @TableField(exist = false)
    private Integer questionCount;

    @TableField(exist = false)
    private Integer examDuration;
}
