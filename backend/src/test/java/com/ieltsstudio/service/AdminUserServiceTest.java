package com.ieltsstudio.service;

import com.ieltsstudio.dto.admin.AdminCreateUserRequest;
import com.ieltsstudio.dto.admin.AdminUserDto;
import com.ieltsstudio.dto.admin.AdminUserPageDto;
import com.ieltsstudio.entity.User;
import com.ieltsstudio.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
 * {@link AdminUserService} 单元测试（Phase 8A）。
 *
 * <p>不连接真实数据库：{@link UserMapper} 与 {@link PasswordEncoder} 用 Mockito mock。
 * 验证分页 clamp、role/status 过滤、角色修改保护、禁用/启用保护、重置密码使用 BCrypt、
 * DTO 不暴露 password。</p>
 */
class AdminUserServiceTest {

    private UserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private AdminUserService service;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new AdminUserService(userMapper, passwordEncoder);
    }

    // ─── 1. listUsers 应 clamp page / pageSize ─────────────────────────────────

    @Test
    void listUsersShouldClampPageAndPageSize() {
        when(userMapper.countAdminUsers(any(), any(), any())).thenReturn(0L);
        when(userMapper.selectAdminUsers(anyInt(), anyInt(), any(), any(), any())).thenReturn(List.of());

        // page=0 应 clamp 到 1，pageSize=999 应 clamp 到 100
        AdminUserPageDto dto = service.listUsers(0, 999, null, null, null);

        assertEquals(1L, dto.getPage(), "page<1 应 clamp 到 1");
        assertEquals(100L, dto.getPageSize(), "pageSize>100 应 clamp 到 100");
        // offset 应为 (1-1)*100 = 0
        verify(userMapper, times(1)).selectAdminUsers(eq(0), eq(100), any(), any(), any());
    }

    // ─── 2. listUsers 应按 role / status 过滤 ──────────────────────────────────

    @Test
    void listUsersShouldFilterRoleAndStatus() {
        when(userMapper.countAdminUsers(any(), any(), any())).thenReturn(1L);
        User u = newUser(1L, "alice", "alice@test.com", "ADMIN", 0);
        when(userMapper.selectAdminUsers(anyInt(), anyInt(), any(), any(), any())).thenReturn(List.of(u));

        // role=ADMIN, status=ACTIVE → deleted=0
        AdminUserPageDto dto = service.listUsers(1, 20, null, "ADMIN", "ACTIVE");

        assertNotNull(dto);
        assertEquals(1, dto.getRecords().size());
        verify(userMapper, times(1)).countAdminUsers(eq(null), eq("ADMIN"), eq(0));
        verify(userMapper, times(1)).selectAdminUsers(anyInt(), anyInt(), eq(null), eq("ADMIN"), eq(0));

        // status=DISABLED → deleted=1
        service.listUsers(1, 20, null, null, "DISABLED");
        verify(userMapper, times(1)).countAdminUsers(eq(null), eq(null), eq(1));

        // status=ALL → deleted=null
        service.listUsers(1, 20, null, null, "ALL");
        verify(userMapper, times(1)).countAdminUsers(eq(null), eq(null), eq(null));

        // 非法 role 应抛业务错误
        assertThrows(IllegalArgumentException.class,
                () -> service.listUsers(1, 20, null, "SUPER_ADMIN", null));

        // 非法 status 应抛业务错误
        assertThrows(IllegalArgumentException.class,
                () -> service.listUsers(1, 20, null, null, "HACKED"));
    }

    // ─── 3. updateRole 应拒绝非法 role ─────────────────────────────────────────

    @Test
    void updateRoleShouldRejectInvalidRole() {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateRole(1L, 2L, "SUPER_ADMIN"));
        assertThrows(IllegalArgumentException.class,
                () -> service.updateRole(1L, 2L, null));
        // 不应查库
        verify(userMapper, never()).selectUserIncludingDeleted(anyLong());
    }

    // ─── 4. updateRole 应拒绝降级自己 ──────────────────────────────────────────

    @Test
    void updateRoleShouldRejectDemotingSelf() {
        User target = newUser(1L, "admin", "admin@test.com", "ADMIN", 0);
        when(userMapper.selectUserIncludingDeleted(1L)).thenReturn(target);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.updateRole(1L, 1L, "USER"));
        assertTrue(ex.getMessage().contains("不能降级自己"), "应拒绝降级自己，实际消息=" + ex.getMessage());
        // 不应执行更新
        verify(userMapper, never()).updateRoleById(anyLong(), anyString());
    }

    // ─── 5. updateRole 应拒绝降级最后一个 ADMIN ────────────────────────────────

    @Test
    void updateRoleShouldRejectDemotingLastAdmin() {
        User target = newUser(2L, "other", "other@test.com", "ADMIN", 0);
        when(userMapper.selectUserIncludingDeleted(2L)).thenReturn(target);
        when(userMapper.countActiveAdmins()).thenReturn(1L);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.updateRole(1L, 2L, "USER"));
        assertTrue(ex.getMessage().contains("最后一个管理员"), "应拒绝降级最后一个 ADMIN，实际消息=" + ex.getMessage());
        verify(userMapper, never()).updateRoleById(anyLong(), anyString());
    }

    // ─── 6. disableUser 应拒绝禁用自己 ─────────────────────────────────────────

    @Test
    void disableUserShouldRejectSelf() {
        User target = newUser(1L, "admin", "admin@test.com", "ADMIN", 0);
        when(userMapper.selectUserIncludingDeleted(1L)).thenReturn(target);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.disableUser(1L, 1L));
        assertTrue(ex.getMessage().contains("不能禁用自己"), "应拒绝禁用自己，实际消息=" + ex.getMessage());
        verify(userMapper, never()).updateDeletedById(anyLong(), anyInt());
    }

    // ─── 7. disableUser 应拒绝禁用最后一个 ADMIN ───────────────────────────────

    @Test
    void disableUserShouldRejectLastAdmin() {
        User target = newUser(2L, "other", "other@test.com", "ADMIN", 0);
        when(userMapper.selectUserIncludingDeleted(2L)).thenReturn(target);
        when(userMapper.countActiveAdmins()).thenReturn(1L);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.disableUser(1L, 2L));
        assertTrue(ex.getMessage().contains("最后一个管理员"), "应拒绝禁用最后一个 ADMIN，实际消息=" + ex.getMessage());
        verify(userMapper, never()).updateDeletedById(anyLong(), anyInt());
    }

    // ─── 8. enableUser 应恢复 deleted=0 ────────────────────────────────────────

    @Test
    void enableUserShouldRestoreDeletedFlag() {
        User disabled = newUser(3L, "bob", "bob@test.com", "USER", 1);
        User enabled = newUser(3L, "bob", "bob@test.com", "USER", 0);
        when(userMapper.selectUserIncludingDeleted(3L)).thenReturn(disabled).thenReturn(enabled);

        AdminUserDto dto = service.enableUser(1L, 3L);

        verify(userMapper, times(1)).updateDeletedById(3L, 0);
        assertEquals(0, dto.getDeleted(), "启用后 deleted 应为 0");
        assertEquals("USER", dto.getRole(), "启用不应改变 role");
    }

    // ─── 9. resetPassword 应使用 PasswordEncoder ───────────────────────────────

    @Test
    void resetPasswordShouldUsePasswordEncoder() {
        User target = newUser(4L, "carol", "carol@test.com", "USER", 0);
        when(userMapper.selectUserIncludingDeleted(4L)).thenReturn(target);
        when(passwordEncoder.encode("NewStrong123!")).thenReturn("$2a$10$hashedvalue");

        service.resetPassword(4L, "NewStrong123!");

        // 应调用 encode
        verify(passwordEncoder, times(1)).encode("NewStrong123!");
        // 应调用 mapper 更新密码哈希
        verify(userMapper, times(1)).updatePasswordById(4L, "$2a$10$hashedvalue");
    }

    // ─── 10. DTO 不应暴露 password ─────────────────────────────────────────────

    @Test
    void dtoShouldNotExposePassword() throws Exception {
        User user = newUser(1L, "alice", "alice@test.com", "ADMIN", 0);
        user.setPassword("$2a$10$secret_hash");

        // 通过反射调用 private toDto
        java.lang.reflect.Method toDto = AdminUserService.class.getDeclaredMethod("toDto", User.class);
        toDto.setAccessible(true);
        AdminUserDto dto = (AdminUserDto) toDto.invoke(service, user);

        // DTO 类不应有 password 字段
        Field[] fields = AdminUserDto.class.getDeclaredFields();
        for (Field f : fields) {
            assertFalse(f.getName().equalsIgnoreCase("password"),
                    "AdminUserDto 不应包含 password 字段，发现=" + f.getName());
        }
        // password 不应被复制（即使 User 有 password 字段，DTO 也没有对应 setter 可接收）
        // 验证 DTO 不含 password 的 getter
        assertFalse(hasField(AdminUserDto.class, "password"),
                "AdminUserDto 不应有 password 字段");
    }

    // ─── 11. createUser 应拒绝非法 role ────────────────────────────────────────

    @Test
    void createUserShouldRejectInvalidRole() {
        AdminCreateUserRequest req = buildCreateRequest("bob", "bob@test.com", "Password123!", "SUPER_ADMIN");
        assertThrows(IllegalArgumentException.class, () -> service.createUser(req),
                "role=SUPER_ADMIN 应被拒绝");
        verify(userMapper, never()).insert(any(User.class));
    }

    // ─── 12. createUser 应拒绝用户名/邮箱占用 ──────────────────────────────────

    @Test
    void createUserShouldRejectDuplicateUsernameOrEmail() {
        when(userMapper.countByUsernameOrEmail("bob", "bob@test.com")).thenReturn(1L);
        AdminCreateUserRequest req = buildCreateRequest("bob", "bob@test.com", "Password123!", "USER");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.createUser(req));
        assertTrue(ex.getMessage().contains("已被占用"), "应提示已被占用");
        verify(userMapper, never()).insert(any(User.class));
        // 不应接触 password 编码
        verify(passwordEncoder, never()).encode(anyString());
    }

    // ─── 13. createUser 正常创建并 BCrypt 加密、不返回 password ─────────────────

    @Test
    void createUserShouldEncodePasswordAndNotExposeIt() {
        when(userMapper.countByUsernameOrEmail("carol", "carol@test.com")).thenReturn(0L);
        when(passwordEncoder.encode("Password123!")).thenReturn("$2a$10$encoded_hash");
        User saved = newUser(99L, "carol", "carol@test.com", "USER", 0);
        saved.setPassword("$2a$10$encoded_hash");
        when(userMapper.selectByUsernameOrEmailIncludingDeleted("carol", "carol@test.com")).thenReturn(saved);

        AdminCreateUserRequest req = buildCreateRequest("carol", "carol@test.com", "Password123!", "USER");
        AdminUserDto dto = service.createUser(req);

        // 验证 BCrypt 加密被调用
        verify(passwordEncoder, times(1)).encode("Password123!");
        // 验证 insert 被调用
        verify(userMapper, times(1)).insert(any(User.class));
        // 返回的 DTO 不含 password
        assertEquals(99L, dto.getId());
        assertEquals("carol", dto.getUsername());
        assertEquals("USER", dto.getRole());
        assertFalse(hasField(AdminUserDto.class, "password"), "DTO 不应有 password 字段");
    }

    // ─── 14. createUser 可创建 ADMIN 角色 ──────────────────────────────────────

    @Test
    void createUserShouldAllowAdminRole() {
        when(userMapper.countByUsernameOrEmail(anyString(), anyString())).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hash");
        User saved = newUser(50L, "dave", "dave@test.com", "ADMIN", 0);
        when(userMapper.selectByUsernameOrEmailIncludingDeleted(anyString(), anyString())).thenReturn(saved);

        AdminCreateUserRequest req = buildCreateRequest("dave", "dave@test.com", "Password123!", "ADMIN");
        AdminUserDto dto = service.createUser(req);

        assertEquals("ADMIN", dto.getRole());
    }

    private AdminCreateUserRequest buildCreateRequest(String username, String email, String password, String role) {
        AdminCreateUserRequest req = new AdminCreateUserRequest();
        req.setUsername(username);
        req.setEmail(email);
        req.setPassword(password);
        req.setRole(role);
        return req;
    }

    // ─── 辅助 ───────────────────────────────────────────────────────────────────

    private User newUser(Long id, String username, String email, String role, Integer deleted) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setEmail(email);
        u.setRole(role);
        u.setDeleted(deleted);
        u.setPassword("$2a$10$existing_hash");
        return u;
    }

    private boolean hasField(Class<?> clazz, String fieldName) {
        for (Field f : clazz.getDeclaredFields()) {
            if (f.getName().equals(fieldName)) return true;
        }
        return false;
    }
}
