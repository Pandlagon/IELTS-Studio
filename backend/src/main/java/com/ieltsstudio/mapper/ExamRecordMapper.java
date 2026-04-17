package com.ieltsstudio.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ieltsstudio.entity.ExamRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ExamRecordMapper extends BaseMapper<ExamRecord> {

    @Select("SELECT r.*, e.title AS examTitle, e.type AS examType " +
            "FROM exam_records r LEFT JOIN exams e ON r.exam_id = e.id " +
            "WHERE r.user_id = #{userId} ORDER BY r.submitted_at DESC")
    List<ExamRecord> findByUserId(Long userId);

    @Select("SELECT r.*, e.title AS examTitle, e.type AS examType " +
            "FROM exam_records r LEFT JOIN exams e ON r.exam_id = e.id " +
            "WHERE r.id = #{id}")
    ExamRecord findByIdWithExam(Long id);

    @Select("SELECT AVG(band_score) FROM exam_records WHERE user_id = #{userId}")
    Double avgBandByUserId(Long userId);
}
