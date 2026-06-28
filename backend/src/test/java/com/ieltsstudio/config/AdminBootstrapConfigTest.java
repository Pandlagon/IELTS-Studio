package com.ieltsstudio.config;

import com.ieltsstudio.entity.User;
import com.ieltsstudio.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdminBootstrapConfig} 单元测试。
 *
 * <p>覆盖启动时管理员初始化的关键分支：未配置跳过、已有 ADMIN 跳过、
 * 升级现有 USER（含重置密码+启用）、创建新 ADMIN。</p>
 */
class AdminBootstrapConfigTest {

    private UserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private AdminBootstrapConfig config;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        config = new AdminBootstrapConfig(userMapper, passwordEncoder);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
    }

    // ─── 1. 三项任一为空 → 跳过 ────────────────────────────────────────────────

    @Test
    void shouldSkipWhenConfigBlank() {
        config.initAdminIfNeeded("", "a@b.com", "password123");
        config.initAdminIfNeeded("admin", "", "password123");
        config.initAdminIfNeeded("admin", "a@b.com", "");
        config.initAdminIfNeeded(null, "a@b.com", "password123");

        verify(userMapper, never()).countAllAdminsIncludingDeleted();
        verify(userMapper, never()).insert(any(User.class));
    }

    // ─── 2. 库中已有 ADMIN → 跳过 ──────────────────────────────────────────────

    @Test
    void shouldSkipWhenAdminAlreadyExists() {
        when(userMapper.countAllAdminsIncludingDeleted()).thenReturn(1L);

        config.initAdminIfNeeded("admin", "a@b.com", "password123");

        verify(userMapper, never()).insert(any(User.class));
        verify(userMapper, never()).updateRoleById(anyLong(), anyString());
        verify(userMapper, never()).updatePasswordById(anyLong(), anyString());
    }

    // ─── 3. 用户名已存在（USER）→ 升级 + 重置密码 + 启用 ────────────────────────

    @Test
    void shouldUpgradeExistingUserWhenUsernameTaken() {
        when(userMapper.countAllAdminsIncludingDeleted()).thenReturn(0L);
        User existing = new User();
        existing.setId(42L);
        existing.setUsername("admin");
        existing.setEmail("old@test.com");
        existing.setRole("USER");
        existing.setDeleted(1); // 被禁用，应一并启用
        when(userMapper.selectByUsernameOrEmailIncludingDeleted("admin", "a@b.com")).thenReturn(existing);

        config.initAdminIfNeeded("admin", "a@b.com", "NewPass123!");

        // 升级 role
        verify(userMapper, times(1)).updateRoleById(42L, "ADMIN");
        // 重置密码（BCrypt）
        verify(passwordEncoder, times(1)).encode("NewPass123!");
        verify(userMapper, times(1)).updatePasswordById(42L, "$2a$10$hashed");
        // 禁用账号应启用
        verify(userMapper, times(1)).updateDeletedById(42L, 0);
        // 不应 insert 新用户
        verify(userMapper, never()).insert(any(User.class));
    }

    // ─── 3b. 用户名已存在且启用 → 升级 + 重置密码，不重复启用 ──────────────────

    @Test
    void shouldUpgradeExistingActiveUserWithoutReEnabling() {
        when(userMapper.countAllAdminsIncludingDeleted()).thenReturn(0L);
        User existing = new User();
        existing.setId(7L);
        existing.setUsername("admin");
        existing.setRole("USER");
        existing.setDeleted(0); // 已启用
        when(userMapper.selectByUsernameOrEmailIncludingDeleted(anyString(), anyString())).thenReturn(existing);

        config.initAdminIfNeeded("admin", "a@b.com", "NewPass123!");

        verify(userMapper, times(1)).updateRoleById(7L, "ADMIN");
        verify(userMapper, times(1)).updatePasswordById(eq(7L), anyString());
        // 已启用 → 不应调用 updateDeletedById
        verify(userMapper, never()).updateDeletedById(anyLong(), anyInt());
    }

    // ─── 4. 用户名不存在 → 创建新 ADMIN ────────────────────────────────────────

    @Test
    void shouldCreateNewAdminWhenUsernameFree() {
        when(userMapper.countAllAdminsIncludingDeleted()).thenReturn(0L);
        when(userMapper.selectByUsernameOrEmailIncludingDeleted(anyString(), anyString())).thenReturn(null);

        config.initAdminIfNeeded("sysadmin", "sys@test.com", "StrongPass123!");

        verify(userMapper, times(1)).insert(any(User.class));
        verify(passwordEncoder, times(1)).encode("StrongPass123!");
        // 不应走升级分支
        verify(userMapper, never()).updateRoleById(anyLong(), anyString());
    }
}
