package com.labreserve.security;

import com.labreserve.common.constant.RedisKeyConst;
import com.labreserve.mapper.PermissionMapper;
import com.labreserve.model.vo.LoginUser;
import com.labreserve.util.RedisUtils;
import com.labreserve.util.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 权限服务。
 *
 * <p>「角色 → 权限码」是全站最高频的查询，因此查一次库之后缓存在 Redis：
 * <pre>
 * key   lab:reserve:perm:role:{roleId}
 * value 权限码，逗号分隔（StringRedisTemplate 纯字符串，redis-cli 可读）
 * ttl   不设过期 —— 角色权限是业务数据，变更时由 {@link #evictRole} 主动删除
 * </pre>
 *
 * <p>角色权限是「5 个角色 × 47 个权限点」量级，一次查全再内存判断，
 * 比每个接口查一次数据库快得多；权限变更后删除缓存即可立刻生效。
 */
@Slf4j
@Service
public class PermissionService {

    private static final String SEPARATOR = ",";

    @Autowired
    private PermissionMapper permissionMapper;
    @Autowired
    private RedisUtils redisUtils;
    @Autowired
    private SecurityUtils securityUtils;

    /**
     * 查询角色拥有的权限码集合（优先读缓存）。
     *
     * @param roleId 角色 ID
     * @return 权限码集合；角色不存在或没有权限时返回空集合
     */
    public Set<String> listCodesByRoleId(Long roleId) {
        if (roleId == null) {
            return Set.of();
        }
        String key = RedisKeyConst.permRoleKey(roleId);
        String cached = redisUtils.get(key);
        if (cached != null) {
            if (cached.isBlank()) {
                return Set.of();
            }
            return new LinkedHashSet<>(Arrays.asList(cached.split(SEPARATOR)));
        }

        List<String> codes = permissionMapper.selectCodesByRoleId(roleId);
        // 空集合也缓存（写成空串），避免「没有权限的角色」每次请求都打库
        redisUtils.set(key, String.join(SEPARATOR, codes));
        log.debug("[权限] 角色 {} 的权限码已缓存，共 {} 个", roleId, codes.size());
        return new LinkedHashSet<>(codes);
    }

    /** 当前登录用户的权限码集合 */
    public Set<String> listCurrentUserCodes() {
        LoginUser loginUser = securityUtils.getLoginUser();
        return loginUser == null ? Set.of() : listCodesByRoleId(loginUser.getRoleId());
    }

    /** 当前登录用户是否拥有指定权限码 */
    public boolean hasPermission(String code) {
        return code != null && listCurrentUserCodes().contains(code);
    }

    /** 删除某角色的权限缓存（角色权限变更后调用，下一次请求自动回源） */
    public void evictRole(Long roleId) {
        if (roleId == null) {
            return;
        }
        redisUtils.delete(RedisKeyConst.permRoleKey(roleId));
        log.info("[权限] 角色 {} 的权限缓存已失效", roleId);
    }
}
