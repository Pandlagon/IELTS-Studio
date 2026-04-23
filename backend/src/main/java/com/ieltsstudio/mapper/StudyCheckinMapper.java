package com.ieltsstudio.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ieltsstudio.entity.StudyCheckin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface StudyCheckinMapper extends BaseMapper<StudyCheckin> {

    @Select("SELECT * FROM study_checkins WHERE user_id = #{userId} ORDER BY checkin_date DESC")
    List<StudyCheckin> findAllByUserIdOrderByDateDesc(Long userId);

    @Select("SELECT COUNT(1) FROM study_checkins WHERE user_id = #{userId}")
    long countByUserId(Long userId);

    @Select("SELECT COUNT(1) FROM study_checkins WHERE user_id = #{userId} AND checkin_date = #{date}")
    long countByUserIdAndDate(Long userId, LocalDate date);

    @Select("SELECT checkin_date FROM study_checkins WHERE user_id = #{userId} AND checkin_date >= #{fromDate} ORDER BY checkin_date ASC")
    List<LocalDate> findDatesFrom(Long userId, LocalDate fromDate);
}
