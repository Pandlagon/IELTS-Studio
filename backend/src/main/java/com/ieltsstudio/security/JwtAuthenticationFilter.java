package com.ieltsstudio.security;

import com.ieltsstudio.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器
 *
 * <p>每次请求只执行一次（继承自 {@link OncePerRequestFilter}）。
 * 处理流程：
 * <ol>
 *   <li>从请求头 {@code Authorization: Bearer <token>} 提取 JWT</li>
 *   <li>校验 Token 有效性（签名、过期时间）</li>
 *   <li>解析用户信息，构建认证对象并写入 SecurityContext</li>
 *   <li>无论 Token 是否存在，均放行请求（由后续权限规则决定是否拦截）</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 从请求头提取 Token
        String token = extractToken(request);

        // Token 存在且有效时，解析用户信息并设置认证上下文
        if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
            Long userId = jwtUtil.getUserId(token);
            String username = jwtUtil.getUsername(token);
            String role = jwtUtil.getRole(token);

            // 构建认证对象（credentials 为 null，因为已通过 JWT 验证）
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            new AuthUser(userId, username, role),
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // 将认证信息存入当前请求的 SecurityContext
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 放行，继续执行后续过滤器
        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头 Authorization 中提取 Bearer Token
     *
     * @param request HTTP 请求
     * @return Token 字符串；若不存在或格式不符则返回 null
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
