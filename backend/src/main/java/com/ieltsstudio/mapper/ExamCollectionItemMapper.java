package com.ieltsstudio.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ieltsstudio.entity.ExamCollectionItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ExamCollectionItemMapper extends BaseMapper<ExamCollectionItem> {

    @Select("""
        SELECT i.*, e.title AS exam_title, e.type AS exam_type, e.status AS exam_status, 
               e.question_count, e.duration AS exam_duration
        FROM exam_collection_items i 
        JOIN exams e ON e.id = i.exam_id AND e.deleted = 0
        WHERE i.collection_id = #{collectionId}
        ORDER BY i.sort_order ASC
    """)
    List<ExamCollectionItem> findByCollectionId(Long collectionId);

    @Delete("DELETE FROM exam_collection_items WHERE collection_id = #{collectionId} AND exam_id = #{examId}")
    int removeByCollectionAndExam(Long collectionId, Long examId);

    @Delete("DELETE FROM exam_collection_items WHERE collection_id = #{collectionId}")
    int removeAllByCollection(Long collectionId);

    @Select("SELECT COALESCE(MAX(sort_order), -1) FROM exam_collection_items WHERE collection_id = #{collectionId}")
    int getMaxSortOrder(Long collectionId);
}
