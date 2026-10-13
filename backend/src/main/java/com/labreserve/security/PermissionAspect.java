package com.labreserve.security;

import com.labreserve.common.exception.BizException;
import com.labreserve.common.result.ResultCode;
import com.labreserve.model.vo.LoginUser;
import com.labreserve.util.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * {@link RequirePerm} 的校验切面。
 *
 * <p>校验顺序：
 * <ol>
 *   <li>没登录 → 401（正常情况下 SecurityConfig 已经挡住匿名请求，这里是兜底）</li>
 *   <li>取当前角色的权限码集合（走 {@link PermissionService} 的 Redis 缓存，不查库）</li>
 *   <li>按 {@code requireAll} 判断「全部满足」还是「任意一个满足」，不满足 → 403</li>
 * </ol>
 *
 * <p>切面只做「功能级权限」；「数据级权限」（只能看/改自己的数据）由各模块 Service 负责，
 * 例如迭代 5 的预约列表会对师生强制追加 {@code applicant_id = 当前用户}。
 */
@Slf4j
@Aspect
@Component
public class PermissionAspect {

    @Autowired
    private PermissionService permissionService;
    @Autowired
    private SecurityUtils securityUtils;

    @Around("@annotation(requirePerm)")
    public Object around(ProceedingJoinPoint joinPoint, RequirePerm requirePerm) throws Throwable {
        String[] required = requirePerm.value();
        if (required.length == 0) {
            return joinPoint.proceed();
        }

        LoginUser loginUser = securityUtils.getLoginUser();
        if (loginUser == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "未登录或登录已失效，请重新登录");
        }

        Set<String> owned = permissionService.listCodesByRoleId(loginUser.getRoleId());
        boolean pass = requirePerm.requireAll()
                ? owned.containsAll(Arrays.asList(required))
                : Arrays.stream(required).anyMatch(owned::contains);

        if (!pass) {
            log.warn("[权限] 拒绝访问：userId={}, roleCode={}, 需要={}, 拥有={}",
                    loginUser.getUserId(), loginUser.getRoleCode(), Arrays.toString(required), owned.size());
            throw BizException.forbidden(requirePerm.message());
        }

        return joinPoint.proceed();
    }
}
