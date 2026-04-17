package com.ieltsstudio.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ieltsstudio.entity.ErrorBook;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ErrorBookMapper extends BaseMapper<ErrorBook> {

    @Select("SELECT * FROM error_book WHERE user_id = #{userId} AND mastered = 0 ORDER BY created_at DESC")
    List<ErrorBook> findUnmasteredByUserId(Long userId);

    @Select("SELECT COUNT(*) FROM error_book WHERE user_id = #{userId} AND mastered = 0")
    int countUnmasteredByUserId(Long userId);
}
