package com.ieltsstudio.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ieltsstudio.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM users WHERE username = #{username} AND deleted = 0")
    User findByUsername(String username);

    @Select("SELECT * FROM users WHERE email = #{email} AND deleted = 0")
    User findByEmail(String email);

    // ─── Phase 8A：管理端用户管理（自定义 SQL，绕过 @TableLogic 以查询/操作禁用用户）────

    /**
     * 管理端分页查询用户（包含已逻辑删除的用户）。
     *
     * <p>使用原生 SQL 而非 wrapper，避免 MyBatis-Plus {@code @TableLogic} 自动追加
     * {@code deleted = 0} 导致无法查到已禁用用户。</p>
     *
     * @param offset  偏移量（{@code (page-1) * pageSize}）
     * @param pageSize 每页条数
     * @param keyword  关键字（可匹配 username / email），可为 null
     * @param role     角色过滤（USER / ADMIN），可为 null
     * @param deleted  状态过滤：0=启用，1=禁用，null=全部
     */
    @Select("<script>" +
            "SELECT * FROM users " +
            "WHERE 1=1 " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (username LIKE CONCAT('%',#{keyword},'%') OR email LIKE CONCAT('%',#{keyword},'%')) " +
            "</if>" +
            "<if test='role != null and role != \"\"'>" +
            "  AND role = #{role} " +
            "</if>" +
            "<if test='deleted != null'>" +
            "  AND deleted = #{deleted} " +
            "</if>" +
            "ORDER BY id DESC " +
            "LIMIT #{offset}, #{pageSize}" +
            "</script>")
    List<User> selectAdminUsers(@Param("offset") int offset,
                                @Param("pageSize") int pageSize,
                                @Param("keyword") String keyword,
                                @Param("role") String role,
                                @Param("deleted") Integer deleted);

    /**
     * 管理端统计用户总数（包含已逻辑删除的用户），过滤条件与 {@link #selectAdminUsers} 一致。
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM users " +
            "WHERE 1=1 " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (username LIKE CONCAT('%',#{keyword},'%') OR email LIKE CONCAT('%',#{keyword},'%')) " +
            "</if>" +
            "<if test='role != null and role != \"\"'>" +
            "  AND role = #{role} " +
            "</if>" +
            "<if test='deleted != null'>" +
            "  AND deleted = #{deleted} " +
            "</if>" +
            "</script>")
    Long countAdminUsers(@Param("keyword") String keyword,
                         @Param("role") String role,
                         @Param("deleted") Integer deleted);

    /**
     * 按主键查询用户（包含已逻辑删除的用户），用于管理端查看禁用用户详情。
     */
    @Select("SELECT * FROM users WHERE id = #{id}")
    User selectUserIncludingDeleted(@Param("id") Long id);

    /**
     * 更新用户启用/禁用状态（直接操作 deleted 字段，绕过 @TableLogic 的逻辑删除语义）。
     *
     * @param id      用户 ID
     * @param deleted 0=启用，1=禁用
     */
    @Update("UPDATE users SET deleted = #{deleted}, updated_at = NOW() WHERE id = #{id}")
    int updateDeletedById(@Param("id") Long id, @Param("deleted") Integer deleted);

    /**
     * 更新用户角色。
     */
    @Update("UPDATE users SET role = #{role}, updated_at = NOW() WHERE id = #{id}")
    int updateRoleById(@Param("id") Long id, @Param("role") String role);

    /**
     * 更新用户密码哈希（已通过 {@code PasswordEncoder.encode(...)} 加密）。
     */
    @Update("UPDATE users SET password = #{passwordHash}, updated_at = NOW() WHERE id = #{id}")
    int updatePasswordById(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    /**
     * 统计当前启用的 ADMIN 用户数量（deleted = 0），用于"不能禁用/降级最后一个管理员"保护。
     */
    @Select("SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND deleted = 0")
    Long countActiveAdmins();

    /**
     * 统计所有 ADMIN 用户数量（包含已禁用 deleted=1），用于启动时管理员初始化的幂等判断：
     * 只要库中曾存在过 ADMIN（即使被禁用），就不再用环境变量凭据自动创建新的管理员，
     * 避免初始密码被重新激活。
     */
    @Select("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'")
    Long countAllAdminsIncludingDeleted();

    /**
     * 检查 username 或 email 是否已被占用（含已禁用用户），用于启动时管理员初始化的冲突检测，
     * 避免插入时撞 users.username / users.email 的 UNIQUE 约束。
     */
    @Select("SELECT COUNT(*) FROM users WHERE username = #{username} OR email = #{email}")
    Long countByUsernameOrEmail(@Param("username") String username, @Param("email") String email);

    /**
     * 按 username 或 email 查询用户（含已禁用 deleted=1），用于启动时管理员初始化的"升级现有账号"分支：
     * 若目标用户名/邮箱已存在且为 USER，则升级为 ADMIN 并重置密码。
     */
    @Select("SELECT * FROM users WHERE username = #{username} OR email = #{email} LIMIT 1")
    User selectByUsernameOrEmailIncludingDeleted(@Param("username") String username, @Param("email") String email);
}
