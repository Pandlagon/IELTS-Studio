package com.ieltsstudio.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("word_study_states")
public class WordStudyState {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String bookId;

    private String knownIds;

    private String unknownIds;

    private String reviewStates;

    private String errorCounts;

    private String sortMode;

    private Integer batchSize;

    private Integer batchIndex;

    private Integer currentIndex;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
