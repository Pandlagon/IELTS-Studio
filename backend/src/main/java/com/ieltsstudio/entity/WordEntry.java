package com.ieltsstudio.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("word_entries")
public class WordEntry {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bookId;

    private Long userId;

    private String word;

    private String phonetic;

    private String pos;      // e.g. "v.", "adj.", "n."

    private String posType;  // e.g. "v", "adj", "n"

    private String meaning;

    private String example;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
