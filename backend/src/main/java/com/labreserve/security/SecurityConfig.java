package com.labreserve.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置。
 *
 * <p><b>⚠️ 当前是迭代 0 的临时配置：全部放行。</b>
 * 这样做是为了让骨架先跑起来（健康检查、Knife4j 能访问）。
 *
 * <p>迭代 1 会改成：
 * <ul>
 *   <li>注册 {@code JwtAuthenticationFilter}（在 {@code UsernamePasswordAuthenticationFilter} 之前）</li>
 *   <li>白名单只留 {@code /api/auth/login}、{@code /api/auth/refresh}、{@code /api/system/health}、Knife4j 相关路径</li>
 *   <li>其余请求 {@code authenticated()}</li>
 *   <li>自定义 {@code AuthenticationEntryPoint}（401）与 {@code AccessDeniedHandler}（403），
 *       统一返回 {@code R} 格式</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** 迭代 0 阶段完全放行，迭代 1 收紧 */
    private static final String[] PERMIT_ALL = {
            "/api/**",
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-resources/**",
            "/favicon.ico",
            "/error"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 前后端分离 + JWT，不需要 CSRF 与 Session
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // TODO 迭代 1：替换为 JWT 过滤器 + 按权限收紧
                .authorizeHttpRequests(registry -> registry.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
