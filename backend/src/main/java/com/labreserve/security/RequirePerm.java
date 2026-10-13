package com.labreserve.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解。
 *
 * <p>标在 Controller 方法（或类）上，由 {@link PermissionAspect} 在调用前校验
 * 当前登录用户是否拥有指定权限码；没有则抛 403。
 *
 * <pre>{@code
 * @RequirePerm("reservation:audit")
 * @OpLog(module = OperationModule.RESERVATION, type = OperationType.AUDIT, content = "审核预约")
 * @PostMapping("/audit")
 * public Result<Void> audit(@RequestBody @Valid ReservationAuditDTO dto) { ... }
 * }</pre>
 *
 * <p>多个权限码默认「任意一个满足即可」（{@code requireAll = false}）。
 *
 * <p>为什么不用 Spring Security 的 {@code @PreAuthorize}：本项目权限是<b>数据</b>
 * （存在 {@code tb_role_permission}，管理员可在界面上勾选），
 * 用权限码注解 + 查缓存的服务判断，比把权限硬编码进 SpEL 表达式更贴合设计。
 *
 * <p>注意：注解类型里不能定义静态方法，因此「抛 403」的便捷写法放在
 * {@code BizException#forbidden(String)}，数据级权限判断请直接用它。
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePerm {

    /** 需要的权限码，如 {@code reservation:lab:create} */
    String[] value();

    /** 为 true 时要求同时拥有全部权限码，默认满足任意一个即可 */
    boolean requireAll() default false;

    /** 校验失败时的提示语 */
    String message() default "无操作权限";
}
