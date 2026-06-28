package com.ieltsstudio.ai.service;

import com.ieltsstudio.ai.AiFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * AI USER 模式 Redis 分布式限流器（Phase 6B-2C）。
 *
 * <p>固定窗口算法：每用户每 feature 每分钟一个 Redis key，{@code INCR} 计数，
 * 首次写入设置 TTL（70 秒，略大于 60 秒窗口，确保过期清理）。
 * 超过 {@code limitPerMinute} 则拒绝。</p>
 *
 * <p>多实例共享 Redis 计数状态；Redis 不可用时由 {@link AiUsageGuard}
 * 捕获异常并降级到单机内存限流，保证 AI 功能可用。</p>
 *
 * <p>Bean 创建条件：{@code app.redis.enabled=true}（与 {@code RedisConfig} 保持一致）。
 * 当 Redis 关闭时，{@link AiUsageGuard} 通过 {@link ObjectProvider} 拿不到本 bean，
 * 直接走内存限流。</p>
 *
 * <p>Key 设计：{@code ai:rate:user:{userId}:feature:{feature}:minute:{epochMinute}}，
 * 仅包含 userId / feature / epochMinute，<b>不包含</b> API Key、provider、baseUrl、model、prompt。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
@RequiredArgsConstructor
public class AiRedisRateLimiter {

    /** TTL 略大于 60 秒窗口，确保跨窗口前 key 仍能过期清理 */
    private static final Duration KEY_TTL = Duration.ofSeconds(70);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 检查指定用户在指定 feature 上的当前分钟窗口是否允许调用。
     *
     * @param userId          用户 ID
     * @param feature         AI 功能
     * @param limitPerMinute  每分钟允许调用次数上限
     * @return {@code true} 允许；{@code false} 触发限流
     * @throws IllegalStateException Redis INCR 返回 null（不应发生）
     * @throws RuntimeException      Redis 不可用或操作失败（由调用方捕获并降级）
     */
    public boolean isAllowed(Long userId, AiFeature feature, int limitPerMinute) {
        String key = buildKey(userId, feature);
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count == null) {
            throw new IllegalStateException("Redis INCR returned null for key: " + key);
        }
        if (count == 1L) {
            stringRedisTemplate.expire(key, KEY_TTL);
        }
        return count <= limitPerMinute;
    }

    /**
     * 构造 Redis key。
     *
     * <p>Key 仅包含 userId / feature / epochMinute，禁止拼接 API Key、provider、
     * baseUrl、model、prompt 等任何敏感或可变信息。</p>
     */
    private String buildKey(Long userId, AiFeature feature) {
        long epochMinute = System.currentTimeMillis() / 60_000L;
        return "ai:rate:user:" + userId + ":feature:" + feature.name() + ":minute:" + epochMinute;
    }
}
