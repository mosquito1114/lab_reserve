package com.labreserve.security;

import com.labreserve.config.JwtProperties;
import com.labreserve.model.vo.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与解析。
 *
 * <p>两种 token：
 * <ul>
 *   <li><b>access</b>：访问凭证，默认 2 小时，随请求头 {@code Authorization: Bearer xxx} 带上</li>
 *   <li><b>refresh</b>：续期凭证，默认 7 天，只在 {@code /api/auth/refresh} 使用，
 *       同时以 {@code lab:reserve:auth:token:{userId}} 存在 Redis 里（单端登录：新登录覆盖旧的）</li>
 * </ul>
 *
 * <p>Payload 里携带 {@code userId / username / realName / roleId / roleCode / jti}，
 * 因此鉴权链路上<b>不需要每请求查库</b>——过滤器和权限服务直接读 claim。
 *
 * <p>{@code jti} 有两个用途：登出时写入黑名单（{@code auth:blacklist:{jti}}）；
 * 前端拿到 401 时也能据此判断是「过期」还是「被踢下线」。
 */
@Slf4j
@Component
public class JwtTokenProvider {

    /** HS256 要求密钥至少 256 bit（32 字节） */
    private static final int MIN_SECRET_BYTES = 32;

    /** token 类型 claim */
    public static final String CLAIM_TYPE = "type";
    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_REAL_NAME = "realName";
    public static final String CLAIM_ROLE_ID = "roleId";
    public static final String CLAIM_ROLE_CODE = "roleCode";

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    @Autowired
    private JwtProperties properties;

    /** HS256 密钥，由 {@link #init()} 按配置生成 */
    private SecretKey secretKey;

    /**
     * 启动即校验密钥长度：太短的密钥会让 HS256 变得可暴力破解，
     * 与其线上出问题，不如让应用启动失败（字段注入后等效于原来的构造器校验）。
     */
    @PostConstruct
    void init() {
        String secret = properties.getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "lab-reserve.jwt.secret 未配置或长度不足 " + MIN_SECRET_BYTES + " 字节，请检查 .env 里的 JWT_SECRET");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------
    // 签发
    // ------------------------------------------------------------

    /** 签发 access token */
    public String createAccessToken(LoginUser user) {
        return create(user, TYPE_ACCESS, Duration.ofSeconds(properties.getAccessExpireSeconds()));
    }

    /** 签发 refresh token */
    public String createRefreshToken(LoginUser user) {
        return create(user, TYPE_REFRESH, Duration.ofSeconds(properties.getRefreshExpireSeconds()));
    }

    /** 为任意用户签发 access token（供单元测试与后续模块复用） */
    public String create(LoginUser user, String type, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getUserId()))
                .id(UUID.randomUUID().toString().replace("-", ""))
                .claim(CLAIM_TYPE, type)
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_REAL_NAME, user.getRealName())
                .claim(CLAIM_ROLE_ID, user.getRoleId())
                .claim(CLAIM_ROLE_CODE, user.getRoleCode())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(secretKey)
                .compact();
    }

    // ------------------------------------------------------------
    // 解析
    // ------------------------------------------------------------

    /**
     * 解析并验签。
     *
     * @return 解析结果；token 非法、过期或类型不符时返回 {@code null}（不抛异常，交给调用方决定怎么响应）
     */
    public JwtPayload parse(String token, String expectedType) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String type = claims.get(CLAIM_TYPE, String.class);
            if (expectedType != null && !expectedType.equals(type)) {
                log.debug("[JWT] token 类型不符：期望 {}，实际 {}", expectedType, type);
                return null;
            }

            // roleId 在 JSON 里可能被解析成 Integer，这里统一按 Number 取，避免 cast 异常
            Object roleIdRaw = claims.get(CLAIM_ROLE_ID);
            Long roleId = (roleIdRaw instanceof Number number) ? number.longValue() : null;

            return new JwtPayload(
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_USERNAME, String.class),
                    claims.get(CLAIM_REAL_NAME, String.class),
                    roleId,
                    claims.get(CLAIM_ROLE_CODE, String.class),
                    claims.getId(),
                    type,
                    claims.getExpiration());
        } catch (JwtException | IllegalArgumentException e) {
            // 过期、签名不对、格式错都在这里，统一按「token 不可用」处理
            log.debug("[JWT] 解析失败：{}", e.getMessage());
            return null;
        }
    }

    /** 解析 access token */
    public JwtPayload parseAccess(String token) {
        return parse(token, TYPE_ACCESS);
    }

    /** 解析 refresh token */
    public JwtPayload parseRefresh(String token) {
        return parse(token, TYPE_REFRESH);
    }

    /** 把 payload 还原成 SecurityContext 里放置的登录用户 */
    public static LoginUser toLoginUser(JwtPayload payload) {
        LoginUser user = new LoginUser();
        user.setUserId(payload.userId());
        user.setUsername(payload.username());
        user.setRealName(payload.realName());
        user.setRoleId(payload.roleId());
        user.setRoleCode(payload.roleCode());
        return user;
    }

    /** 剩余有效期（秒），最小 1 秒；用于黑名单 TTL */
    public static long remainingSeconds(JwtPayload payload) {
        if (payload == null || payload.expiration() == null) {
            return 1L;
        }
        long remain = Duration.between(Instant.now(), payload.expiration().toInstant()).getSeconds();
        return Math.max(remain, 1L);
    }

    public long getAccessExpireSeconds() {
        return properties.getAccessExpireSeconds();
    }

    public long getRefreshExpireSeconds() {
        return properties.getRefreshExpireSeconds();
    }

    /**
     * JWT 解析结果。
     *
     * @param userId      用户 ID
     * @param username    登录账号
     * @param realName    真实姓名
     * @param roleId      角色 ID
     * @param roleCode    角色编码
     * @param jti         token 唯一标识（黑名单键）
     * @param type        {@code access} / {@code refresh}
     * @param expiration  过期时间
     */
    public record JwtPayload(
            Long userId,
            String username,
            String realName,
            Long roleId,
            String roleCode,
            String jti,
            String type,
            Date expiration) {
    }
}
