package com.ieltsstudio.ai.util;

import java.util.regex.Pattern;

/**
 * AI 日志与错误脱敏工具。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>{@link #maskApiKey(String)} —— 将 API Key 脱敏为 {@code sk-****3456} 形式，便于日志 / 前端展示 masked key。</li>
 *   <li>{@link #summarizeProviderError(String)} —— 截断 provider 返回的错误体，最多 300 字符，并屏蔽疑似 key / Bearer 片段。</li>
 *   <li>{@link #sanitize(String)} —— 通用脱敏：去掉 Authorization 头、屏蔽疑似 key 片段、截断过长内容。</li>
 * </ul>
 *
 * <p><b>安全要求：</b>所有方法对 {@code null} / 空白字符串安全返回，不抛 NPE；
 * 任何方法都不得原样输出完整 API Key。</p>
 */
public final class AiLogSanitizer {

    /** provider 错误体保留的最大长度（截断后追加 {@code ...}） */
    private static final int MAX_ERROR_BODY_LEN = 300;

    /** 通用 sanitize 保留的最大长度 */
    private static final int MAX_GENERIC_LEN = 500;

    /** 匹配疑似 API Key 片段：sk- 开头 / Bearer 后跟 token */
    private static final Pattern KEY_LIKE_PATTERN =
            Pattern.compile("(?i)(sk-[A-Za-z0-9]{4,})|(bearer\\s+[A-Za-z0-9._-]{4,})");

    /** 匹配 Authorization 头（整行），用于 sanitize 时移除 */
    private static final Pattern AUTH_HEADER_PATTERN =
            Pattern.compile("(?im)^\\s*authorization\\s*:.*$");

    private AiLogSanitizer() {
        // 工具类，禁止实例化
    }

    /**
     * 脱敏 API Key，保留首部前缀与尾部 4 位，中间用 {@code ****} 替代。
     *
     * <p>示例：</p>
     * <ul>
     *   <li>{@code maskApiKey("sk-abcdef123456")} → {@code sk-****3456}</li>
     *   <li>{@code maskApiKey("sk-ab")} → {@code ****}（过短时仅返回星号，避免泄露）</li>
     *   <li>{@code maskApiKey(null)} → {@code ""}</li>
     * </ul>
     *
     * @param apiKey 原始 API Key
     * @return 脱敏后的展示串；null / 空白返回空串
     */
    public static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        String trimmed = apiKey.trim();
        // 太短的 key 不保留任何明文片段，避免被反推
        if (trimmed.length() <= 8) {
            return "****";
        }
        // 保留前缀（如 sk-）与末尾 4 位
        int prefixLen = Math.min(6, trimmed.length() - 4);
        String prefix = trimmed.substring(0, prefixLen);
        String tail = trimmed.substring(trimmed.length() - 4);
        return prefix + "****" + tail;
    }

    /**
     * 摘要 provider 错误响应体：屏蔽疑似 key / Bearer 片段，并截断到 300 字符。
     *
     * <p>用于把 provider 原始错误写入日志前的脱敏处理，
     * <b>不得</b>把脱敏后的结果直接返回给前端（前端只看通用提示）。</p>
     *
     * @param body provider 返回的原始响应体
     * @return 脱敏 + 截断后的摘要；null / 空白返回空串
     */
    public static String summarizeProviderError(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String masked = maskKeyLike(body);
        if (masked.length() > MAX_ERROR_BODY_LEN) {
            masked = masked.substring(0, MAX_ERROR_BODY_LEN) + "...(truncated)";
        }
        return masked;
    }

    /**
     * 通用脱敏：移除 Authorization 头、屏蔽疑似 key 片段、截断过长内容。
     *
     * <p>适用于把任意输入（请求头、字符串、异常 message）写入日志前的处理。</p>
     *
     * @param input 原始输入
     * @return 脱敏后的字符串；null 返回空串
     */
    public static String sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        // 先移除 Authorization 头（整行）
        String s = AUTH_HEADER_PATTERN.matcher(input).replaceAll("[authorization]: ***");
        // 再屏蔽疑似 key 片段
        s = maskKeyLike(s);
        // 截断
        if (s.length() > MAX_GENERIC_LEN) {
            s = s.substring(0, MAX_GENERIC_LEN) + "...(truncated)";
        }
        return s;
    }

    /**
     * 屏蔽字符串中疑似 API Key / Bearer token 的片段。
     * 保留前 3 位 + {@code ****}，避免完整 token 进入日志。
     */
    private static String maskKeyLike(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return KEY_LIKE_PATTERN.matcher(input).replaceAll(match -> {
            String g = match.group();
            if (g.length() <= 6) {
                return "***";
            }
            return g.substring(0, 3) + "****";
        });
    }
}
