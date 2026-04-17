package com.ieltsstudio.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * JWT 认证失败处理器
 *
 * <p>当请求访问受保护接口但未携带有效 Token（未登录或 Token 过期）时触发。
 * 直接返回 JSON 格式的 401 错误，而非默认的 HTML 页面或 302 重定向，
 * 以符合前后端分离架构下前端对响应格式的预期。
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 未认证请求的统一处理：返回 401 JSON 响应
     */
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
            objectMapper.writeValueAsString(Map.of(
                "code", 401,
                "message", "未登录或 Token 已过期，请重新登录"
            ))
        );
    }
}
