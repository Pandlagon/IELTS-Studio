package com.ieltsstudio.common;

import lombok.Data;

/**
 * 统一 API 响应封装类
 *
 * <p>所有接口返回值统一使用本类包装，格式如下：
 * <pre>
 * {
 *   "code": 200,
 *   "message": "success",
 *   "data": { ... }
 * }
 * </pre>
 *
 * @param <T> 业务数据类型
 */
@Data
public class Result<T> {

    /** HTTP 状态码 / 业务状态码 */
    private int code;

    /** 响应消息描述 */
    private String message;

    /** 实际业务数据 */
    private T data;

    // ─── 成功响应 ──────────────────────────────────────────────────────────────

    /** 返回带数据的成功响应（200） */
    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.code = 200;
        r.message = "success";
        r.data = data;
        return r;
    }

    /** 返回无数据的成功响应（200） */
    public static <T> Result<T> success() {
        return success(null);
    }

    // ─── 错误响应 ──────────────────────────────────────────────────────────────

    /** 返回指定状态码的错误响应 */
    public static <T> Result<T> error(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }

    /** 返回 500 服务器内部错误响应 */
    public static <T> Result<T> error(String message) {
        return error(500, message);
    }

    /** 返回 401 未认证响应 */
    public static <T> Result<T> unauthorized(String message) {
        return error(401, message);
    }

    /** 返回 403 权限不足响应 */
    public static <T> Result<T> forbidden(String message) {
        return error(403, message);
    }

    /** 返回 404 资源不存在响应 */
    public static <T> Result<T> notFound(String message) {
        return error(404, message);
    }
}
