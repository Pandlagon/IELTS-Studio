package com.ieltsstudio.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ieltsstudio.entity.AdminOperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端操作审计日志 Mapper（Phase 8D）。
 *
 * <p>对应 {@code admin_operation_logs} 表。除 {@link BaseMapper} 标准方法外，
 * 提供 {@code selectAdminOperationLogs} / {@code countAdminOperationLogs} 自定义查询，
 * 支持 actorUserId / targetUserId / action / resourceType / status / dateFrom / dateTo 筛选。</p>
 *
 * <p>本表无 {@code @TableLogic} 软删除字段，审计日志不允许删除。</p>
 */
@Mapper
public interface AdminOperationLogMapper extends BaseMapper<AdminOperationLog> {

    /**
     * 分页查询审计日志，按 created_at DESC 排序。
     *
     * <p>所有筛选参数均可为 null（表示不过滤）。使用 {@code <script>} 动态 SQL
     * 保持项目现有 Mapper 风格。</p>
     *
     * @param offset       偏移量（page * pageSize）
     * @param pageSize     每页条数
     * @param actorUserId  操作者 ID
     * @param targetUserId 被操作用户 ID
     * @param action       action 枚举名
     * @param resourceType 资源类型
     * @param status       状态
     * @param dateFrom     起始时间（含）
     * @param dateTo       截止时间（含）
     */
    @Select("""
            <script>
            SELECT * FROM admin_operation_logs
            <where>
                <if test="actorUserId != null">AND actor_user_id = #{actorUserId}</if>
                <if test="targetUserId != null">AND target_user_id = #{targetUserId}</if>
                <if test="action != null and action != ''">AND action = #{action}</if>
                <if test="resourceType != null and resourceType != ''">AND resource_type = #{resourceType}</if>
                <if test="status != null and status != ''">AND status = #{status}</if>
                <if test="dateFrom != null">AND created_at &gt;= #{dateFrom}</if>
                <if test="dateTo != null">AND created_at &lt;= #{dateTo}</if>
            </where>
            ORDER BY created_at DESC, id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<AdminOperationLog> selectAdminOperationLogs(
            @Param("offset") int offset,
            @Param("pageSize") int pageSize,
            @Param("actorUserId") Long actorUserId,
            @Param("targetUserId") Long targetUserId,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("status") String status,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo);

    /**
     * 统计符合筛选条件的审计日志总数。
     *
     * <p>筛选参数与 {@link #selectAdminOperationLogs} 一致。</p>
     */
    @Select("""
            <script>
            SELECT COUNT(*) FROM admin_operation_logs
            <where>
                <if test="actorUserId != null">AND actor_user_id = #{actorUserId}</if>
                <if test="targetUserId != null">AND target_user_id = #{targetUserId}</if>
                <if test="action != null and action != ''">AND action = #{action}</if>
                <if test="resourceType != null and resourceType != ''">AND resource_type = #{resourceType}</if>
                <if test="status != null and status != ''">AND status = #{status}</if>
                <if test="dateFrom != null">AND created_at &gt;= #{dateFrom}</if>
                <if test="dateTo != null">AND created_at &lt;= #{dateTo}</if>
            </where>
            </script>
            """)
    Long countAdminOperationLogs(
            @Param("actorUserId") Long actorUserId,
            @Param("targetUserId") Long targetUserId,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("status") String status,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo);
}
