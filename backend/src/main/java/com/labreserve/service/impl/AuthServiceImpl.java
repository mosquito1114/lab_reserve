package com.labreserve.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labreserve.common.constant.CommonConst;
import com.labreserve.common.constant.RedisKeyConst;
import com.labreserve.common.enums.SensitiveType;
import com.labreserve.common.exception.BizException;
import com.labreserve.common.result.ResultCode;
import com.labreserve.mapper.RoleMapper;
import com.labreserve.mapper.UserMapper;
import com.labreserve.model.dto.LoginDTO;
import com.labreserve.model.dto.UpdatePasswordDTO;
import com.labreserve.model.entity.Role;
import com.labreserve.model.entity.User;
import com.labreserve.model.vo.CurrentUserVO;
import com.labreserve.model.vo.LoginUserVO;
import com.labreserve.model.vo.LoginVO;
import com.labreserve.model.vo.LoginUser;
import com.labreserve.security.JwtTokenProvider;
import com.labreserve.security.MenuService;
import com.labreserve.security.PermissionService;
import com.labreserve.service.AuthService;
import com.labreserve.util.RedisUtils;
import com.labreserve.util.SecurityUtils;
import com.labreserve.util.SensitiveUtils;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 认证服务实现。
 *
 * <h3>登录失败计数（需求：5 次锁定 15 分钟）</h3>
 * <pre>
 * key   lab:reserve:login:fail:{username}
 * value 连续失败次数（登录成功即删除）
 * ttl   15 分钟 —— 第一次失败时设置，因此「锁定」会在最后一次失败后 15 分钟自动解除
 * </pre>
 *
 * <h3>会话与 token</h3>
 * <ul>
 *   <li>access token：2 小时，无状态，payload 里带 userId / roleCode / jti，鉴权不查库</li>
 *   <li>refresh token：7 天，同时在 Redis {@code auth:token:{userId}} 留一份，
 *       因此「登出 / 改密 / 再次登录」都会让旧的 refresh token 立即作废（单端登录）</li>
 *   <li>登出 / 改密时把 access 的 jti 写入 {@code auth:blacklist:{jti}}，TTL = 剩余有效期</li>
 * </ul>
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private RoleMapper roleMapper;
    @Autowired
    private PermissionService permissionService;
    @Autowired
    private MenuService menuService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private RedisUtils redisUtils;
    @Autowired
    private SecurityUtils securityUtils;
    @Autowired
    private SensitiveUtils sensitiveUtils;

    // ------------------------------------------------------------
    // 登录
    // ------------------------------------------------------------

    @Override
    public LoginVO login(LoginDTO dto) {
        String username = dto.getUsername();
        String failKey = RedisKeyConst.LOGIN_FAIL + username;

        // ① 先看是否处于锁定期
        long failed = currentFailCount(failKey);
        if (failed >= CommonConst.LOGIN_FAIL_MAX) {
            throw new BizException(ResultCode.BAD_REQUEST, lockMessage());
        }

        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, username));

        // ② 账号不存在或密码错误：都按「账号或密码错误」处理，避免泄漏账号是否存在
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            long count = redisUtils.incrementWithTtl(failKey, Duration.ofMinutes(CommonConst.LOGIN_LOCK_MINUTES));
            if (count >= CommonConst.LOGIN_FAIL_MAX) {
                log.warn("[登录] 账号 {} 连续失败 {} 次，已锁定 {} 分钟", username, count, CommonConst.LOGIN_LOCK_MINUTES);
                throw new BizException(ResultCode.BAD_REQUEST, lockMessage());
            }
            throw new BizException(ResultCode.UNAUTHORIZED,
                    "账号或密码错误，还可尝试 " + (CommonConst.LOGIN_FAIL_MAX - count) + " 次");
        }

        // ③ 已注销账号不允许登录
        if (!user.isActive()) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已注销，请联系系统管理员");
        }

        // ④ 登录成功：清零失败计数
        redisUtils.delete(failKey);

        Role role = roleMapper.selectById(user.getRoleId());
        LoginUser loginUser = buildLoginUser(user, role);
        // 登录成功即把「本次请求」标记为已认证：这样登录操作的日志能记下操作人（否则 operator_id 为空）
        authenticate(loginUser);
        Set<String> codes = permissionService.listCodesByRoleId(user.getRoleId());

        String accessToken = jwtTokenProvider.createAccessToken(loginUser);
        String refreshToken = jwtTokenProvider.createRefreshToken(loginUser);
        saveRefreshToken(user.getId(), refreshToken);
        touchLastLogin(user.getId());

        log.info("[登录] 成功：userId={}, username={}, roleCode={}", user.getId(), username, role.getCode());
        return buildLoginVO(accessToken, refreshToken, user, role, codes);
    }

    // ------------------------------------------------------------
    // 刷新
    // ------------------------------------------------------------

    @Override
    public LoginVO refresh(String refreshToken) {
        JwtTokenProvider.JwtPayload payload = jwtTokenProvider.parseRefresh(refreshToken);
        if (payload == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }

        // refresh token 必须与 Redis 中记录的一致（登出 / 改密 / 新登录会覆盖它）
        String stored = redisUtils.get(RedisKeyConst.AUTH_TOKEN + payload.userId());
        if (stored == null || !stored.equals(refreshToken)) {
            throw new BizException(ResultCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }

        User user = userMapper.selectById(payload.userId());
        if (user == null || !user.isActive()) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已注销，请联系系统管理员");
        }

        Role role = roleMapper.selectById(user.getRoleId());
        LoginUser loginUser = buildLoginUser(user, role);
        Set<String> codes = permissionService.listCodesByRoleId(user.getRoleId());

        LoginVO vo = buildLoginVO(jwtTokenProvider.createAccessToken(loginUser), refreshToken, user, role, codes);
        log.debug("[登录] 刷新 access token：userId={}", user.getId());
        return vo;
    }

    // ------------------------------------------------------------
    // 登出
    // ------------------------------------------------------------

    @Override
    public void logout(String accessToken) {
        JwtTokenProvider.JwtPayload payload = jwtTokenProvider.parseAccess(accessToken);
        if (payload == null) {
            // token 已经无效，登出视为成功（幂等）
            return;
        }
        blacklist(payload);
        // 连同 refresh token 一起作废，避免「登出后还能续期」
        redisUtils.delete(RedisKeyConst.AUTH_TOKEN + payload.userId());
        log.info("[登出] userId={}, username={}", payload.userId(), payload.username());
    }

    // ------------------------------------------------------------
    // 当前用户信息
    // ------------------------------------------------------------

    @Override
    public CurrentUserVO getCurrentUser() {
        LoginUser loginUser = requireLogin();
        User user = userMapper.selectById(loginUser.getUserId());
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }
        Role role = roleMapper.selectById(user.getRoleId());
        Set<String> codes = permissionService.listCodesByRoleId(user.getRoleId());

        CurrentUserVO vo = new CurrentUserVO();
        vo.setUser(toUserVO(user, role));
        vo.setPermissions(List.copyOf(codes));
        vo.setMenus(menuService.build(codes));
        return vo;
    }

    // ------------------------------------------------------------
    // 修改密码
    // ------------------------------------------------------------

    @Override
    public void updatePassword(UpdatePasswordDTO dto, String accessToken) {
        LoginUser loginUser = requireLogin();
        User user = userMapper.selectById(loginUser.getUserId());
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw BizException.badRequest("原密码不正确");
        }
        if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
            throw BizException.badRequest("新密码不能与原密码相同");
        }

        User update = new User();
        update.setId(user.getId());
        update.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(update);

        // 改密后旧凭证全部失效：当前 access 拉黑 + refresh 作废 + 失败计数清零
        JwtTokenProvider.JwtPayload payload = jwtTokenProvider.parseAccess(accessToken);
        if (payload != null) {
            blacklist(payload);
        }
        redisUtils.delete(RedisKeyConst.AUTH_TOKEN + user.getId());
        redisUtils.delete(RedisKeyConst.LOGIN_FAIL + user.getUsername());

        log.info("[改密] userId={}, username={} 修改密码成功，旧 token 已失效", user.getId(), user.getUsername());
    }

    // ------------------------------------------------------------
    // 私有方法
    // ------------------------------------------------------------

    private LoginUser requireLogin() {
        LoginUser loginUser = securityUtils.getLoginUser();
        if (loginUser == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "未登录或登录已失效，请重新登录");
        }
        return loginUser;
    }

    /**
     * 把用户放进当前请求的 SecurityContext。
     *
     * <p>只影响本次请求（STATELESS，不写 session）：登录成功后 {@code @OpLog} 切面才能取到操作人。
     */
    private void authenticate(LoginUser loginUser) {
        List<SimpleGrantedAuthority> authorities = loginUser.getRoleCode() == null
                ? List.of()
                : List.of(new SimpleGrantedAuthority("ROLE_" + loginUser.getRoleCode()));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(loginUser, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private long currentFailCount(String failKey) {
        String value = redisUtils.get(failKey);
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            redisUtils.delete(failKey);
            return 0L;
        }
    }

    private static String lockMessage() {
        return "账号已锁定，请 " + CommonConst.LOGIN_LOCK_MINUTES + " 分钟后重试";
    }

    private void saveRefreshToken(Long userId, String refreshToken) {
        redisUtils.set(RedisKeyConst.AUTH_TOKEN + userId, refreshToken,
                Duration.ofSeconds(jwtTokenProvider.getRefreshExpireSeconds()));
    }

    private void blacklist(JwtTokenProvider.JwtPayload payload) {
        if (payload.jti() == null) {
            return;
        }
        redisUtils.set(RedisKeyConst.AUTH_BLACKLIST + payload.jti(), "1",
                Duration.ofSeconds(JwtTokenProvider.remainingSeconds(payload)));
    }

    /** 更新最后登录时间（只更新这一列，其余字段为 null 会被 MyBatis-Plus 忽略） */
    private void touchLastLogin(Long userId) {
        User update = new User();
        update.setId(userId);
        update.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(update);
    }

    private LoginUser buildLoginUser(User user, Role role) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setRealName(user.getRealName());
        loginUser.setRoleId(user.getRoleId());
        loginUser.setRoleCode(role == null ? null : role.getCode());
        return loginUser;
    }

    private LoginVO buildLoginVO(String accessToken, String refreshToken,
                                 User user, Role role, Set<String> codes) {
        LoginVO vo = new LoginVO();
        vo.setAccessToken(accessToken);
        vo.setRefreshToken(refreshToken);
        vo.setExpiresIn(jwtTokenProvider.getAccessExpireSeconds());
        vo.setUser(toUserVO(user, role));
        vo.setPermissions(List.copyOf(codes));
        return vo;
    }

    /**
     * 实体 → 视图对象。
     *
     * <p>手机号脱敏：迭代 1 只做「本人可见自己」，因此统一按脱敏输出（与 {@code api.md}
     * 的响应示例一致）；迭代 2 的用户管理模块会引入 {@code revealPhone} 与
     * {@code QUERY_PHONE} 操作日志。
     */
    private LoginUserVO toUserVO(User user, Role role) {
        LoginUserVO vo = new LoginUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setRoleCode(role == null ? null : role.getCode());
        vo.setRoleName(role == null ? null : role.getName());
        vo.setPhone(sensitiveUtils.mask(user.getPhone(), SensitiveType.PHONE));
        vo.setEmail(user.getEmail());
        vo.setNotifyEmail(user.getNotifyEmail());
        vo.setAvatar(null);
        return vo;
    }
}
