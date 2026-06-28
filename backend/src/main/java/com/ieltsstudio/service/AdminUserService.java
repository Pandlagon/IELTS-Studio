package com.ieltsstudio.service;

import com.ieltsstudio.dto.admin.AdminCreateUserRequest;
import com.ieltsstudio.dto.admin.AdminUserDto;
import com.ieltsstudio.dto.admin.AdminUserPageDto;
import com.ieltsstudio.entity.User;
import com.ieltsstudio.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 管理端用户管理服务（Phase 8A）。
 *
 * <p>职责：用户列表/搜索、用户详情、角色修改、禁用/启用、重置密码。
 *
 * <p><b>安全规则：</b>
 * <ul>
 *   <li>不能禁用自己</li>
 *   <li>不能降级自己</li>
 *   <li>不能禁用最后一个 ADMIN</li>
 *   <li>不能降级最后一个 ADMIN</li>
 *   <li>重置密码使用 {@link PasswordEncoder#encode(CharSequence)} 存储 BCrypt 哈希</li>
 *   <li>DTO 永远不返回 password 字段</li>
 *   <li>不记录明文密码日志</li>
 * </ul>
 *
 * <p><b>逻辑删除处理：</b>使用 {@link UserMapper} 的自定义 SQL 方法
 * （{@code selectAdminUsers} / {@code selectUserIncludingDeleted} / {@code updateDeletedById} 等）
 * 绕过 MyBatis-Plus {@code @TableLogic} 自动过滤，以查询/操作已禁用（deleted=1）的用户。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    /** 允许的角色值（白名单） */
    private static final Set<String> ALLOWED_ROLES = Set.of("USER", "ADMIN");

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 分页查询用户列表（包含已禁用用户）。
     *
     * @param page     页码，最小 1（小于 1 自动 clamp 到 1）
     * @param pageSize 每页条数，范围 1~100（越界自动 clamp）
     * @param keyword  关键字（匹配 username / email），可为 null/空
     * @param role     角色过滤：null/空=全部，仅允许 USER / ADMIN
     * @param status   状态过滤：null/空/ALL=全部，ACTIVE=启用，DISABLED=禁用
     * @return 分页用户列表（不包含 password）
     * @throws IllegalArgumentException 当 role 或 status 传入非法值时
     */
    public AdminUserPageDto listUsers(int page, int pageSize, String keyword, String role, String status) {
        // 参数 clamp
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));

        // role 白名单校验（null/空表示不过滤，否则必须是 USER / ADMIN）
        String safeRole = trimToNull(role);
        if (safeRole != null && !ALLOWED_ROLES.contains(safeRole)) {
            throw new IllegalArgumentException("非法的 role 值：" + role);
        }

        // status 映射到 deleted 字段
        Integer deleted = parseStatus(status);

        long total = userMapper.countAdminUsers(trimToNull(keyword), safeRole, deleted);
        int offset = (safePage - 1) * safePageSize;
        List<User> users = userMapper.selectAdminUsers(offset, safePageSize, trimToNull(keyword), safeRole, deleted);

        AdminUserPageDto dto = new AdminUserPageDto();
        dto.setRecords(users.stream().map(this::toDto).toList());
        dto.setTotal(total);
        dto.setPage((long) safePage);
        dto.setPageSize((long) safePageSize);
        dto.setPages(safePageSize == 0 ? 0L : (long) Math.ceil((double) total / safePageSize));
        return dto;
    }

    /**
     * 管理员新增用户。
     *
     * <p>规则：
     * <ul>
     *   <li>role 必须是 USER / ADMIN（白名单校验，不信任前端）。</li>
     *   <li>username / email 必须未被占用（含已禁用用户，避免唯一约束冲突）。</li>
     *   <li>密码使用 {@link PasswordEncoder#encode(CharSequence)} 存储 BCrypt 哈希。</li>
     *   <li>不记录明文密码日志。</li>
     *   <li>返回的 DTO 不包含 password。</li>
     * </ul>
     *
     * @param request 新增用户请求（username/email/password/role，已在 Controller 层做基本校验）
     * @return 创建后的用户 DTO（不包含 password）
     * @throws IllegalArgumentException role 非法
     * @throws RuntimeException         用户名或邮箱已被占用
     */
    public AdminUserDto createUser(AdminCreateUserRequest request) {
        String role = request.getRole();
        if (role == null || !ALLOWED_ROLES.contains(role)) {
            throw new IllegalArgumentException("非法的 role 值：" + role);
        }
        String username = request.getUsername().trim();
        String email = request.getEmail().trim();

        // 唯一性校验（含已禁用用户，避免撞 UNIQUE 约束）
        Long conflict = userMapper.countByUsernameOrEmail(username, email);
        if (conflict != null && conflict > 0) {
            throw new RuntimeException("用户名或邮箱已被占用");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        user.setDeleted(0);
        userMapper.insert(user);

        // 重新查询拿到数据库生成的 id / createdAt / updatedAt
        User saved = userMapper.selectByUsernameOrEmailIncludingDeleted(username, email);
        log.info("管理员创建用户成功：username={}, role={}", username, role);
        return toDto(saved != null ? saved : user);
    }

    /**
     * 查询单个用户详情（包含已禁用用户）。
     *
     * @param id 用户 ID
     * @return 用户 DTO（不包含 password）
     * @throws RuntimeException 用户不存在时
     */
    public AdminUserDto getUser(Long id) {
        User user = loadUserOrThrow(id);
        return toDto(user);
    }

    /**
     * 修改用户角色。
     *
     * <p>保护：不能降级自己、不能降级最后一个 ADMIN、role 必须是 USER / ADMIN。
     *
     * @param currentAdminId 当前操作的管理员 ID（从 AuthUser 取）
     * @param targetUserId   目标用户 ID
     * @param role           新角色（USER / ADMIN）
     * @return 更新后的用户 DTO
     * @throws IllegalArgumentException role 非法
     * @throws RuntimeException         用户不存在 / 降级自己 / 降级最后一个 ADMIN
     */
    public AdminUserDto updateRole(Long currentAdminId, Long targetUserId, String role) {
        if (role == null || !ALLOWED_ROLES.contains(role)) {
            throw new IllegalArgumentException("非法的 role 值：" + role);
        }
        User target = loadUserOrThrow(targetUserId);

        // 降级保护（仅当目标当前是 ADMIN 且新角色是 USER 时触发）
        if ("USER".equals(role) && "ADMIN".equals(target.getRole())) {
            if (currentAdminId != null && currentAdminId.equals(targetUserId)) {
                throw new RuntimeException("不能降级自己的管理员角色");
            }
            Long activeAdmins = userMapper.countActiveAdmins();
            if (activeAdmins == null || activeAdmins <= 1) {
                throw new RuntimeException("不能降级最后一个管理员");
            }
        }

        userMapper.updateRoleById(targetUserId, role);
        return toDto(loadUserOrThrow(targetUserId));
    }

    /**
     * 禁用用户（deleted = 1）。
     *
     * <p>保护：不能禁用自己、不能禁用最后一个 ADMIN。
     *
     * @param currentAdminId 当前操作的管理员 ID
     * @param targetUserId   目标用户 ID
     * @return 更新后的用户 DTO
     * @throws RuntimeException 用户不存在 / 禁用自己 / 禁用最后一个 ADMIN
     */
    public AdminUserDto disableUser(Long currentAdminId, Long targetUserId) {
        User target = loadUserOrThrow(targetUserId);

        if (currentAdminId != null && currentAdminId.equals(targetUserId)) {
            throw new RuntimeException("不能禁用自己");
        }
        if ("ADMIN".equals(target.getRole()) && target.getDeleted() != null && target.getDeleted() == 0) {
            Long activeAdmins = userMapper.countActiveAdmins();
            if (activeAdmins == null || activeAdmins <= 1) {
                throw new RuntimeException("不能禁用最后一个管理员");
            }
        }

        userMapper.updateDeletedById(targetUserId, 1);
        return toDto(loadUserOrThrow(targetUserId));
    }

    /**
     * 启用用户（deleted = 0）。不改变其 role。
     *
     * @param currentAdminId 当前操作的管理员 ID（保留以保持签名一致，本方法不使用）
     * @param targetUserId   目标用户 ID
     * @return 更新后的用户 DTO
     * @throws RuntimeException 用户不存在
     */
    public AdminUserDto enableUser(Long currentAdminId, Long targetUserId) {
        loadUserOrThrow(targetUserId);
        userMapper.updateDeletedById(targetUserId, 0);
        return toDto(loadUserOrThrow(targetUserId));
    }

    /**
     * 重置用户密码。
     *
     * <p>安全：使用 {@link PasswordEncoder#encode(CharSequence)} 存储 BCrypt 哈希，
     * 不记录明文密码日志，不返回 password。
     *
     * @param targetUserId 目标用户 ID
     * @param newPassword  新密码（已在 Controller 层通过 @Size 校验长度 8~128）
     */
    public void resetPassword(Long targetUserId, String newPassword) {
        loadUserOrThrow(targetUserId);
        String hash = passwordEncoder.encode(newPassword);
        userMapper.updatePasswordById(targetUserId, hash);
        log.info("管理员重置用户密码成功，userId={}", targetUserId);
    }

    // ─── 内部辅助 ──────────────────────────────────────────────────────────────

    /**
     * 加载用户（包含已禁用），不存在则抛业务异常。
     */
    private User loadUserOrThrow(Long id) {
        if (id == null) {
            throw new RuntimeException("用户 ID 不能为空");
        }
        User user = userMapper.selectUserIncludingDeleted(id);
        if (user == null) {
            throw new RuntimeException("用户不存在：id=" + id);
        }
        return user;
    }

    /**
     * 将 status 参数映射为 deleted 字段值。
     *
     * <ul>
     *   <li>null/空/ALL → null（不过滤）</li>
     *   <li>ACTIVE → 0</li>
     *   <li>DISABLED → 1</li>
     *   <li>其他 → 抛 IllegalArgumentException</li>
     * </ul>
     */
    private Integer parseStatus(String status) {
        String s = trimToNull(status);
        if (s == null || "ALL".equalsIgnoreCase(s)) {
            return null;
        }
        if ("ACTIVE".equalsIgnoreCase(s)) {
            return 0;
        }
        if ("DISABLED".equalsIgnoreCase(s)) {
            return 1;
        }
        throw new IllegalArgumentException("非法的 status 值：" + status);
    }

    /**
     * 去除首尾空白，空白字符串返回 null。
     */
    private String trimToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * User → AdminUserDto 映射。<b>不包含 password 字段</b>。
     */
    private AdminUserDto toDto(User user) {
        AdminUserDto dto = new AdminUserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setAvatar(user.getAvatar());
        dto.setRole(user.getRole());
        dto.setDeleted(user.getDeleted());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());
        return dto;
    }
}
