package com.ieltsstudio.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ieltsstudio.entity.Exam;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ExamMapper extends BaseMapper<Exam> {

    @Select("SELECT * FROM exams WHERE user_id = #{userId} AND deleted = 0 ORDER BY created_at DESC")
    List<Exam> findByUserId(Long userId);

    @Select("SELECT * FROM exams WHERE deleted = 0 AND status = 'ready' ORDER BY created_at DESC LIMIT #{limit}")
    List<Exam> findPublicExams(int limit);
}
