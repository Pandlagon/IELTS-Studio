package com.ieltsstudio.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ieltsstudio.ai.AiFeature;
import com.ieltsstudio.ai.AiKeyMode;
import com.ieltsstudio.entity.AiUsageQuota;
import com.ieltsstudio.entity.AiUsageRecord;
import com.ieltsstudio.mapper.AiUsageQuotaMapper;
import com.ieltsstudio.mapper.AiUsageRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 用量守卫：负责 credits 额度、流水记录与基础限流。
 *
 * <p>Phase 6A 落地实现：</p>
 * <ul>
 *   <li><b>BUILTIN 模式</b>：每用户每周 30 credits，采用「调用前预扣 + 失败回滚」模型，
 *       保证高并发下不超卖。额度不足写 REJECTED 流水并拒绝调用。</li>
 *   <li><b>USER 模式</b>：不消耗站点 credits，每用户每 feature 每分钟 20 次 rate limit。
 *       Phase 6B-2C 起优先使用 Redis 分布式限流（{@link AiRedisRateLimiter}），
 *       Redis 不可用时降级到单机内存限流；超限写 REJECTED 流水并拒绝调用。</li>
 *   <li>所有调用结果均写 {@code ai_usage_records} 流水：SUCCESS / FAILED / REJECTED。</li>
 *   <li>errorMessage 必须脱敏，禁止记录 API Key / Authorization / provider 原始 body。</li>
 * </ul>
 *
 * <p>方法签名：Phase 6B-2A 起新增 provider-aware overload，旧三参数方法保留兼容，
 * 内部委托到新方法并传 {@code provider=null}。</p>
 * <ul>
 *   <li>{@link #checkBeforeCall(Long, AiFeature, AiKeyMode)} —— 旧签名，provider=null。</li>
 *   <li>{@link #markSuccess(Long, AiFeature, AiKeyMode)} —— 旧签名，provider=null。</li>
 *   <li>{@link #markFailure(Long, AiFeature, AiKeyMode, Exception)} —— 旧签名，provider=null。</li>
 *   <li>{@link #checkBeforeCall(Long, AiFeature, AiKeyMode, String)} —— provider-aware：BUILTIN 预扣 credits，USER 计数限流。</li>
 *   <li>{@link #markSuccess(Long, AiFeature, AiKeyMode, String)} —— provider-aware：写 SUCCESS 流水（BUILTIN 已预扣，此处不再扣费）。</li>
 *   <li>{@link #markFailure(Long, AiFeature, AiKeyMode, String, Exception)} —— provider-aware：BUILTIN 回滚 credits 并写 FAILED 流水。</li>
 * </ul>
 *
 * <p>扣费模型说明：见 {@code docs/security-and-quota-plan.md} §6「调用前预扣 + 失败回滚」。</p>
 */
@Slf4j
@Component
public class AiUsageGuard {

    /** 每用户每周默认发放 credits，后续可配置化 */
    private static final int DEFAULT_WEEKLY_CREDITS = 100;

    /** USER 模式基础限流：每用户每 feature 每分钟调用上限 */
    private static final int USER_RATE_LIMIT_PER_MINUTE = 20;

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_REJECTED = "REJECTED";

    private final AiUsageQuotaMapper quotaMapper;
    private final AiUsageRecordMapper recordMapper;

    /** Phase 6B-2C 起 USER 模式优先走 Redis 分布式限流；为 null 时降级到单机内存限流 */
    private final AiRedisRateLimiter redisRateLimiter;

    // Redis unavailable fallback: single-node in-memory limiter. Multi-instance consistency depends on Redis.
    private final ConcurrentHashMap<String, RateWindow> rateLimitMap = new ConcurrentHashMap<>();

    /**
     * 构造函数注入 {@link ObjectProvider}，使 {@link AiRedisRateLimiter} 成为可选依赖。
     *
     * <p>当 {@code app.redis.enabled=false} 时 {@link AiRedisRateLimiter} bean 不存在，
     * {@code getIfAvailable()} 返回 null，{@link #checkUserRateLimit} 自动降级到内存限流。</p>
     */
    public AiUsageGuard(AiUsageQuotaMapper quotaMapper,
                        AiUsageRecordMapper recordMapper,
                        ObjectProvider<AiRedisRateLimiter> redisRateLimiterProvider) {
        this.quotaMapper = quotaMapper;
        this.recordMapper = recordMapper;
        this.redisRateLimiter = redisRateLimiterProvider.getIfAvailable();
    }

    /**
     * AI 调用前守卫（旧签名，provider=null）。
     *
     * <p>保留兼容；新调用点应使用 {@link #checkBeforeCall(Long, AiFeature, AiKeyMode, String)}。</p>
     */
    public void checkBeforeCall(Long userId, AiFeature feature, AiKeyMode keyMode) {
        checkBeforeCall(userId, feature, keyMode, null);
    }

    /**
     * AI 调用前守卫（provider-aware）。
     *
     * <ul>
     *   <li>BUILTIN：原子预扣 credits，不足则抛业务异常并写 REJECTED 流水（provider 一并写入）。</li>
     *   <li>USER：内存 rate limit 计数，超限则抛业务异常并写 REJECTED 流水（provider 一并写入）。</li>
     * </ul>
     *
     * @param provider provider 枚举名（如 {@code "DEEPSEEK"} / {@code "QWEN"} / {@code "MIMO"} /
     *                 {@code "OPENAI_COMPATIBLE"}），可为 null
     */
    public void checkBeforeCall(Long userId, AiFeature feature, AiKeyMode keyMode, String provider) {
        validate(userId, feature, keyMode);
        if (keyMode == AiKeyMode.BUILTIN) {
            checkBuiltin(userId, feature, provider);
        } else {
            checkUserRateLimit(userId, feature, provider);
        }
    }

    /**
     * AI 调用成功后回调（旧签名，provider=null）。
     *
     * <p>保留兼容；新调用点应使用 {@link #markSuccess(Long, AiFeature, AiKeyMode, String)}。</p>
     */
    public void markSuccess(Long userId, AiFeature feature, AiKeyMode keyMode) {
        markSuccess(userId, feature, keyMode, null);
    }

    /**
     * AI 调用成功后回调（provider-aware）。
     *
     * <ul>
     *   <li>BUILTIN：credits 已在 {@link #checkBeforeCall} 预扣，此处仅写 SUCCESS 流水，cost = feature cost。</li>
     *   <li>USER：不消耗站点 credits，写 SUCCESS 流水，cost = 0。</li>
     * </ul>
     */
    public void markSuccess(Long userId, AiFeature feature, AiKeyMode keyMode, String provider) {
        validate(userId, feature, keyMode);
        int cost = keyMode == AiKeyMode.BUILTIN ? feature.getBuiltinCost() : 0;
        insertRecord(userId, feature, keyMode, provider, STATUS_SUCCESS, cost, null);
        log.info("AiUsageGuard.markSuccess user={} feature={} keyMode={} provider={} cost={}",
                userId, feature, keyMode, provider, cost);
    }

    /**
     * AI 调用失败后回调（旧签名，provider=null）。
     *
     * <p>保留兼容；新调用点应使用 {@link #markFailure(Long, AiFeature, AiKeyMode, String, Exception)}。</p>
     */
    public void markFailure(Long userId, AiFeature feature, AiKeyMode keyMode, Exception ex) {
        markFailure(userId, feature, keyMode, null, ex);
    }

    /**
     * AI 调用失败后回调（provider-aware）。
     *
     * <ul>
     *   <li>BUILTIN：回滚预扣的 credits，写 FAILED 流水，cost = 0（失败不消耗 credits）。</li>
     *   <li>USER：写 FAILED 流水，cost = 0，不改 quota。</li>
     * </ul>
     *
     * <p>异常 message 脱敏后写入流水 errorMessage 字段。provider 不参与 sanitize，不会被误删。</p>
     */
    public void markFailure(Long userId, AiFeature feature, AiKeyMode keyMode, String provider, Exception ex) {
        validate(userId, feature, keyMode);
        String summary = summarize(ex);
        if (keyMode == AiKeyMode.BUILTIN) {
            refundBuiltin(userId, feature);
        }
        insertRecord(userId, feature, keyMode, provider, STATUS_FAILED, 0, summary);
        log.info("AiUsageGuard.markFailure user={} feature={} keyMode={} provider={} ex={}",
                userId, feature, keyMode, provider, summary);
    }

    // ─── BUILTIN 模式 ───────────────────────────────────────────────────────────

    /** 预扣 credits：原子更新，credits_total - credits_used >= cost 才成功 */
    private void checkBuiltin(Long userId, AiFeature feature, String provider) {
        AiUsageQuota quota = getOrCreateCurrentQuota(userId);
        int cost = feature.getBuiltinCost();
        int updated = quotaMapper.update(null,
                new LambdaUpdateWrapper<AiUsageQuota>()
                        .eq(AiUsageQuota::getId, quota.getId())
                        .apply("credits_total - credits_used >= {0}", cost)
                        .setSql("credits_used = credits_used + " + cost));
        if (updated == 0) {
            insertRecord(userId, feature, AiKeyMode.BUILTIN, provider, STATUS_REJECTED, 0, "INSUFFICIENT_CREDITS");
            throw new IllegalStateException("本周 AI 额度已用完，可切换自填 Key 模式");
        }
    }

    /** 回滚预扣的 credits：GREATEST 防止越界减为负数 */
    private void refundBuiltin(Long userId, AiFeature feature) {
        int cost = feature.getBuiltinCost();
        LocalDateTime periodStart = currentPeriod().start();
        quotaMapper.update(null,
                new LambdaUpdateWrapper<AiUsageQuota>()
                        .eq(AiUsageQuota::getUserId, userId)
                        .eq(AiUsageQuota::getPeriodStart, periodStart)
                        .setSql("credits_used = GREATEST(credits_used - " + cost + ", 0)"));
    }

    /**
     * 获取或创建当前周期的 quota 行。
     *
     * <p>并发插入会因 unique key (user_id, period_start) 冲突，捕获后重新查询。</p>
     */
    private AiUsageQuota getOrCreateCurrentQuota(Long userId) {
        Period period = currentPeriod();
        LambdaQueryWrapper<AiUsageQuota> q = new LambdaQueryWrapper<AiUsageQuota>()
                .eq(AiUsageQuota::getUserId, userId)
                .eq(AiUsageQuota::getPeriodStart, period.start());
        AiUsageQuota quota = quotaMapper.selectOne(q);
        if (quota != null) {
            return quota;
        }
        AiUsageQuota created = new AiUsageQuota();
        created.setUserId(userId);
        created.setPeriodStart(period.start());
        created.setPeriodEnd(period.end());
        created.setCreditsTotal(DEFAULT_WEEKLY_CREDITS);
        created.setCreditsUsed(0);
        try {
            quotaMapper.insert(created);
        } catch (Exception dup) {
            // 并发插入：unique key (user_id, period_start) 冲突，重新查询已插入的行
            AiUsageQuota existing = quotaMapper.selectOne(q);
            if (existing != null) {
                return existing;
            }
            throw dup;
        }
        // insert 成功。部分 ORM/驱动不会把自增 ID 回填到 entity，
        // 此时 created.getId() 为 null，需要重新 select 当前周期 quota 再用于后续预扣。
        if (created.getId() != null) {
            return created;
        }
        AiUsageQuota reloaded = quotaMapper.selectOne(q);
        if (reloaded != null) {
            return reloaded;
        }
        throw new IllegalStateException("AI quota row was created but cannot be reloaded");
    }

    // ─── USER 模式限流 ─────────────────────────────────────────────────────────
    // Phase 6B-2C：优先使用 Redis 分布式限流（多实例共享计数）；
    // Redis 不可用或抛异常时降级到单机内存限流，保证 AI 功能可用。

    private void checkUserRateLimit(Long userId, AiFeature feature, String provider) {
        boolean allowed;
        if (redisRateLimiter != null) {
            try {
                allowed = redisRateLimiter.isAllowed(userId, feature, USER_RATE_LIMIT_PER_MINUTE);
            } catch (Exception ex) {
                log.warn("Redis rate limiter unavailable, falling back to in-memory limiter: {}",
                        ex.getClass().getSimpleName());
                allowed = checkInMemoryRateLimit(userId, feature);
            }
        } else {
            allowed = checkInMemoryRateLimit(userId, feature);
        }

        if (!allowed) {
            insertRecord(userId, feature, AiKeyMode.USER, provider, STATUS_REJECTED, 0, "RATE_LIMITED");
            throw new IllegalStateException("请求过于频繁，请稍后再试");
        }
    }

    /**
     * 单机内存限流（Redis 降级路径）。
     *
     * <p>返回 {@code true} 表示放行；{@code false} 表示触发限流。
     * 不直接写流水、不抛异常，由 {@link #checkUserRateLimit} 统一处理。</p>
     */
    private boolean checkInMemoryRateLimit(Long userId, AiFeature feature) {
        String key = userId + ":" + feature.name();
        long minute = System.currentTimeMillis() / 60_000L;
        RateWindow window = rateLimitMap.compute(key, (k, existing) ->
                (existing == null || existing.minute != minute) ? new RateWindow(minute) : existing);
        int count = window.count.incrementAndGet();
        return count <= USER_RATE_LIMIT_PER_MINUTE;
    }

    // ─── 公共辅助 ───────────────────────────────────────────────────────────────

    private void insertRecord(Long userId, AiFeature feature, AiKeyMode keyMode, String provider,
                              String status, int cost, String errorMessage) {
        AiUsageRecord record = new AiUsageRecord();
        record.setUserId(userId);
        record.setTaskType(feature.getTaskType().name());
        record.setFeature(feature.name());
        record.setCost(cost);
        record.setKeyMode(keyMode.name());
        // provider 仅写枚举名或 null，不包含 baseUrl / model / API Key
        record.setProvider(provider);
        record.setStatus(status);
        record.setErrorMessage(sanitize(errorMessage));
        recordMapper.insert(record);
    }

    /** 异常摘要：类名 + 简短 message，后续由 {@link #sanitize} 脱敏 */
    private String summarize(Exception ex) {
        if (ex == null) return null;
        String msg = ex.getMessage();
        return ex.getClass().getSimpleName() + ": " + (msg == null ? "" : msg);
    }

    /** 统一基础参数校验，三个入口方法共用 */
    private void validate(Long userId, AiFeature feature, AiKeyMode keyMode) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (feature == null) {
            throw new IllegalArgumentException("AiFeature must not be null");
        }
        if (keyMode == null) {
            throw new IllegalArgumentException("AiKeyMode must not be null");
        }
    }

    /**
     * 脱敏：去除 API Key / Authorization / Bearer，并截断到 500 字符
     * （与 {@code ai_usage_records.error_message} 的 VARCHAR(500) 对齐）。
     */
    private String sanitize(String msg) {
        if (msg == null) return null;
        String s = msg;
        s = s.replaceAll("(?i)Authorization\\s*:\\s*Bearer\\s+\\S+", "Authorization: Bearer [REDACTED]");
        s = s.replaceAll("(?i)Bearer\\s+\\S+", "Bearer [REDACTED]");
        s = s.replaceAll("sk-[A-Za-z0-9_\\-]{6,}", "sk-[REDACTED]");
        if (s.length() > 500) s = s.substring(0, 500);
        return s;
    }

    /** 当前自然周：周一 00:00:00 到下周一 00:00:00（基于服务器默认时区） */
    private Period currentPeriod() {
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDateTime start = monday.atStartOfDay();
        LocalDateTime end = start.plusWeeks(1);
        return new Period(start, end);
    }

    /** 周期区间 */
    private record Period(LocalDateTime start, LocalDateTime end) {}

    /** USER 限流滑动窗口（按分钟） */
    private static class RateWindow {
        final long minute;
        final AtomicInteger count = new AtomicInteger(0);

        RateWindow(long minute) {
            this.minute = minute;
        }
    }
}
