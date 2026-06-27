package com.ieltsstudio.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ieltsstudio.entity.UserAiSettings;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code user_ai_settings} 表的 MyBatis-Plus Mapper。
 *
 * <p>本阶段只提供 {@link BaseMapper} 通用能力，复杂 SQL 留待后续阶段。</p>
 */
@Mapper
public interface UserAiSettingsMapper extends BaseMapper<UserAiSettings> {
}
