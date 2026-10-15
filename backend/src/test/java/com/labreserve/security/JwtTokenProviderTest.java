package com.labreserve.security;

import com.labreserve.config.JwtProperties;
import com.labreserve.model.vo.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JwtTokenProvider} 单元测试。
 *
 * <p>覆盖迭代 1 的三条关键约束：
 * ① access token 能带出全部 claim；② refresh token 不能当 access 用；
 * ③ 过期 token 一律解析失败（返回 null，由调用方转成 401）。
 */
class JwtTokenProviderTest {

    /** 48 字节的测试密钥，满足 HS256 最小长度 */
    private static final String SECRET = "unit-test-secret-0123456789-0123456789-abcdef";

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = newProvider(properties(SECRET));
    }

    @Test
    @DisplayName("access token 能还原出全部 claim")
    void accessTokenShouldCarryAllClaims() {
        LoginUser user = loginUser();

        String token = provider.createAccessToken(user);
        JwtTokenProvider.JwtPayload payload = provider.parseAccess(token);

        assertNotNull(payload);
        assertEquals(1L, payload.userId());
        assertEquals("admin", payload.username());
        assertEquals("系统管理员", payload.realName());
        assertEquals(3L, payload.roleId());
        assertEquals("SYS_ADMIN", payload.roleCode());
        assertEquals(JwtTokenProvider.TYPE_ACCESS, payload.type());
        assertNotNull(payload.jti(), "jti 必须存在，登出黑名单依赖它");

        // 还原成 SecurityContext 里的登录用户
        LoginUser restored = JwtTokenProvider.toLoginUser(payload);
        assertEquals(user.getUserId(), restored.getUserId());
        assertEquals(user.getRoleCode(), restored.getRoleCode());
    }

    @Test
    @DisplayName("refresh token 不能当作 access token 使用")
    void refreshTokenShouldNotBeAcceptedAsAccess() {
        String refreshToken = provider.createRefreshToken(loginUser());

        assertNotNull(provider.parseRefresh(refreshToken));
        assertNull(provider.parseAccess(refreshToken), "类型不符时必须解析失败");
    }

    @Test
    @DisplayName("过期 token 解析失败")
    void expiredTokenShouldBeRejected() {
        LoginUser user = loginUser();
        String expired = provider.create(user, JwtTokenProvider.TYPE_ACCESS, Duration.ofSeconds(-10));

        assertNull(provider.parseAccess(expired));
    }

    @Test
    @DisplayName("被篡改的 token 解析失败")
    void tamperedTokenShouldBeRejected() {
        String token = provider.createAccessToken(loginUser());
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertNull(provider.parseAccess(tampered));
    }

    @Test
    @DisplayName("剩余有效期用于黑名单 TTL，至少 1 秒")
    void remainingSecondsShouldBePositive() {
        JwtTokenProvider.JwtPayload payload = provider.parseAccess(provider.createAccessToken(loginUser()));

        assertNotNull(payload);
        assertTrue(JwtTokenProvider.remainingSeconds(payload) > 0);
    }

    @Test
    @DisplayName("密钥太短时启动即失败（避免线上用弱密钥）")
    void shortSecretShouldFailFast() {
        JwtProperties properties = properties("too-short");

        assertThrows(IllegalStateException.class, () -> newProvider(properties));
    }

    // ------------------------------------------------------------

    /**
     * 构造一个 JwtTokenProvider：本类改为 @Autowired 字段注入后没有带参构造，
     * 测试用 ReflectionTestUtils 手动注入配置再触发 @PostConstruct 的 init()。
     */
    private static JwtTokenProvider newProvider(JwtProperties properties) {
        JwtTokenProvider target = new JwtTokenProvider();
        ReflectionTestUtils.setField(target, "properties", properties);
        target.init();
        return target;
    }

    private static JwtProperties properties(String secret) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setAccessExpireSeconds(7200L);
        properties.setRefreshExpireSeconds(604800L);
        return properties;
    }

    private static LoginUser loginUser() {
        LoginUser user = new LoginUser();
        user.setUserId(1L);
        user.setUsername("admin");
        user.setRealName("系统管理员");
        user.setRoleId(3L);
        user.setRoleCode("SYS_ADMIN");
        return user;
    }
}
