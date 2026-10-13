package com.labreserve.common.exception;

import com.labreserve.common.result.ResultCode;
import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常。
 *
 * <p>业务代码里遇到可预期的失败（参数不合法、状态不允许、时段冲突……）
 * 一律抛这个异常，由 {@link GlobalExceptionHandler} 统一转成响应。
 * <b>禁止</b>在业务代码里裸抛 {@code RuntimeException}。
 */
@Getter
public class BizException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务状态码 */
    private final int code;

    /**
     * 冲突详情：会作为响应体的 data 返回。
     * 典型场景——预约冲突时返回 {@code {conflictCode, queueCount, canQueue}} 供前端弹窗。
     */
    private final transient Object data;

    public BizException(String message) {
        this(ResultCode.CONFLICT.getCode(), message, null);
    }

    public BizException(ResultCode resultCode) {
        this(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public BizException(ResultCode resultCode, String message) {
        this(resultCode.getCode(), message, null);
    }

    public BizException(int code, String message) {
        this(code, message, null);
    }

    public BizException(int code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public BizException(int code, String message, Object data, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.data = data;
    }

    // ------------------------------------------------------------
    // 快捷工厂
    // ------------------------------------------------------------

    /** 400 参数不合法 */
    public static BizException badRequest(String message) {
        return new BizException(ResultCode.BAD_REQUEST, message);
    }

    /** 403 无权限 */
    public static BizException forbidden(String message) {
        return new BizException(ResultCode.FORBIDDEN, message);
    }

    /** 404 资源不存在 */
    public static BizException notFound(String message) {
        return new BizException(ResultCode.NOT_FOUND, message);
    }

    /** 409 业务冲突 */
    public static BizException conflict(String message) {
        return new BizException(ResultCode.CONFLICT, message);
    }

    /** 409 业务冲突（带 detail，前端据此弹窗） */
    public static BizException conflict(String message, Object data) {
        return new BizException(ResultCode.CONFLICT.getCode(), message, data);
    }

    /**
     * 断言为真，否则抛业务异常。
     *
     * @param expression 期望为 true 的条件
     * @param message    失败信息
     */
    public static void assertTrue(boolean expression, String message) {
        if (!expression) {
            throw conflict(message);
        }
    }

    /**
     * 断言对象非空，否则抛 404。
     */
    public static <T> T requireFound(T target, String message) {
        if (target == null) {
            throw notFound(message);
        }
        return target;
    }
}
