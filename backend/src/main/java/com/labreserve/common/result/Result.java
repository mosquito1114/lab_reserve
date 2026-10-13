package com.labreserve.common.result;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一响应体。
 *
 * <p>所有 Controller 方法都返回 {@code Result<T>}，由 {@link #ok(Object)} 与
 * {@link #fail} 系列工厂方法构造。
 *
 * <pre>
 * {
 *   "code": 200,
 *   "message": "success",
 *   "data": { },
 *   "timestamp": 1758000000000
 * }
 * </pre>
 *
 * <p>失败时 {@code data} 仍然输出（值为 {@code null}），方便前端统一处理；
 * 例外是业务冲突（409），会带上冲突详情供前端弹窗使用。
 *
 * @param <T> 业务数据类型
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int code;
    private String message;
    private T data;
    private long timestamp;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    // ============================================================
    // 成功
    // ============================================================

    /** 成功，无数据（新增 / 修改 / 删除类接口用这个） */
    public static Result<Void> ok() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /** 成功，带业务数据 */
    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    // ============================================================
    // 失败
    // ============================================================

    /** 用状态码枚举的默认提示 */
    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /** 用状态码枚举 + 自定义提示 */
    public static <T> Result<T> fail(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null);
    }

    /** 带业务数据一起返回的失败（典型场景：409 冲突携带 queueCount / canQueue） */
    public static <T> Result<T> fail(ResultCode resultCode, String message, T data) {
        return new Result<>(resultCode.getCode(), message, data);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> fail(int code, String message, T data) {
        return new Result<>(code, message, data);
    }

    /** 仅供服务端判断，不序列化到响应体（否则响应里会多出一个 success 字段） */
    @JsonIgnore
    public boolean isSuccess() {
        return this.code == ResultCode.SUCCESS.getCode();
    }
}
