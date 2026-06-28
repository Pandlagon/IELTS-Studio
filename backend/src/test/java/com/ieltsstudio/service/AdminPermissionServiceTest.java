package com.ieltsstudio.service;

import com.ieltsstudio.dto.admin.AdminPermissionDto;
import com.ieltsstudio.entity.AdminPermission;
import com.ieltsstudio.entity.AdminUserPermission;
import com.ieltsstudio.entity.User;
import com.ieltsstudio.mapper.AdminUserPermissionMapper;
import com.ieltsstudio.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdminPermissionService} 单元测试（Phase 8C）。
 *
 * <p>不连接真实数据库：{@link UserMapper} 与 {@link AdminUserPermissionMapper} 用 Mockito mock。
 * 覆盖显式/兼容模式、USER 无权限、5 条安全规则、DTO 不暴露敏感字段。</p>
 */
class AdminPermissionServiceTest {

    private UserMapper userMapper;
    private AdminUserPermissionMapper permissionMapper;
    private AdminPermissionService service;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        permissionMapper = mock(AdminUserPermissionMapper.class);
        service = new AdminPermissionService(userMapper, permissionMapper);
    }

    // ─── 1. adminShouldHaveAllPermissionsWhenExplicitModeDisabled ──────────────

    @Test
    void adminShouldHaveAllPermissionsWhenExplicitModeDisabled() {
        // 表为空（兼容模式）
        when(permissionMapper.countAllPermissions()).thenReturn(0L);
        when(userMapper.selectUserIncludingDeleted(1L)).thenReturn(newUser(1L, "admin", "a@x.com", "ADMIN", 0));

        for (AdminPermission p : AdminPermission.values()) {
            assertTrue(service.hasPermission(1L, p), "兼容模式下 ADMIN 应有全部权限：" + p);
        }

        // getUserPermissions 应返回全部权限
        Set<String> perms = service.getUserPermissions(1L);
        assertEquals(AdminPermission.values().length, perms.size());
        for (AdminPermission p : AdminPermission.values()) {
            assertTrue(perms.contains(p.name()));
        }
        // 不应查询 permission 表（兼容模式直接返回全部）
        verify(permissionMapper, never()).selectByUserId(anyLong());
    }

    // ─── 2. userShouldHaveNoPermission ──────────────────────────────────────────

    @Test
    void userShouldHaveNoPermission() {
        // 即使表里有记录，USER 永远没权限
        when(permissionMapper.countAllPermissions()).thenReturn(10L);
        when(userMapper.selectUserIncludingDeleted(1L)).thenReturn(newUser(1L, "alice", "a@x.com", "USER", 0));

        for (AdminPermission p : AdminPermission.values()) {
            assertFalse(service.hasPermission(1L, p), "USER 不应有任何后台权限：" + p);
        }
        // getUserPermissions 返回空集合
        Set<String> perms = service.getUserPermissions(1L);
        assertTrue(perms.isEmpty());
        // 不应查询 permission 表（USER 直接 short-circuit）
        verify(permissionMapper, never()).selectByUserId(anyLong());
    }

    // ─── 3. adminShouldUseExplicitPermissionsWhenModeEnabled ───────────────────

    @Test
    void adminShouldUseExplicitPermissionsWhenModeEnabled() {
        // 表非空（显式模式）
        when(permissionMapper.countAllPermissions()).thenReturn(2L);
        when(userMapper.selectUserIncludingDeleted(1L)).thenReturn(newUser(1L, "admin", "a@x.com", "ADMIN", 0));
        // 只分配 ADMIN_USERS_VIEW
        when(permissionMapper.selectByUserId(1L)).thenReturn(List.of(
                newPerm(10L, 1L, AdminPermission.ADMIN_USERS_VIEW.name())));

        assertTrue(service.hasPermission(1L, AdminPermission.ADMIN_USERS_VIEW));
        assertFalse(service.hasPermission(1L, AdminPermission.ADMIN_USERS_MANAGE));
        assertFalse(service.hasPermission(1L, AdminPermission.ADMIN_PERMISSIONS_MANAGE));
    }

    // ─── 4. updatePermissionsShouldRejectNonAdminTarget ────────────────────────

    @Test
    void updatePermissionsShouldRejectNonAdminTarget() {
        when(userMapper.selectUserIncludingDeleted(2L)).thenReturn(newUser(2L, "alice", "a@x.com", "USER", 0));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.updateUserPermissions(1L, 2L, Set.of(AdminPermission.ADMIN_USERS_VIEW.name())));
        assertTrue(ex.getMessage().contains("ADMIN"));
        // 不应执行写操作
        verify(permissionMapper, never()).deleteByUserId(anyLong());
        verify(permissionMapper, never()).insert(any(AdminUserPermission.class));
    }

    // ─── 5. updatePermissionsShouldRejectUnknownPermission ─────────────────────

    @Test
    void updatePermissionsShouldRejectUnknownPermission() {
        when(userMapper.selectUserIncludingDeleted(2L)).thenReturn(newUser(2L, "admin2", "a2@x.com", "ADMIN", 0));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.updateUserPermissions(1L, 2L, Set.of("FAKE_PERMISSION")));
        assertTrue(ex.getMessage().contains("非法权限"));
        verify(permissionMapper, never()).deleteByUserId(anyLong());
        verify(permissionMapper, never()).insert(any(AdminUserPermission.class));
    }

    // ─── 6. updatePermissionsShouldRejectRemovingOwnPermissionManage ───────────

    @Test
    void updatePermissionsShouldRejectRemovingOwnPermissionManage() {
        // currentAdminId == targetUserId，新权限不含 ADMIN_PERMISSIONS_MANAGE
        when(userMapper.selectUserIncludingDeleted(1L)).thenReturn(newUser(1L, "admin", "a@x.com", "ADMIN", 0));
        // 当前持有 ADMIN_PERMISSIONS_MANAGE
        when(permissionMapper.selectByUserId(1L)).thenReturn(List.of(
                newPerm(10L, 1L, AdminPermission.ADMIN_PERMISSIONS_MANAGE.name())));
        when(permissionMapper.countUsersWithPermission(AdminPermission.ADMIN_PERMISSIONS_MANAGE.name()))
                .thenReturn(5L); // 系统有 5 个 manager，仍要拒绝（因为是 self-removal）

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.updateUserPermissions(1L, 1L, Set.of(AdminPermission.ADMIN_USERS_VIEW.name())));
        assertTrue(ex.getMessage().contains("不能移除自己的 ADMIN_PERMISSIONS_MANAGE"));
        verify(permissionMapper, never()).deleteByUserId(anyLong());
        verify(permissionMapper, never()).insert(any(AdminUserPermission.class));
    }

    // ─── 7. updatePermissionsShouldRejectRemovingLastPermissionManager ─────────

    @Test
    void updatePermissionsShouldRejectRemovingLastPermissionManager() {
        // target=2 拥有 ADMIN_PERMISSIONS_MANAGE，且系统只有 1 个 manager
        when(userMapper.selectUserIncludingDeleted(2L)).thenReturn(newUser(2L, "admin2", "a2@x.com", "ADMIN", 0));
        when(permissionMapper.selectByUserId(2L)).thenReturn(List.of(
                newPerm(10L, 2L, AdminPermission.ADMIN_PERMISSIONS_MANAGE.name())));
        when(permissionMapper.countUsersWithPermission(AdminPermission.ADMIN_PERMISSIONS_MANAGE.name()))
                .thenReturn(1L); // 系统只有 1 个 manager
        // buildPermissionDto 用于返回 DTO（updateUserPermissions 末尾会调用）
        when(permissionMapper.countAllPermissions()).thenReturn(1L);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.updateUserPermissions(1L, 2L, Set.of(AdminPermission.ADMIN_USERS_VIEW.name())));
        assertTrue(ex.getMessage().contains("最后一个 ADMIN_PERMISSIONS_MANAGE"));
        verify(permissionMapper, never()).deleteByUserId(anyLong());
        verify(permissionMapper, never()).insert(any(AdminUserPermission.class));
    }

    // ─── 8. updatePermissionsShouldRequireAtLeastOnePermissionManager ──────────

    @Test
    void updatePermissionsShouldRequireAtLeastOnePermissionManager() {
        // 从兼容模式首次插入权限（currentManagers=0），新权限不含 ADMIN_PERMISSIONS_MANAGE → 拒绝
        when(userMapper.selectUserIncludingDeleted(2L)).thenReturn(newUser(2L, "admin2", "a2@x.com", "ADMIN", 0));
        when(permissionMapper.selectByUserId(2L)).thenReturn(List.of()); // target 当前无权限
        when(permissionMapper.countUsersWithPermission(AdminPermission.ADMIN_PERMISSIONS_MANAGE.name()))
                .thenReturn(0L); // 系统中目前没有任何 manager（兼容模式）

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.updateUserPermissions(1L, 2L, Set.of(AdminPermission.ADMIN_USERS_VIEW.name())));
        assertTrue(ex.getMessage().contains("至少保留一个 ADMIN_PERMISSIONS_MANAGE"));
        verify(permissionMapper, never()).deleteByUserId(anyLong());
        verify(permissionMapper, never()).insert(any(AdminUserPermission.class));
    }

    // ─── 9. getMyPermissionsShouldReturnAllWhenExplicitModeDisabled ────────────

    @Test
    void getMyPermissionsShouldReturnAllWhenExplicitModeDisabled() {
        when(permissionMapper.countAllPermissions()).thenReturn(0L);
        when(userMapper.selectUserIncludingDeleted(1L)).thenReturn(newUser(1L, "admin", "a@x.com", "ADMIN", 0));

        AdminPermissionDto dto = service.buildPermissionDto(1L);

        assertEquals(1L, dto.getUserId());
        assertEquals("admin", dto.getUsername());
        assertEquals("ADMIN", dto.getRole());
        assertFalse(dto.getExplicitMode(), "表为空时应为兼容模式");
        assertEquals(AdminPermission.values().length, dto.getPermissions().size());
        for (AdminPermission p : AdminPermission.values()) {
            assertTrue(dto.getPermissions().contains(p.name()));
        }
    }

    // ─── 10. dtoShouldNotExposeSensitiveFields ─────────────────────────────────

    @Test
    void dtoShouldNotExposeSensitiveFields() {
        assertFalse(hasField(AdminPermissionDto.class, "password"), "AdminPermissionDto 不应含 password");
        assertFalse(hasField(AdminPermissionDto.class, "apiKey"), "AdminPermissionDto 不应含 apiKey");
        assertFalse(hasField(AdminPermissionDto.class, "api_key"), "AdminPermissionDto 不应含 api_key");
        assertFalse(hasField(AdminPermissionDto.class, "encryptedKey"), "AdminPermissionDto 不应含 encryptedKey");
        assertFalse(hasField(AdminPermissionDto.class, "maskedKey"), "AdminPermissionDto 不应含 maskedKey");
        assertFalse(hasField(AdminPermissionDto.class, "baseUrl"), "AdminPermissionDto 不应含 baseUrl");
        assertFalse(hasField(AdminPermissionDto.class, "model"), "AdminPermissionDto 不应含 model");
    }

    // ─── 额外：updateUserPermissions 成功路径（先删后插）─────────────────────────

    @Test
    void updatePermissionsShouldDeleteThenInsertOnSuccess() {
        // target=2，当前无权限；新权限含 ADMIN_PERMISSIONS_MANAGE + ADMIN_USERS_VIEW
        when(userMapper.selectUserIncludingDeleted(2L)).thenReturn(newUser(2L, "admin2", "a2@x.com", "ADMIN", 0));
        // selectByUserId 会被调用两次：第 1 次检查当前是否有 ADMIN_PERMISSIONS_MANAGE（返回空），
        // 第 2 次在 buildPermissionDto 末尾读取更新后的权限（返回新插入的 2 条）
        when(permissionMapper.selectByUserId(2L))
                .thenReturn(List.of())
                .thenReturn(List.of(
                        newPerm(20L, 2L, AdminPermission.ADMIN_PERMISSIONS_MANAGE.name()),
                        newPerm(21L, 2L, AdminPermission.ADMIN_USERS_VIEW.name())));
        when(permissionMapper.countUsersWithPermission(AdminPermission.ADMIN_PERMISSIONS_MANAGE.name()))
                .thenReturn(1L); // 系统已有 1 个 manager（其他人），target 也加 manager 不冲突
        when(permissionMapper.countAllPermissions()).thenReturn(5L);

        AdminPermissionDto dto = service.updateUserPermissions(1L, 2L,
                Set.of(AdminPermission.ADMIN_PERMISSIONS_MANAGE.name(), AdminPermission.ADMIN_USERS_VIEW.name()));

        // 应先删后插
        verify(permissionMapper, times(1)).deleteByUserId(2L);
        verify(permissionMapper, times(2)).insert(any(AdminUserPermission.class));
        // 返回的 DTO 应包含新权限
        assertEquals(2, dto.getPermissions().size());
        assertTrue(dto.getPermissions().contains(AdminPermission.ADMIN_PERMISSIONS_MANAGE.name()));
        assertTrue(dto.getPermissions().contains(AdminPermission.ADMIN_USERS_VIEW.name()));
    }

    // ─── 额外：requirePermission 无权限时抛 AccessDeniedException ───────────────

    @Test
    void requirePermissionShouldThrowAccessDeniedWhenLackingPermission() {
        // 显式模式 + USER → 无权限
        when(permissionMapper.countAllPermissions()).thenReturn(5L);
        when(userMapper.selectUserIncludingDeleted(1L)).thenReturn(newUser(1L, "alice", "a@x.com", "USER", 0));

        assertThrows(AccessDeniedException.class,
                () -> service.requirePermission(1L, AdminPermission.ADMIN_USERS_VIEW));
    }

    // ─── helper ─────────────────────────────────────────────────────────────────

    private User newUser(Long id, String username, String email, String role, Integer deleted) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setEmail(email);
        u.setRole(role);
        u.setDeleted(deleted);
        return u;
    }

    private AdminUserPermission newPerm(Long id, Long userId, String permission) {
        AdminUserPermission p = new AdminUserPermission();
        p.setId(id);
        p.setUserId(userId);
        p.setPermission(permission);
        return p;
    }

    private boolean hasField(Class<?> clazz, String fieldName) {
        for (Field f : clazz.getDeclaredFields()) {
            if (f.getName().equalsIgnoreCase(fieldName)) {
                return true;
            }
        }
        return false;
    }
}
