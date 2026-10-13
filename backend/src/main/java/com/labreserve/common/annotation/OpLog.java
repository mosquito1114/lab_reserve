package com.labreserve.common.annotation;

import com.labreserve.common.enums.OperationModule;
import com.labreserve.common.enums.OperationType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解。
 *
 * <p>标在 Controller 方法上，由 {@link OpLogAspect} 自动采集并异步落库。
 * 所有写操作（新增 / 修改 / 删除 / 审核 / 派单 / 验收 / 导出）都必须加。
 *
 * <pre>{@code
 * @OpLog(module = OperationModule.RESERVATION, type = OperationType.AUDIT, content = "审核预约")
 * @PostMapping("/audit")
 * public R<Void> audit(@RequestBody @Valid ReservationAuditDTO dto) { ... }
 * }</pre>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OpLog {

    /** 所属模块 */
    OperationModule module();

    /** 操作类型 */
    OperationType type();

    /** 操作内容描述（人类可读） */
    String content();

    /**
     * 是否记录请求参数。
     *
     * <p>默认记录，入库前会统一脱敏（手机号）并截断。
     * 涉及密码等敏感入参的接口（登录、改密）应设为 {@code false}。
     */
    boolean saveParam() default true;
}
