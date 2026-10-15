package com.labreserve.common.result;

import lombok.Getter;

/**
 * 统一业务状态码。
 *
 * <p>约定：<b>HTTP 状态码与这里的 code 保持一致</b>，
 * 前端既能用 axios 的 HTTP 层判断，也能读响应体里的 {@code code}。
 *
 * @see com.labreserve.common.exception.GlobalExceptionHandler
 */
@Getter
public enum ResultCode {

    /** 成功 */
    SUCCESS(200, "success"),

    /** 参数校验失败 */
    BAD_REQUEST(400, "参数校验失败"),

    /** 未登录 / token 失效 */
    UNAUTHORIZED(401, "未登录或登录已失效"),

    /** 已登录但无权限 */
    FORBIDDEN(403, "无操作权限"),

    /** 资源不存在 */
    NOT_FOUND(404, "资源不存在"),

    /** 请求方法不支持（本项目接口全部为 POST） */
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),

    /**
     * 业务冲突：时段被占 / 设备库存不足 / 重复入队 / 处于 7 天禁入期 / 状态不允许流转。
     * <p>前端必须针对「提交预约」「加入排队」「候补确认」三个场景做特殊 UI 分支。
     */
    CONFLICT(409, "业务冲突"),

    /** 系统异常 */
    INTERNAL_ERROR(500, "系统异常，请稍后重试"),
    ;

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
