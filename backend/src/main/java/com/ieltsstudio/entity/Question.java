package com.ieltsstudio.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("questions")
public class Question {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long examId;

    private Long sectionId;

    private Integer questionNumber;

    private String type; // tfng, mcq, fill

    private String questionText;

    private String options; // JSON array for MCQ

    private String answer; // correct answer

    private String explanation;

    private String locatorText; // text in passage for highlighting

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
