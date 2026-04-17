package com.ieltsstudio.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("exam_records")
public class ExamRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long examId;

    private Integer correctCount;

    private Integer totalCount;

    private Double bandScore;

    private Integer timeUsed; // seconds

    private String answersJson; // JSON: {questionId: userAnswer}

    private String aiFeedbackJson; // JSON: {questionId: {band, taskAchievement, ...}}

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime submittedAt;

    /** Populated by JOIN queries, not stored in DB */
    @TableField(exist = false)
    private String examTitle;

    @TableField(exist = false)
    private String examType;
}
