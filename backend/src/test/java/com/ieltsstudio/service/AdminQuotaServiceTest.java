package com.ieltsstudio.service;

import com.ieltsstudio.dto.admin.AdminQuotaDto;
import com.ieltsstudio.dto.admin.AdminQuotaPageDto;
import com.ieltsstudio.entity.AiUsageQuota;
import com.ieltsstudio.entity.User;
import com.ieltsstudio.mapper.AiUsageQuotaMapper;
import com.ieltsstudio.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdminQuotaService} 单元测试（Phase 8B）。
 *
 * <p>不连接真实数据库：{@link UserMapper} 与 {@link AiUsageQuotaMapper} 用 Mockito mock。
 * 验证虚拟默认视图、quota 行创建/更新、setTotal/grant/resetUsed 规则、非法输入拒绝、
 * DTO 不暴露敏感字段。</p>
 */
class AdminQuotaServiceTest {

    private UserMapper userMapper;
    private AiUsageQuotaMapper quotaMapper;
    private AdminQuotaService service;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        quotaMapper = mock(AiUsageQuotaMapper.class);
        service = new AdminQuotaService(userMapper, quotaMapper);
    }

    // ─── 1. listQuotas 无 quota 行时返回虚拟默认 100/0/100，不 insert ─────────────

    @Test
    void listQuotasShouldReturnVirtualDefaultWhenQuotaMissing() {
        when(userMapper.countAdminUsers(any(), any(), any())).thenReturn(1L);
        User u = newUser(1L, "alice", "alice@test.com", "USER", 0);
        when(userMapper.selectAdminUsers(anyInt(), anyInt(), any(), any(), any())).thenReturn(List.of(u));
        // quota 查询返回 null（无 quota 行）
        when(quotaMapper.selectOne(any())).thenReturn(null);

        AdminQuotaPageDto dto = service.listQuotas(1, 20, null, null, null);

        assertEquals(1, dto.getRecords().size());
        AdminQuotaDto q = dto.getRecords().get(0);
        assertEquals(100, q.getCreditsTotal(), "无 quota 行应返回默认 100");
        assertEquals(0, q.getCreditsUsed());
        assertEquals(100, q.getCreditsRemaining());
        assertFalse(q.getQuotaRowExists(), "quotaRowExists 应为 false");
        // 不应创建 quota 行
        verify(quotaMapper, never()).insert(any(AiUsageQuota.class));
    }

    // ─── 2. getUserQuota 有 quota 行时返回真实数据 ──────────────────────────────

    @Test
    void getUserQuotaShouldReturnExistingQuota() {
        when(userMapper.selectUserIncludingDeleted(1L)).thenReturn(newUser(1L, "bob", "bob@test.com", "USER", 0));
        AiUsageQuota quota = newQuota(10L, 1L, 100, 20);
        when(quotaMapper.selectOne(any())).thenReturn(quota);

        AdminQuotaDto dto = service.getUserQuota(1L);

        assertEquals(100, dto.getCreditsTotal());
        assertEquals(20, dto.getCreditsUsed());
        assertEquals(80, dto.getCreditsRemaining());
        assertTrue(dto.getQuotaRowExists());
        verify(quotaMapper, never()).insert(any(AiUsageQuota.class));
    }

    // ─── 3. setTotal 无 quota 行时创建当前周期 quota ────────────────────────────

    @Test
    void setTotalShouldCreateQuotaWhenMissing() {
        when(userMapper.selectUserIncludingDeleted(1L)).thenReturn(newUser(1L, "carol", "carol@test.com", "USER", 0));
        // 首次查询无 quota 行，insert 后再次查询返回新建的行
        when(quotaMapper.selectOne(any())).thenReturn(null, newQuota(10L, 1L, 50, 0));

        AdminQuotaDto dto = service.setTotal(1L, 50);

        verify(quotaMapper, times(1)).insert(any(AiUsageQuota.class));
        assertEquals(50, dto.getCreditsTotal());
        assertEquals(0, dto.getCreditsUsed());
        assertTrue(dto.getQuotaRowExists());
    }

    // ─── 4. setTotal 已用 20，设置 total 10 应拒绝 ──────────────────────────────

    @Test
    void setTotalShouldRejectWhenTotalLessThanUsed() {
        when(userMapper.selectUserIncludingDeleted(1L)).thenReturn(newUser(1L, "dave", "dave@test.com", "USER", 0));
        when(quotaMapper.selectOne(any())).thenReturn(newQuota(10L, 1L, 30, 20)); // 已用 20

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.setTotal(1L, 10));
        assertTrue(ex.getMessage().contains("不能小于当前已用额度"));
        // 不应更新
        verify(quotaMapper, never()).updateById(any(AiUsageQuota.class));
    }

    // ─── 5. setTotal 已存在 quota 时更新 creditsTotal，不修改 used ───────────────

    @Test
    void setTotalShouldUpdateExistingQuota() {
        when(userMapper.selectUserIncludingDeleted(1L)).thenReturn(newUser(1L, "eve", "eve@test.com", "USER", 0));
        AiUsageQuota existing = newQuota(10L, 1L, 30, 5);
        when(quotaMapper.selectOne(any())).thenReturn(existing);

        AdminQuotaDto dto = service.setTotal(1L, 100);

        assertEquals(100, existing.getCreditsTotal(), "creditsTotal 应被更新为 100");
        assertEquals(5, existing.getCreditsUsed(), "creditsUsed 不应被修改");
        verify(quotaMapper, times(1)).updateById(existing);
        assertEquals(100, dto.getCreditsTotal());
        assertEquals(95, dto.getCreditsRemaining());
    }

    // ─── 6. grantCredits 无 quota 行时创建 100 + grant ───────────────────────────

    @Test
    void grantCreditsShouldCreateQuotaWhenMissing() {
        when(userMapper.selectUserIncludingDeleted(1L)).thenReturn(newUser(1L, "frank", "frank@test.com", "USER", 0));
        when(quotaMapper.selectOne(any())).thenReturn(null, newQuota(10L, 1L, 120, 0));

        AdminQuotaDto dto = service.grantCredits(1L, 20);

        verify(quotaMapper, times(1)).insert(any(AiUsageQuota.class));
        // 新建时 creditsTotal = 100 + 20 = 120，creditsUsed = 0
        assertEquals(120, dto.getCreditsTotal());
        assertEquals(0, dto.getCreditsUsed());
    }

    // ─── 7. grantCredits 已存在 quota 时增加 total，不修改 used ──────────────────

    @Test
    void grantCreditsShouldIncreaseExistingTotal() {
        when(userMapper.selectUserIncludingDeleted(1L)).thenReturn(newUser(1L, "grace", "grace@test.com", "USER", 0));
        AiUsageQuota existing = newQuota(10L, 1L, 100, 8);
        when(quotaMapper.selectOne(any())).thenReturn(existing);

        AdminQuotaDto dto = service.grantCredits(1L, 20);

        assertEquals(120, existing.getCreditsTotal(), "creditsTotal 应为 100+20=120");
        assertEquals(8, existing.getCreditsUsed(), "creditsUsed 不应被修改");
        verify(quotaMapper, times(1)).updateById(existing);
        assertEquals(120, dto.getCreditsTotal());
        assertEquals(112, dto.getCreditsRemaining());
    }

    // ─── 8. resetUsed 无 quota 行时创建默认 quota（100/0）───────────────────────

    @Test
    void resetUsedShouldCreateDefaultQuotaWhenMissing() {
        when(userMapper.selectUserIncludingDeleted(1L)).thenReturn(newUser(1L, "henry", "henry@test.com", "USER", 0));
        when(quotaMapper.selectOne(any())).thenReturn(null, newQuota(10L, 1L, 100, 0));

        AdminQuotaDto dto = service.resetUsed(1L);

        verify(quotaMapper, times(1)).insert(any(AiUsageQuota.class));
        assertEquals(100, dto.getCreditsTotal());
        assertEquals(0, dto.getCreditsUsed());
        assertTrue(dto.getQuotaRowExists());
    }

    // ─── 9. resetUsed 已存在 quota 时将 used 改为 0 ─────────────────────────────

    @Test
    void resetUsedShouldSetUsedToZero() {
        when(userMapper.selectUserIncludingDeleted(1L)).thenReturn(newUser(1L, "ivan", "ivan@test.com", "USER", 0));
        AiUsageQuota existing = newQuota(10L, 1L, 100, 50);
        when(quotaMapper.selectOne(any())).thenReturn(existing);

        AdminQuotaDto dto = service.resetUsed(1L);

        assertEquals(0, existing.getCreditsUsed(), "creditsUsed 应被重置为 0");
        assertEquals(100, existing.getCreditsTotal(), "creditsTotal 不应被修改");
        verify(quotaMapper, times(1)).updateById(existing);
        verify(quotaMapper, never()).insert(any(AiUsageQuota.class));
        assertEquals(0, dto.getCreditsUsed());
        assertEquals(100, dto.getCreditsTotal());
    }

    // ─── 10. 非法输入应被拒绝 ────────────────────────────────────────────────────

    @Test
    void invalidInputsShouldBeRejected() {
        // negative total —— 在 Controller 层由 @Min 拦截，service 仍校验语义；
        // 这里主要验证 user not found 与非法 role/status
        // user not found
        when(userMapper.selectUserIncludingDeleted(999L)).thenReturn(null);
        RuntimeException ex1 = assertThrows(RuntimeException.class, () -> service.getUserQuota(999L));
        assertTrue(ex1.getMessage().contains("用户不存在"));

        // 非法 role
        when(userMapper.countAdminUsers(any(), any(), any())).thenReturn(0L);
        when(userMapper.selectAdminUsers(anyInt(), anyInt(), any(), any(), any())).thenReturn(List.of());
        RuntimeException ex2 = assertThrows(RuntimeException.class,
                () -> service.listQuotas(1, 20, null, "SUPER_ADMIN", null));
        assertTrue(ex2.getMessage().contains("非法的 role"));

        // 非法 status
        RuntimeException ex3 = assertThrows(RuntimeException.class,
                () -> service.listQuotas(1, 20, null, null, "WRONG"));
        assertTrue(ex3.getMessage().contains("非法的 status"));
    }

    // ─── 11. DTO 不应暴露敏感字段 ────────────────────────────────────────────────

    @Test
    void dtoShouldNotExposeSensitiveFields() {
        when(userMapper.selectUserIncludingDeleted(1L)).thenReturn(newUser(1L, "kate", "kate@test.com", "USER", 0));
        when(quotaMapper.selectOne(any())).thenReturn(newQuota(10L, 1L, 30, 0));

        AdminQuotaDto dto = service.getUserQuota(1L);

        assertNotNull(dto);
        assertFalse(hasField(AdminQuotaDto.class, "password"), "DTO 不应含 password");
        assertFalse(hasField(AdminQuotaDto.class, "apiKey"), "DTO 不应含 apiKey");
        assertFalse(hasField(AdminQuotaDto.class, "encryptedKey"), "DTO 不应含 encryptedKey");
        assertFalse(hasField(AdminQuotaDto.class, "maskedKey"), "DTO 不应含 maskedKey");
        assertFalse(hasField(AdminQuotaDto.class, "baseUrl"), "DTO 不应含 baseUrl");
        assertFalse(hasField(AdminQuotaDto.class, "model"), "DTO 不应含 model");
    }

    // ─── 辅助 ───────────────────────────────────────────────────────────────────

    private User newUser(Long id, String username, String email, String role, Integer deleted) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setEmail(email);
        u.setRole(role);
        u.setDeleted(deleted);
        return u;
    }

    private AiUsageQuota newQuota(Long id, Long userId, int total, int used) {
        AiUsageQuota q = new AiUsageQuota();
        q.setId(id);
        q.setUserId(userId);
        q.setPeriodStart(LocalDateTime.now().minusDays(1));
        q.setPeriodEnd(LocalDateTime.now().plusDays(6));
        q.setCreditsTotal(total);
        q.setCreditsUsed(used);
        return q;
    }

    private boolean hasField(Class<?> clazz, String fieldName) {
        for (Field f : clazz.getDeclaredFields()) {
            if (f.getName().equals(fieldName)) {
                return true;
            }
        }
        return false;
    }
}
