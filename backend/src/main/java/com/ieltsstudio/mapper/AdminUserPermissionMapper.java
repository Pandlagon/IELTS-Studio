package com.ieltsstudio.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ieltsstudio.entity.AdminUserPermission;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Admin 用户权限 Mapper（Phase 8C）。
 *
 * <p>对应 {@code admin_user_permissions} 表。除 {@link BaseMapper} 标准方法外，
 * 提供 {@code selectByUserId} / {@code countAllPermissions} /
 * {@code countUsersWithPermission} / {@code deleteByUserId} /
 * {@code deleteByUserIdAndPermission} 自定义方法。</p>
 *
 * <p>本表无 {@code @TableLogic} 软删除字段，所有 wrapper / 直接 SQL 均不附加 deleted 过滤。</p>
 */
@Mapper
public interface AdminUserPermissionMapper extends BaseMapper<AdminUserPermission> {

    /**
     * 查询某用户被分配的全部权限。
     *
     * @param userId 用户 ID
     * @return 权限记录列表（按 id 升序）
     */
    @Select("SELECT * FROM admin_user_permissions WHERE user_id = #{userId} ORDER BY id ASC")
    List<AdminUserPermission> selectByUserId(@Param("userId") Long userId);

    /**
     * 统计表中权限记录总数，用于判断是否进入显式权限模式。
     *
     * @return 表中权限记录总数；0 表示仍处于兼容模式（所有 ADMIN 拥有全部权限）
     */
    @Select("SELECT COUNT(*) FROM admin_user_permissions")
    Long countAllPermissions();

    /**
     * 统计拥有指定权限的用户数量，用于"不能移除最后一个权限管理员"保护。
     *
     * @param permission 权限名（{@link com.ieltsstudio.entity.AdminPermission} 枚举名）
     * @return 拥有该权限的用户数量
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM admin_user_permissions WHERE permission = #{permission}")
    Long countUsersWithPermission(@Param("permission") String permission);

    /**
     * 删除某用户的全部权限记录（用于更新权限时的先删后插策略）。
     *
     * @param userId 用户 ID
     * @return 被删除的记录数
     */
    @Delete("DELETE FROM admin_user_permissions WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 删除某用户的指定权限记录。
     *
     * @param userId     用户 ID
     * @param permission 权限名
     * @return 被删除的记录数
     */
    @Delete("DELETE FROM admin_user_permissions WHERE user_id = #{userId} AND permission = #{permission}")
    int deleteByUserIdAndPermission(@Param("userId") Long userId, @Param("permission") String permission);
}
