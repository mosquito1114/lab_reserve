package com.labreserve.config;

import com.labreserve.security.JwtAuthenticationFilter;
import com.labreserve.security.RestAccessDeniedHandler;
import com.labreserve.security.RestAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置。
 *
 * <p>前后端分离 + JWT，因此：关闭 CSRF / Session / 表单登录，
 * 由 {@link JwtAuthenticationFilter} 从请求头还原登录态，
 * 其余请求一律要求已认证（{@code authenticated()}）。
 *
 * <p>功能级权限不在这里做（URL 维度太粗），而是由
 * {@code @RequirePerm} + {@link com.labreserve.security.PermissionAspect} 在方法上校验，
 * 权限本身是数据（{@code tb_role_permission}），管理员可在界面上勾选。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * 免登录路径。
     *
     * <p>只放「拿不到 token 也必须能访问」的接口：登录、刷新、健康检查、接口文档。
     * 注意 {@code /api/auth/logout}、{@code /api/auth/get_info} 需要登录，不在这里。
     */
    private static final String[] PERMIT_ALL = {
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/system/health",
            // R0 骨架阶段：RAG 健康检查免登录，便于前端/探针免认证拉取
            "/api/rag/health",
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-resources/**",
            "/favicon.ico",
            "/error"
    };

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @Autowired
    private RestAuthenticationEntryPoint authenticationEntryPoint;
    @Autowired
    private RestAccessDeniedHandler accessDeniedHandler;

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
                // 401 / 403 统一返回 Result JSON（默认实现会返回 HTML）
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(registry -> registry
                        // ---------- 异步派发放行（RAG SSE 必需的修正）----------
                        // Tomcat 在异步响应（SseEmitter / StreamingResponseBody）写完以后，
                        // 会做一次 ASYNC dispatch 重新走过滤器链。
                        // 但 SecurityContext 是 ThreadLocal，异步线程里是空的，
                        // JwtAuthenticationFilter 的 shouldNotFilterAsyncDispatch() 又默认跳过 ASYNC，
                        // 于是 anyRequest().authenticated() 会把这次“收尾 dispatch”判成未认证，
                        // 抛 AccessDeniedException；而响应已经 committed，
                        // 结果是连接被中止、chunked 缺终止块（前端 fetch 直接报错）。
                        //
                        // 语义上这是**同一个请求的续传**，原始请求已经鉴权过，不应重复鉴权。
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(PERMIT_ALL).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
