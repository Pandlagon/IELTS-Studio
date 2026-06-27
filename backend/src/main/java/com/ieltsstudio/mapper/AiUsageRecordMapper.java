package com.ieltsstudio.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ieltsstudio.entity.AiUsageRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code ai_usage_records} 表的 MyBatis-Plus Mapper。
 *
 * <p>本阶段只提供 {@link BaseMapper} 通用能力，统计 / 审计 SQL 留待后续阶段。</p>
 */
@Mapper
public interface AiUsageRecordMapper extends BaseMapper<AiUsageRecord> {
}
