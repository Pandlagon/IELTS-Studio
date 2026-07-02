package com.ieltsstudio.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ieltsstudio.dto.admin.AdminGrantCreditsRequest;
import com.ieltsstudio.dto.admin.AdminQuotaDto;
import com.ieltsstudio.dto.admin.AdminQuotaPageDto;
import com.ieltsstudio.entity.AiUsageQuota;
import com.ieltsstudio.entity.User;
import com.ieltsstudio.mapper.AiUsageQuotaMapper;
import com.ieltsstudio.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;

/**
 * 管理端 AI 额度管理服务（Phase 8B）。
 *
 * <p>仅管理当前自然周期 quota 行：
 * <ul>
 *   <li>读操作（list / get）在无 quota 行时返回虚拟默认视图（30/0/30），不创建行。</li>
 *   <li>写操作（setTotal / grant / resetUsed）在无 quota 行时创建当前周期 quota 行。</li>
 *   <li>周期计算与 {@code AiUsageGuard} / {@code AiUsageQueryService} 保持一致：
 *       周一 00:00:00 到下周一 00:00:00（服务器默认时区）。</li>
 *   <li>不修改 AI Provider 调用链、不修改扣费逻辑、不修改 usage records。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminQuotaService {

    /** 每用户每周默认发放 credits，与 AiUsageGuard / AiUsageQueryService 保持一致 */
    private static final int DEFAULT_WEEKLY_CREDITS = 100;
    private static final Set<String> ALLOWED_ROLES = Set.of("USER", "ADMIN");

    private final UserMapper userMapper;
    private final AiUsageQuotaMapper quotaMapper;

    // ─── 查询 ───────────────────────────────────────────────────────────────────

    /**
     * 分页查询用户当前周期 quota 列表。无 quota 行的用户返回虚拟默认视图，不创建行。
     */
    public AdminQuotaPageDto listQuotas(int page, int pageSize, String keyword, String role, String status) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        String safeRole = trimToNull(role);
        if (safeRole != null && !ALLOWED_ROLES.contains(safeRole)) {
            throw new IllegalArgumentException("非法的 role 值：" + role);
        }
        Integer deleted = parseStatus(status);
        String kw = trimToNull(keyword);
        Period period = currentPeriod();

        long total = userMapper.countAdminUsers(kw, safeRole, deleted);
        int offset = (safePage - 1) * safePageSize;
        List<User> users = userMapper.selectAdminUsers(offset, safePageSize, kw, safeRole, deleted);

        AdminQuotaPageDto dto = new AdminQuotaPageDto();
        dto.setRecords(users.stream()
                .map(u -> buildDto(u, findCurrentQuota(u.getId(), period), period))
                .toList());
        dto.setTotal(total);
        dto.setPage((long) safePage);
        dto.setPageSize((long) safePageSize);
        dto.setPages(safePageSize == 0 ? 0L : (long) Math.ceil((double) total / safePageSize));
        return dto;
    }

    /**
     * 查询单个用户当前周期 quota。无 quota 行时返回虚拟默认视图，不创建行。
     */
    public AdminQuotaDto getUserQuota(Long userId) {
        User user = loadUserOrThrow(userId);
        Period period = currentPeriod();
        return buildDto(user, findCurrentQuota(userId, period), period);
    }

    // ─── 写操作 ─────────────────────────────────────────────────────────────────

    /**
     * 设置当前周期 creditsTotal。
     * <ul>
     *   <li>无 quota 行：创建当前周期 quota 行（creditsTotal=request, creditsUsed=0）。</li>
     *   <li>已存在：更新 creditsTotal，不修改 creditsUsed。</li>
     *   <li>若 creditsTotal < 当前 creditsUsed，拒绝。</li>
     * </ul>
     */
    public AdminQuotaDto setTotal(Long userId, Integer creditsTotal) {
        User user = loadUserOrThrow(userId);
        Period period = currentPeriod();
        AiUsageQuota quota = findCurrentQuota(userId, period);
        if (quota == null) {
            quota = safeInsert(newQuota(userId, period, creditsTotal, 0), userId, period);
        }
        int used = quota.getCreditsUsed() == null ? 0 : quota.getCreditsUsed();
        if (creditsTotal < used) {
            throw new RuntimeException("creditsTotal 不能小于当前已用额度：" + used);
        }
        quota.setCreditsTotal(creditsTotal);
        quotaMapper.updateById(quota);
        log.info("管理员设置总额度：userId={}, creditsTotal={}", userId, creditsTotal);
        return buildDto(user, findCurrentQuota(userId, period), period);
    }

    /**
     * 给当前周期增加 creditsTotal。
     * <ul>
     *   <li>无 quota 行：创建（creditsTotal=30+credits, creditsUsed=0）。</li>
     *   <li>已存在：creditsTotal += credits，不修改 creditsUsed。</li>
     * </ul>
     */
    public AdminQuotaDto grantCredits(Long userId, Integer credits) {
        User user = loadUserOrThrow(userId);
        Period period = currentPeriod();
        AiUsageQuota quota = findCurrentQuota(userId, period);
        if (quota == null) {
            quota = safeInsert(newQuota(userId, period, DEFAULT_WEEKLY_CREDITS, 0), userId, period);
        }
        int total = (quota.getCreditsTotal() == null ? 0 : quota.getCreditsTotal()) + credits;
        quota.setCreditsTotal(total);
        quotaMapper.updateById(quota);
        log.info("管理员补充额度：userId={}, credits=+{}, creditsTotal={}", userId, credits, total);
        return buildDto(user, findCurrentQuota(userId, period), period);
    }

    /**
     * 重置当前周期 creditsUsed=0。
     * <ul>
     *   <li>无 quota 行：创建默认 quota 行（creditsTotal=30, creditsUsed=0）。</li>
     *   <li>已存在：creditsUsed=0。</li>
     * </ul>
     */
    public AdminQuotaDto resetUsed(Long userId) {
        User user = loadUserOrThrow(userId);
        Period period = currentPeriod();
        AiUsageQuota quota = findCurrentQuota(userId, period);
        if (quota == null) {
            safeInsert(newQuota(userId, period, DEFAULT_WEEKLY_CREDITS, 0), userId, period);
            log.info("管理员重置已用额度（新建quota行）：userId={}", userId);
        } else {
            quota.setCreditsUsed(0);
            quotaMapper.updateById(quota);
            log.info("管理员重置已用额度：userId={}", userId);
        }
        return buildDto(user, findCurrentQuota(userId, period), period);
    }

    // ─── 内部 helper ────────────────────────────────────────────────────────────

    /** 当前自然周：周一 00:00:00 到下周一 00:00:00（与 AiUsageGuard 一致） */
    private Period currentPeriod() {
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDateTime start = monday.atStartOfDay();
        LocalDateTime end = start.plusWeeks(1);
        return new Period(start, end);
    }

    private record Period(LocalDateTime start, LocalDateTime end) {}

    /** 查询用户当前周期 quota 行，无则返回 null（不创建） */
    private AiUsageQuota findCurrentQuota(Long userId, Period period) {
        return quotaMapper.selectOne(new LambdaQueryWrapper<AiUsageQuota>()
                .eq(AiUsageQuota::getUserId, userId)
                .eq(AiUsageQuota::getPeriodStart, period.start()));
    }

    private AiUsageQuota newQuota(Long userId, Period period, int creditsTotal, int creditsUsed) {
        AiUsageQuota q = new AiUsageQuota();
        q.setUserId(userId);
        q.setPeriodStart(period.start());
        q.setPeriodEnd(period.end());
        q.setCreditsTotal(creditsTotal);
        q.setCreditsUsed(creditsUsed);
        return q;
    }

    /**
     * 插入 quota 行，处理 (user_id, period_start) 唯一约束并发冲突：
     * 插入失败时重新查询已存在的行返回。
     */
    private AiUsageQuota safeInsert(AiUsageQuota q, Long userId, Period period) {
        try {
            quotaMapper.insert(q);
            return q;
        } catch (Exception dup) {
            AiUsageQuota existing = findCurrentQuota(userId, period);
            if (existing != null) {
                return existing;
            }
            throw dup;
        }
    }

    private User loadUserOrThrow(Long userId) {
        if (userId == null) {
            throw new RuntimeException("用户 ID 不能为空");
        }
        User u = userMapper.selectUserIncludingDeleted(userId);
        if (u == null) {
            throw new RuntimeException("用户不存在：id=" + userId);
        }
        return u;
    }

    private AdminQuotaDto buildDto(User user, AiUsageQuota quota, Period period) {
        AdminQuotaDto dto = new AdminQuotaDto();
        dto.setUserId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setDeleted(user.getDeleted());
        dto.setPeriodStart(period.start());
        dto.setPeriodEnd(period.end());
        if (quota != null) {
            dto.setCreditsTotal(quota.getCreditsTotal());
            dto.setCreditsUsed(quota.getCreditsUsed());
            int total = quota.getCreditsTotal() == null ? 0 : quota.getCreditsTotal();
            int used = quota.getCreditsUsed() == null ? 0 : quota.getCreditsUsed();
            dto.setCreditsRemaining(Math.max(0, total - used));
            dto.setQuotaRowExists(true);
        } else {
            dto.setCreditsTotal(DEFAULT_WEEKLY_CREDITS);
            dto.setCreditsUsed(0);
            dto.setCreditsRemaining(DEFAULT_WEEKLY_CREDITS);
            dto.setQuotaRowExists(false);
        }
        return dto;
    }

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

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
