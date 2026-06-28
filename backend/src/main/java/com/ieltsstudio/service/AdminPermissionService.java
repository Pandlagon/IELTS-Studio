package com.ieltsstudio.service;

import com.ieltsstudio.dto.admin.AdminPermissionDto;
import com.ieltsstudio.entity.AdminPermission;
import com.ieltsstudio.entity.AdminUserPermission;
import com.ieltsstudio.entity.User;
import com.ieltsstudio.mapper.AdminUserPermissionMapper;
import com.ieltsstudio.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admin 权限服务（Phase 8C）。
 *
 * <p>负责权限枚举列出、用户权限查询、权限判断、权限更新与安全保护。
 * 保留 USER / ADMIN 两级基础角色不变，仅在 ADMIN 内部做权限细分。</p>
 *
 * <h3>显式权限模式（explicit permission mode）</h3>
 * <ul>
 *   <li>表为空 → 兼容模式：所有 ADMIN 拥有全部权限。</li>
 *   <li>表非空 → 显式模式：ADMIN 按表判断权限。</li>
 * </ul>
 *
 * <h3>USER 权限规则</h3>
 * <p>USER 永远没有后台权限，无论表里是否有记录。</p>
 *
 * <h3>安全规则</h3>
 * <ol>
 *   <li>只有 ADMIN 可以拥有权限：target 非 ADMIN 时拒绝设置。</li>
 *   <li>不能移除自己的 {@link AdminPermission#ADMIN_PERMISSIONS_MANAGE}。</li>
 *   <li>不能移除系统中最后一个 {@link AdminPermission#ADMIN_PERMISSIONS_MANAGE}。</li>
 *   <li>从兼容模式进入显式模式后必须至少保留一个 {@link AdminPermission#ADMIN_PERMISSIONS_MANAGE}。</li>
 *   <li>权限白名单：传入权限必须全部存在于 {@link AdminPermission} 枚举中。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPermissionService {

    private final UserMapper userMapper;
    private final AdminUserPermissionMapper permissionMapper;

    // ─── 查询 ───────────────────────────────────────────────────────────────────

    /**
     * 列出所有合法权限枚举名（按 {@link AdminPermission#values()} 顺序）。
     */
    public List<String> listAllPermissions() {
        return Arrays.stream(AdminPermission.values())
                .map(AdminPermission::name)
                .toList();
    }

    /**
     * 查询某用户的有效权限集合。
     *
     * <p>规则：
     * <ul>
     *   <li>USER → 永远返回空集合。</li>
     *   <li>ADMIN + 兼容模式 → 返回全部权限枚举。</li>
     *   <li>ADMIN + 显式模式 → 返回表中的权限。</li>
     * </ul>
     *
     * @param userId 用户 ID；null 时返回空集合
     */
    public Set<String> getUserPermissions(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        User user = userMapper.selectUserIncludingDeleted(userId);
        if (user == null || !"ADMIN".equals(user.getRole())) {
            return Set.of();
        }
        if (!isExplicitPermissionMode()) {
            // 兼容模式：ADMIN 拥有全部权限
            return Arrays.stream(AdminPermission.values())
                    .map(AdminPermission::name)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        // 显式模式：仅返回表中权限
        return permissionMapper.selectByUserId(userId).stream()
                .map(AdminUserPermission::getPermission)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 构造某用户的权限视图 DTO（包含 userId / username / role / explicitMode / permissions）。
     */
    public AdminPermissionDto buildPermissionDto(Long userId) {
        User user = userMapper.selectUserIncludingDeleted(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在：id=" + userId);
        }
        boolean explicit = isExplicitPermissionMode();
        AdminPermissionDto dto = new AdminPermissionDto();
        dto.setUserId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setRole(user.getRole());
        dto.setExplicitMode(explicit);
        if (!"ADMIN".equals(user.getRole())) {
            // USER 永远没有后台权限
            dto.setPermissions(Set.of());
            return dto;
        }
        dto.setPermissions(getUserPermissions(userId));
        return dto;
    }

    /**
     * 判断某用户是否拥有指定权限。
     *
     * <p>规则：
     * <ul>
     *   <li>USER → 永远 false。</li>
     *   <li>ADMIN + 兼容模式 → 永远 true。</li>
     *   <li>ADMIN + 显式模式 → 表中包含该权限时 true。</li>
     * </ul>
     *
     * <p>注意：role 以数据库为准，不信任 JWT 里的 role。</p>
     *
     * @param userId     用户 ID
     * @param permission 待校验权限
     * @return true=有权限；false=无权限
     */
    public boolean hasPermission(Long userId, AdminPermission permission) {
        if (userId == null || permission == null) {
            return false;
        }
        User user = userMapper.selectUserIncludingDeleted(userId);
        if (user == null || !"ADMIN".equals(user.getRole())) {
            return false;
        }
        if (!isExplicitPermissionMode()) {
            return true;
        }
        return permissionMapper.selectByUserId(userId).stream()
                .anyMatch(p -> permission.name().equals(p.getPermission()));
    }

    /**
     * 要求当前用户拥有指定权限，否则抛 {@link org.springframework.security.access.AccessDeniedException}。
     *
     * <p>Controller 内调用 {@code requirePermission(authUser.getId(), AdminPermission.XXX)} 做精细权限二次校验。
     * 前置条件：调用方应已通过 {@code requireAdmin(authUser)} 校验过 ADMIN 角色。</p>
     *
     * @param userId     当前操作者 ID
     * @param permission 要求的权限
     */
    public void requirePermission(Long userId, AdminPermission permission) {
        if (!hasPermission(userId, permission)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "缺少权限：" + permission.name());
        }
    }

    // ─── 写操作 ─────────────────────────────────────────────────────────────────

    /**
     * 更新某 ADMIN 用户的权限集合（先删后插）。
     *
     * <p>执行 5 条安全规则（见类注释），任一不满足时抛 {@link RuntimeException}。</p>
     *
     * @param currentAdminId 当前操作者 ID（用于"不能移除自己 ADMIN_PERMISSIONS_MANAGE"保护）
     * @param targetUserId   被更新用户 ID
     * @param permissions    新权限集合（{@link AdminPermission} 枚举名）
     * @return 更新后的权限视图 DTO
     */
    public AdminPermissionDto updateUserPermissions(Long currentAdminId, Long targetUserId, Set<String> permissions) {
        if (currentAdminId == null || targetUserId == null) {
            throw new RuntimeException("currentAdminId / targetUserId 不能为空");
        }
        if (permissions == null) {
            throw new RuntimeException("permissions 不能为 null（空集合请传 []）");
        }

        // 规则 5：权限白名单
        for (String p : permissions) {
            if (!AdminPermission.isValid(p)) {
                throw new RuntimeException("非法权限值：" + p);
            }
        }

        User target = userMapper.selectUserIncludingDeleted(targetUserId);
        if (target == null) {
            throw new RuntimeException("用户不存在：id=" + targetUserId);
        }

        // 规则 1：只有 ADMIN 可以拥有权限
        if (!"ADMIN".equals(target.getRole())) {
            throw new RuntimeException("只能为 ADMIN 用户配置权限，target role=" + target.getRole());
        }

        // 规则 2：不能移除自己的 ADMIN_PERMISSIONS_MANAGE
        if (currentAdminId.equals(targetUserId)
                && !permissions.contains(AdminPermission.ADMIN_PERMISSIONS_MANAGE.name())) {
            throw new RuntimeException("不能移除自己的 ADMIN_PERMISSIONS_MANAGE 权限");
        }

        boolean currentlyHasPermissionManage = permissionMapper.selectByUserId(targetUserId).stream()
                .anyMatch(p -> AdminPermission.ADMIN_PERMISSIONS_MANAGE.name().equals(p.getPermission()));
        boolean willKeepPermissionManage = permissions.contains(AdminPermission.ADMIN_PERMISSIONS_MANAGE.name());

        if (currentlyHasPermissionManage && !willKeepPermissionManage) {
            // 规则 3：不能移除系统中最后一个 ADMIN_PERMISSIONS_MANAGE
            long permissionManagers = permissionMapper.countUsersWithPermission(
                    AdminPermission.ADMIN_PERMISSIONS_MANAGE.name());
            if (permissionManagers <= 1) {
                throw new RuntimeException("不能移除系统中最后一个 ADMIN_PERMISSIONS_MANAGE 权限管理者");
            }
        }

        // 规则 4：从兼容模式进入显式模式后必须至少保留一个 ADMIN_PERMISSIONS_MANAGE
        // 这里通过"更新后全系统不允许没有任何 permission manager"来兜底
        // （包括从空表首次插入的场景）
        long currentManagers = permissionMapper.countUsersWithPermission(
                AdminPermission.ADMIN_PERMISSIONS_MANAGE.name());
        boolean targetWasManager = currentlyHasPermissionManage;
        boolean targetWillBeManager = willKeepPermissionManage;

        // 更新后的 manager 总数（去重 target）
        long managersAfterUpdate = currentManagers
                - (targetWasManager ? 1 : 0)
                + (targetWillBeManager ? 1 : 0);
        if (managersAfterUpdate < 1) {
            throw new RuntimeException("系统中必须至少保留一个 ADMIN_PERMISSIONS_MANAGE 权限管理者");
        }

        // 执行：先删后插
        permissionMapper.deleteByUserId(targetUserId);
        for (String p : permissions) {
            AdminUserPermission entity = new AdminUserPermission();
            entity.setUserId(targetUserId);
            entity.setPermission(p);
            permissionMapper.insert(entity);
        }
        log.info("管理员更新权限：currentAdminId={}, targetUserId={}, permissions={}",
                currentAdminId, targetUserId, permissions);

        return buildPermissionDto(targetUserId);
    }

    // ─── 内部 helper ────────────────────────────────────────────────────────────

    /**
     * 是否处于显式权限模式：表非空时为 true。
     */
    public boolean isExplicitPermissionMode() {
        Long count = permissionMapper.countAllPermissions();
        return count != null && count > 0;
    }
}
