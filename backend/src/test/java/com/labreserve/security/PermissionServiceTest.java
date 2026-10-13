package com.labreserve.security;

import com.labreserve.common.constant.RedisKeyConst;
import com.labreserve.mapper.PermissionMapper;
import com.labreserve.util.RedisUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PermissionService} 单元测试。
 *
 * <p>重点验证缓存语义：命中缓存不查库、未命中查库后写缓存、
 * 空权限也要缓存（避免「没有权限的角色」每次请求都打库）。
 */
@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    private static final Long ROLE_ID = 4L;
    private static final String CACHE_KEY = RedisKeyConst.PERM_ROLE + ROLE_ID;

    @Mock
    private PermissionMapper permissionMapper;

    @Mock
    private RedisUtils redisUtils;

    @InjectMocks
    private PermissionService permissionService;

    @Test
    @DisplayName("缓存命中：不查数据库")
    void shouldReadFromCache() {
        when(redisUtils.get(CACHE_KEY)).thenReturn("user:list,reservation:audit");

        Set<String> codes = permissionService.listCodesByRoleId(ROLE_ID);

        assertEquals(Set.of("user:list", "reservation:audit"), codes);
        verify(permissionMapper, never()).selectCodesByRoleId(ROLE_ID);
    }

    @Test
    @DisplayName("缓存未命中：查库并写缓存")
    void shouldQueryAndCacheOnMiss() {
        when(redisUtils.get(CACHE_KEY)).thenReturn(null);
        when(permissionMapper.selectCodesByRoleId(ROLE_ID))
                .thenReturn(List.of("resource:lab:list", "resource:equip:list"));

        Set<String> codes = permissionService.listCodesByRoleId(ROLE_ID);

        assertEquals(Set.of("resource:lab:list", "resource:equip:list"), codes);
        verify(redisUtils, times(1)).set(CACHE_KEY, "resource:lab:list,resource:equip:list");
    }

    @Test
    @DisplayName("角色没有任何权限时缓存空串，避免每次请求都打库")
    void shouldCacheEmptyResult() {
        when(redisUtils.get(CACHE_KEY)).thenReturn(null);
        when(permissionMapper.selectCodesByRoleId(ROLE_ID)).thenReturn(List.of());

        assertTrue(permissionService.listCodesByRoleId(ROLE_ID).isEmpty());
        verify(redisUtils, times(1)).set(CACHE_KEY, "");
    }

    @Test
    @DisplayName("权限变更后清除缓存")
    void shouldEvictCache() {
        permissionService.evictRole(ROLE_ID);

        verify(redisUtils, times(1)).delete(CACHE_KEY);
    }
}
