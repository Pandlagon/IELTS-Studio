package com.ieltsstudio.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ieltsstudio.entity.WordStudyState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WordStudyStateMapper extends BaseMapper<WordStudyState> {

    @Select("SELECT * FROM word_study_states WHERE user_id = #{userId} AND book_id = #{bookId} LIMIT 1")
    WordStudyState findByUserIdAndBookId(@Param("userId") Long userId, @Param("bookId") String bookId);
}
