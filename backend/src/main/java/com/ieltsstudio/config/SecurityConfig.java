package com.ieltsstudio.config;

import com.ieltsstudio.security.JwtAuthenticationEntryPoint;
import com.ieltsstudio.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 安全配置
 *
 * <p>配置要点：
 * <ul>
 *   <li>无状态 Session（JWT 鉴权，不依赖 Cookie/Session）</li>
 *   <li>白名单路径：注册/登录、OPTIONS 预检、公开试卷、Actuator</li>
 *   <li>其余所有接口均需携带有效 JWT Token</li>
 *   <li>CORS 仅允许本地开发域名（生产环境请修改为实际域名）</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    /**
     * 密码编码器：使用 BCrypt 哈希算法
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 安全过滤链配置
     *
     * <p>执行顺序：CORS → CSRF 禁用 → 会话策略 → 权限规则 → JWT 过滤器
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（前后端分离 + JWT 鉴权，不需要 CSRF 防护）
            .csrf(AbstractHttpConfigurer::disable)
            // 启用自定义 CORS 配置
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // 无状态会话，每次请求都通过 JWT 重新鉴权
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 接口权限规则
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()           // 放行所有 OPTIONS 预检请求
                .requestMatchers("/auth/**").permitAll()                           // 注册/登录不需要认证
                .requestMatchers(HttpMethod.POST, "/exams/grade-writing").permitAll() // 写作 AI 评分（允许未登录调用）
                .requestMatchers(HttpMethod.GET, "/exams/public/**").permitAll()  // 公开试卷接口
                .requestMatchers("/actuator/**").permitAll()                       // 健康检查接口
                .anyRequest().authenticated()                                      // 其余接口均需登录
            )
            // 未登录或 Token 过期时返回 JSON 错误（而非 302 重定向）
            .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint))
            // 在用户名密码过滤器之前插入 JWT 过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS 跨域配置
     *
     * <p>仅允许本地开发地址（localhost / 127.0.0.1 / IPv6 回环）。
     * <strong>生产环境部署时，请将此处替换为你的实际前端域名。</strong>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 允许的前端来源（本地开发任意端口）
        config.setAllowedOriginPatterns(List.of(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "http://[::1]:*"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L); // 预检请求缓存 1 小时

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
