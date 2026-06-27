package com.ieltsstudio.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ieltsstudio.entity.AiUsageQuota;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code ai_usage_quota} 表的 MyBatis-Plus Mapper。
 *
 * <p>本阶段只提供 {@link BaseMapper} 通用能力，扣费 / 周期切换 SQL 留待后续阶段。</p>
 */
@Mapper
public interface AiUsageQuotaMapper extends BaseMapper<AiUsageQuota> {
}
