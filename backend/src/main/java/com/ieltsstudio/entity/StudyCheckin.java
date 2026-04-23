package com.ieltsstudio.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("study_checkins")
public class StudyCheckin {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private LocalDate checkinDate;

    @TableField
    private LocalDateTime createdAt;
}
