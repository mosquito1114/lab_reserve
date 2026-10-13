package com.labreserve.common.aop;

import com.labreserve.common.annotation.OpLog;
import com.labreserve.util.IpUtils;
import com.labreserve.util.JsonUtils;
import com.labreserve.model.entity.OperationLog;
import com.labreserve.util.SensitiveUtils;
import com.labreserve.model.vo.LoginUser;
import com.labreserve.util.SecurityUtils;
import com.labreserve.service.OperationLogService;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;

/**
 * 操作日志切面。
 *
 * <p>对应需求模块 7「操作日志记录」：自动记录操作人、时间、操作内容、IP 地址。
 * 日志内容为：模块 / 操作类型 / 描述 / 请求方法 / URL / 参数（已脱敏）/ IP / UA / 结果 / 耗时。
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li><b>异步写库</b>：通过 {@link OperationLogService#record} 走独立线程池</li>
 *   <li><b>失败不阻断</b>：无论组装还是写入失败，都只打日志</li>
 *   <li><b>参数脱敏</b>：入库前统一把手机号替换成 {@code 138****1234}</li>
 *   <li><b>登录失败也记录</b>：此时 SecurityContext 为空，操作人记为 null</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OpLogAspect {

    private final OperationLogService operationLogService;

    /** 请求参数最大长度（列类型为 TEXT，这里主动截断避免日志过大） */
    private static final int PARAM_MAX_LEN = 2000;
    /** tb_operation_log.content 为 VARCHAR(500) */
    private static final int CONTENT_MAX_LEN = 500;
    /** tb_operation_log.error_msg 为 VARCHAR(500) */
    private static final int ERROR_MAX_LEN = 500;
    /** tb_operation_log.request_url 为 VARCHAR(255) */
    private static final int URL_MAX_LEN = 255;

    @Around("@annotation(opLog)")
    public Object around(ProceedingJoinPoint joinPoint, OpLog opLog) throws Throwable {
        long start = System.currentTimeMillis();
        Throwable error = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            try {
                OperationLog entity = build(joinPoint, opLog, System.currentTimeMillis() - start, error);
                operationLogService.record(entity);
            } catch (Exception e) {
                log.error("[操作日志] 组装失败，content={}", opLog.content(), e);
            }
        }
    }

    // ------------------------------------------------------------

    private OperationLog build(ProceedingJoinPoint joinPoint, OpLog opLog, long costMs, Throwable error) {
        OperationLog entity = new OperationLog();

        // 操作人（未登录时全部为 null，例如登录失败场景）
        LoginUser user = SecurityUtils.getLoginUser();
        if (user != null) {
            entity.setOperatorId(user.getUserId());
            entity.setOperatorName(user.getRealName());
            entity.setRoleCode(user.getRoleCode());
        }

        entity.setModule(opLog.module().getCode());
        entity.setOperationType(opLog.type().name());
        entity.setContent(truncate(opLog.content(), CONTENT_MAX_LEN));

        HttpServletRequest request = currentRequest();
        if (request != null) {
            entity.setRequestMethod(request.getMethod());
            entity.setRequestUrl(truncate(request.getRequestURI(), URL_MAX_LEN));
            entity.setIp(IpUtils.getClientIp(request));
            entity.setUserAgent(IpUtils.truncateUserAgent(request.getHeader("User-Agent")));
        }

        if (opLog.saveParam()) {
            entity.setRequestParam(serializeArgs(joinPoint.getArgs()));
        }

        entity.setResult(error == null ? 1 : 0);
        if (error != null) {
            entity.setErrorMsg(truncate(error.getMessage(), ERROR_MAX_LEN));
        }
        entity.setCostMs((int) Math.min(costMs, Integer.MAX_VALUE));
        return entity;
    }

    /**
     * 序列化方法入参。
     *
     * <p>过滤掉无法（也不该）序列化的类型：Servlet 对象、上传文件、二进制。
     * 序列化后统一做手机号脱敏再截断。
     */
    private String serializeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        Object[] serializable = Arrays.stream(args)
                .filter(arg -> !(arg instanceof ServletRequest))
                .filter(arg -> !(arg instanceof ServletResponse))
                .filter(arg -> !(arg instanceof MultipartFile))
                .filter(arg -> !(arg instanceof byte[]))
                .toArray();
        if (serializable.length == 0) {
            return null;
        }
        String json = JsonUtils.toJson(serializable);
        if (json == null) {
            return null;
        }
        return truncate(SensitiveUtils.maskInText(json), PARAM_MAX_LEN);
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}
