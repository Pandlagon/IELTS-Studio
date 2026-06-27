package com.ieltsstudio.ai.service;

import com.ieltsstudio.ai.AiFeature;
import com.ieltsstudio.ai.AiKeyMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI 用量守卫骨架。
 *
 * <p>本阶段只做守卫入口与基础参数校验，<b>不</b>真正写库 / 扣费 / 限流。</p>
 *
 * <p>方法签名已为后续阶段预留：</p>
 * <ul>
 *   <li>{@link #checkBeforeCall(Long, AiFeature, AiKeyMode)} —— 调用前：BUILTIN 校验 credits 余量，USER 做 rate limit。</li>
 *   <li>{@link #markSuccess(Long, AiFeature, AiKeyMode)} —— 调用成功：BUILTIN 扣费，USER 计数。</li>
 *   <li>{@link #markFailure(Long, AiFeature, AiKeyMode, Exception)} —— 调用失败：记录失败流水（不扣费）。</li>
 * </ul>
 *
 * <p>当前实现仅输出 debug / info 日志，不抛业务异常。</p>
 *
 * <p>后续阶段落地建议见 {@code docs/security-and-quota-plan.md}：
 * credits 扣费在调用<b>成功后</b>扣（避免失败也扣费）；rate limit 可复用 {@code infra/RedisOps}。</p>
 */
@Slf4j
@Component
public class AiUsageGuard {

    /**
     * AI 调用前守卫。
     *
     * <p>后续阶段实现：</p>
     * <ul>
     *   <li>BUILTIN：校验当前周期 credits 余量是否 ≥ feature 对应 cost，不足则抛业务异常。</li>
     *   <li>USER：按用户 / 接口做 rate limit（建议 20 次/分钟）。</li>
     * </ul>
     *
     * <p>本阶段：仅做基础参数校验 + 日志。</p>
     */
    public void checkBeforeCall(Long userId, AiFeature feature, AiKeyMode keyMode) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (feature == null) {
            throw new IllegalArgumentException("AiFeature must not be null");
        }
        if (keyMode == null) {
            throw new IllegalArgumentException("AiKeyMode must not be null");
        }
        // TODO(后续阶段): BUILTIN 模式查 ai_usage_quota 校验 credits；USER 模式做 rate limit
        log.debug("AiUsageGuard.checkBeforeCall user={} feature={} keyMode={} (no-op)", userId, feature, keyMode);
    }

    /**
     * AI 调用成功后回调。
     *
     * <p>后续阶段实现：</p>
     * <ul>
     *   <li>BUILTIN：按 feature cost 扣减 credits，写 ai_usage_records 流水（status=SUCCESS）。</li>
     *   <li>USER：累加计数 / 写流水（不扣站点 credits）。</li>
     * </ul>
     *
     * <p>本阶段：仅记日志。</p>
     */
    public void markSuccess(Long userId, AiFeature feature, AiKeyMode keyMode) {
        // TODO(后续阶段): 扣减 credits 并写流水
        log.info("AiUsageGuard.markSuccess user={} feature={} keyMode={} (no-op)", userId, feature, keyMode);
    }

    /**
     * AI 调用失败后回调。
     *
     * <p>后续阶段实现：写 ai_usage_records 流水（status=FAILED），<b>不</b>扣费。
     * 异常信息脱敏后记录（不输出 provider 原始 body / key）。</p>
     *
     * <p>本阶段：仅记日志，异常 message 取其类名 + 简短信息，避免泄露。</p>
     */
    public void markFailure(Long userId, AiFeature feature, AiKeyMode keyMode, Exception ex) {
        String exSummary = ex == null ? "null"
                : ex.getClass().getSimpleName() + ": " + sanitize(ex.getMessage());
        // TODO(后续阶段): 写失败流水（不扣费）
        log.info("AiUsageGuard.markFailure user={} feature={} keyMode={} ex={} (no-op)",
                userId, feature, keyMode, exSummary);
    }

    /** 简单脱敏：截断过长异常 message，避免把 provider 原始 body 写进日志 */
    private String sanitize(String msg) {
        if (msg == null) return "";
        return msg.length() > 200 ? msg.substring(0, 200) + "..." : msg;
    }
}
