package com.ieltsstudio.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("exams")
public class Exam {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String description;

    private String type; // reading, listening, writing

    private String status; // uploading, processing, ready, error

    private Integer questionCount;

    private Integer duration;

    private String difficulty;

    private String fileKey; // MinIO file key

    private String parseResult; // JSON: parsed exam data

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
