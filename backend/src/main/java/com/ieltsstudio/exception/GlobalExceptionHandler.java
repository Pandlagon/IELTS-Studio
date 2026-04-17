package com.ieltsstudio.exception;

import com.ieltsstudio.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * <p>统一捕获 Controller 层抛出的异常，转换为标准 JSON 响应，
 * 避免在每个接口中重复编写 try-catch 逻辑。
 *
 * <p>异常处理优先级（从高到低）：
 * <ol>
 *   <li>{@link MethodArgumentNotValidException} — 请求参数校验失败（400）</li>
 *   <li>{@link RuntimeException}               — 业务逻辑异常（400）</li>
 *   <li>{@link Exception}                      — 未预期的系统异常（500）</li>
 * </ol>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理请求参数校验失败（@Valid 注解触发）
     * <p>将所有字段的校验错误信息拼接后统一返回。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return Result.error(400, msg);
    }

    /**
     * 处理业务逻辑异常（RuntimeException）
     * <p>如用户名重复、密码错误、资源不存在等可预知的业务错误。
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleRuntime(RuntimeException ex) {
        log.warn("业务异常: {}", ex.getMessage());
        return Result.error(400, ex.getMessage());
    }

    /**
     * 处理所有未预期的系统异常
     * <p>日志记录完整堆栈，对外只返回通用错误提示（不暴露系统内部细节）。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleGeneral(Exception ex) {
        log.error("系统异常", ex);
        return Result.error(500, "服务器内部错误，请稍后重试");
    }
}
