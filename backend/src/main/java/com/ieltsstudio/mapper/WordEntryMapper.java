package com.ieltsstudio.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ieltsstudio.entity.WordEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WordEntryMapper extends BaseMapper<WordEntry> {

    @Select("SELECT * FROM word_entries WHERE book_id = #{bookId} AND deleted = 0 ORDER BY created_at ASC")
    List<WordEntry> findByBookId(Long bookId);

    @Select("SELECT COUNT(*) FROM word_entries WHERE book_id = #{bookId} AND deleted = 0")
    int countByBookId(Long bookId);
}
