package com.labreserve.security;

import com.labreserve.common.constant.CommonConst;
import com.labreserve.common.constant.RedisKeyConst;
import com.labreserve.model.vo.LoginUser;
import com.labreserve.util.RedisUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器。
 *
 * <p>放在 {@code UsernamePasswordAuthenticationFilter} 之前，职责只有一件事：
 * 把请求头里的 access token 换成 {@code SecurityContext} 里的 {@link LoginUser}。
 *
 * <p>「不通过」一律表现为<b>不设置认证信息</b>（保持匿名），而不是直接返回错误：
 * 免登录接口（登录、刷新、健康检查、Knife4j）照常放行，
 * 受保护接口由 {@code AuthorizationFilter} + {@link RestAuthenticationEntryPoint} 统一返回 401。
 *
 * <p>三种 token 不可用的情况都在这里被拦下：
 * ① 解析失败（过期 / 签名不对 / 格式错）；② 类型不是 access（防止拿 refresh 当 access 用）；
 * ③ 已被拉黑（登出、改密后的旧 token）。
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private RedisUtils redisUtils;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(request);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request) {
        String token = resolveToken(request);
        if (token == null) {
            return;
        }

        JwtTokenProvider.JwtPayload payload = jwtTokenProvider.parseAccess(token);
        if (payload == null) {
            return;
        }

        String blacklistKey = RedisKeyConst.AUTH_BLACKLIST + payload.jti();
        if (Boolean.TRUE.equals(redisUtils.hasKey(blacklistKey))) {
            log.debug("[JWT] token 已在黑名单中，userId={}", payload.userId());
            return;
        }

        LoginUser loginUser = JwtTokenProvider.toLoginUser(payload);
        List<SimpleGrantedAuthority> authorities = payload.roleCode() == null
                ? List.of()
                : List.of(new SimpleGrantedAuthority("ROLE_" + payload.roleCode()));

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(loginUser, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * 从 {@code Authorization: Bearer xxx} 中取出 token。
     *
     * @return token；请求头缺失或格式不符时返回 {@code null}
     */
    public static String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(CommonConst.TOKEN_HEADER);
        if (header == null || !header.startsWith(CommonConst.TOKEN_PREFIX)) {
            return null;
        }
        String token = header.substring(CommonConst.TOKEN_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
