package com.ieltsstudio.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ieltsstudio.entity.ExamCollection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ExamCollectionMapper extends BaseMapper<ExamCollection> {

    @Select("""
        SELECT c.*, 
               (SELECT COUNT(*) FROM exam_collection_items i WHERE i.collection_id = c.id) AS exam_count
        FROM exam_collections c 
        WHERE c.user_id = #{userId} AND c.deleted = 0 
        ORDER BY c.updated_at DESC
    """)
    List<ExamCollection> findByUserId(Long userId);
}
