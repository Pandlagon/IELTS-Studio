package com.ieltsstudio.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ieltsstudio.entity.WordBook;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WordBookMapper extends BaseMapper<WordBook> {

    @Select("SELECT * FROM word_books WHERE user_id = #{userId} AND deleted = 0 ORDER BY is_default DESC, created_at ASC")
    List<WordBook> findByUserId(Long userId);

    @Select("SELECT * FROM word_books WHERE user_id = #{userId} AND is_default = 1 AND deleted = 0 LIMIT 1")
    WordBook findDefaultBook(Long userId);
}
