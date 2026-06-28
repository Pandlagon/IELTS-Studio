package com.ieltsstudio.service;

import com.ieltsstudio.dto.admin.AdminOperationLogDto;
import com.ieltsstudio.dto.admin.AdminOperationLogPageDto;
import com.ieltsstudio.entity.AdminOperationAction;
import com.ieltsstudio.entity.AdminOperationLog;
import com.ieltsstudio.entity.AdminOperationStatus;
import com.ieltsstudio.mapper.AdminOperationLogMapper;
import com.ieltsstudio.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 管理端操作审计日志服务（Phase 8D）。
 *
 * <p>职责：
 * <ul>
 *   <li>写入审计日志（{@link #recordSuccess} / {@link #recordFailure}）。</li>
 *   <li>查询审计日志（{@link #listLogs}），支持 actor/target/action/resource/status/time 筛选。</li>
 *   <li>DTO 映射。</li>
 *   <li>{@link #summary} 脱敏（禁止 password / API Key / token / Authorization / Bearer / provider body）。</li>
 *   <li>请求 IP / User-Agent 读取。</li>
 * </ul>
 *
 * <p><b>安全：</b>
 * <ul>
 *   <li>{@link #sanitizeSummary(String)} 对敏感关键字做脱敏 + 截断到 1000 字符。</li>
 *   <li>重置密码场景只记录 {@code "reset password for userId=xxx"}，绝不记录密码内容。</li>
 *   <li>审计日志写入失败不影响主业务流程（catch + warn log）。</li>
 *   <li>审计日志不允许删除（无 delete 方法）。</li>
 * </ul>
 *
 * <p><b>Phase 8D 范围：</b>至少记录成功写操作。失败审计（{@link #recordFailure}）
 * 已实现但本阶段不强制在所有失败路径调用；如接入成本高可只记成功写操作。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditLogService {

    /** summary 最大长度，与 {@code admin_operation_logs.summary} 列长度一致 */
    private static final int SUMMARY_MAX_LENGTH = 1000;

    /** ipAddress / userAgent 最大长度，与 DB 列长度一致 */
    private static final int IP_MAX_LENGTH = 80;
    private static final int UA_MAX_LENGTH = 300;

    /** 分页参数 clamp 上限 */
    private static final int MAX_PAGE_SIZE = 100;

    // ─── 脱敏 Patterns（Phase 8D-polish：key=value / Bearer / sk-* 整段脱敏）────────

    /**
     * key=value / key:value 形式的敏感键值对。
     * 匹配 password / newPassword / new_password / passwd / pwd /
     * apiKey / api_key / key / access_token / refresh_token / token / jwt /
     * encryptedKey / encrypted_key / maskedKey / masked_key。
     * 替换为 {@code keyName***=***}（保留 key 名 + 分隔符，掩盖 value）。
     */
    private static final Pattern KEY_VALUE_SECRET_PATTERN = Pattern.compile(
            "(?i)\\b(password|newPassword|new_password|passwd|pwd|apiKey|api_key|" +
            "access_token|refresh_token|token|jwt|" +
            "encryptedKey|encrypted_key|maskedKey|masked_key|key)" +
            "\\b(\\s*[=:]\\s*)[^,;\\s]+");

    /**
     * Authorization: xxx / Authorization=xxx（含 Bearer 场景）。
     * 替换为 {@code Authorization***=***}（保留 key 名 + 分隔符，掩盖 Bearer + token body）。
     */
    private static final Pattern AUTHORIZATION_PATTERN = Pattern.compile(
            "(?i)(\\bAuthorization\\b\\s*[=:]\\s*)(Bearer\\s+)?[^,;\\s]+");

    /**
     * Bearer xxx（独立出现，不在 Authorization 后）。
     * 替换为 {@code Bearer ***}（保留 Bearer 关键字，掩盖 token body）。
     */
    private static final Pattern BEARER_PATTERN = Pattern.compile(
            "(?i)(\\bBearer\\b)\\s+[^,;\\s]+");

    /**
     * sk-xxx / sk_xxx provider key（DeepSeek/OpenAI 风格）。
     * 整段替换为 {@code sk-***}。
     */
    private static final Pattern SK_KEY_PATTERN = Pattern.compile(
            "(?i)\\bsk[-_][A-Za-z0-9._-]+");

    private final AdminOperationLogMapper auditLogMapper;

    // ─── 写入 ───────────────────────────────────────────────────────────────────

    /**
     * 记录一条成功写操作审计日志。
     *
     * <p>调用方应在主业务成功返回后调用本方法。{@code request} 用于读取 IP / User-Agent；
     * 可为 null（例如异步场景），此时 IP / UA 字段为 null。</p>
     *
     * <p>写入失败不会抛异常（仅 warn log），避免影响主业务。</p>
     *
     * @param actor        操作者（从 {@code @AuthenticationPrincipal AuthUser} 取）
     * @param action       操作枚举
     * @param resourceType 资源类型：USER / QUOTA / PERMISSION
     * @param resourceId   资源 ID，可为 null
     * @param targetUserId 被操作用户 ID，可为 null
     * @param summary      摘要（会被脱敏 + 截断），禁止传密码/key/token
     * @param request      HTTP 请求，可为 null
     */
    public void recordSuccess(
            AuthUser actor,
            AdminOperationAction action,
            String resourceType,
            Long resourceId,
            Long targetUserId,
            String summary,
            HttpServletRequest request) {
        record(actor, action, resourceType, resourceId, targetUserId, summary, request,
                AdminOperationStatus.SUCCESS);
    }

    /**
     * 记录一条失败写操作审计日志。
     *
     * <p>调用方应在 catch 块中调用本方法。{@code summary} 可包含异常类名 / 简短消息，
     * 但禁止包含密码/key/token/provider body。</p>
     *
     * <p>写入失败不会抛异常（仅 warn log），避免影响主业务的异常传播。</p>
     */
    public void recordFailure(
            AuthUser actor,
            AdminOperationAction action,
            String resourceType,
            Long resourceId,
            Long targetUserId,
            String summary,
            HttpServletRequest request) {
        record(actor, action, resourceType, resourceId, targetUserId, summary, request,
                AdminOperationStatus.FAILED);
    }

    private void record(
            AuthUser actor,
            AdminOperationAction action,
            String resourceType,
            Long resourceId,
            Long targetUserId,
            String summary,
            HttpServletRequest request,
            AdminOperationStatus status) {
        if (actor == null || action == null) {
            log.warn("审计日志参数非法：actor={}, action={}", actor, action);
            return;
        }
        try {
            AdminOperationLog entity = new AdminOperationLog();
            entity.setActorUserId(actor.getId());
            entity.setActorUsername(actor.getUsername());
            entity.setAction(action.name());
            entity.setResourceType(resourceType);
            entity.setResourceId(resourceId);
            entity.setTargetUserId(targetUserId);
            entity.setStatus(status.name());
            entity.setSummary(sanitizeSummary(summary));
            if (request != null) {
                entity.setIpAddress(truncate(extractClientIp(request), IP_MAX_LENGTH));
                entity.setUserAgent(truncate(request.getHeader("User-Agent"), UA_MAX_LENGTH));
            }
            auditLogMapper.insert(entity);
        } catch (Exception e) {
            // 审计日志写入失败不影响主业务流程
            log.warn("审计日志写入失败：actor={}, action={}, status={}, err={}",
                    actor.getId(), action, status, e.getMessage());
        }
    }

    // ─── 查询 ───────────────────────────────────────────────────────────────────

    /**
     * 分页查询审计日志。page / pageSize 自动 clamp（page≥1，pageSize∈[1,100]）。
     *
     * <p>所有筛选参数可为 null（不过滤）。结果按 {@code created_at DESC, id DESC} 排序。</p>
     */
    public AdminOperationLogPageDto listLogs(
            int page,
            int pageSize,
            Long actorUserId,
            Long targetUserId,
            String action,
            String resourceType,
            String status,
            LocalDateTime dateFrom,
            LocalDateTime dateTo) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
        int offset = (safePage - 1) * safePageSize;

        Long total = auditLogMapper.countAdminOperationLogs(
                actorUserId, targetUserId, action, resourceType, status, dateFrom, dateTo);
        List<AdminOperationLog> logs = auditLogMapper.selectAdminOperationLogs(
                offset, safePageSize, actorUserId, targetUserId, action, resourceType, status, dateFrom, dateTo);

        AdminOperationLogPageDto dto = new AdminOperationLogPageDto();
        dto.setRecords(logs.stream().map(this::toDto).toList());
        dto.setTotal(total == null ? 0L : total);
        dto.setPage((long) safePage);
        dto.setPageSize((long) safePageSize);
        dto.setPages(safePageSize == 0 ? 0L : (long) Math.ceil((double) dto.getTotal() / safePageSize));
        return dto;
    }

    // ─── 内部 helper ────────────────────────────────────────────────────────────

    /**
     * summary 脱敏 + 截断（Phase 8D-polish 增强）。
     *
     * <p>规则（按顺序依次替换，保留 key 名与分隔符，掩盖敏感值）：
     * <ol>
     *   <li>{@code Authorization: Bearer xxx} / {@code Authorization=xxx} → {@code Authorization: ***}。</li>
     *   <li>{@code keyName=value} / {@code keyName: value} 形式（password / newPassword /
     *       new_password / passwd / pwd / apiKey / api_key / key / access_token /
     *       refresh_token / token / jwt / encryptedKey / encrypted_key / maskedKey /
     *       masked_key）→ {@code keyName=***}。</li>
     *   <li>独立 {@code Bearer xxx} → {@code Bearer ***}。</li>
     *   <li>{@code sk-xxx} / {@code sk_xxx} provider key → {@code sk-***}。</li>
     *   <li>截断到 {@value #SUMMARY_MAX_LENGTH} 字符。</li>
     *   <li>null 返回 null。</li>
     * </ol>
     *
     * <p>注意：只脱敏 {@code key=value} / {@code key:value} 形式中的 value，
     * 不影响普通文本里出现的 key 名（如 "reset password for userId=42" 中的 password 不带 value，
     * 不会被脱敏）。</p>
     */
    String sanitizeSummary(String summary) {
        if (summary == null) {
            return null;
        }
        String sanitized = summary;
        // 顺序：先处理 Authorization（最具体），再 key=value，再独立 Bearer，最后 sk-* key
        sanitized = AUTHORIZATION_PATTERN.matcher(sanitized)
                .replaceAll(mr -> mr.group(1) + "***");
        sanitized = KEY_VALUE_SECRET_PATTERN.matcher(sanitized)
                .replaceAll(mr -> mr.group(1) + mr.group(2) + "***");
        sanitized = BEARER_PATTERN.matcher(sanitized)
                .replaceAll(mr -> mr.group(1) + " ***");
        sanitized = SK_KEY_PATTERN.matcher(sanitized)
                .replaceAll(mr -> "sk-***");
        return truncate(sanitized, SUMMARY_MAX_LENGTH);
    }

    /** 截断到 maxLen 字符，避免 DB 列长度溢出 */
    private String truncate(String s, int maxLen) {
        if (s == null) {
            return null;
        }
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    /**
     * 提取客户端真实 IP。
     *
     * <p>优先读 {@code X-Forwarded-For} 第一个 IP（反向代理场景），
     * 其次 {@code X-Real-IP}，最后 {@code request.getRemoteAddr()}。</p>
     */
    private String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // X-Forwarded-For: client, proxy1, proxy2 — 取第一个
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) {
            return xri.trim();
        }
        return request.getRemoteAddr();
    }

    /** Entity → DTO（无敏感字段，summary 已在写入时脱敏） */
    private AdminOperationLogDto toDto(AdminOperationLog log) {
        AdminOperationLogDto dto = new AdminOperationLogDto();
        dto.setId(log.getId());
        dto.setActorUserId(log.getActorUserId());
        dto.setActorUsername(log.getActorUsername());
        dto.setAction(log.getAction());
        dto.setResourceType(log.getResourceType());
        dto.setResourceId(log.getResourceId());
        dto.setTargetUserId(log.getTargetUserId());
        dto.setStatus(log.getStatus());
        dto.setSummary(log.getSummary());
        dto.setIpAddress(log.getIpAddress());
        dto.setUserAgent(log.getUserAgent());
        dto.setCreatedAt(log.getCreatedAt());
        return dto;
    }
}
