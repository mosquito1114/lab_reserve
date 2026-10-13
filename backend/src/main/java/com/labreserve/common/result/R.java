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
public class R<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int code;
    private String message;
    private T data;
    private long timestamp;

    private R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    // ------------------------------------------------------------
    // 成功
    // ------------------------------------------------------------

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    // ------------------------------------------------------------
    // 失败
    // ------------------------------------------------------------

    public static <T> R<T> fail(ResultCode resultCode) {
        return new R<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public static <T> R<T> fail(ResultCode resultCode, String message) {
        return new R<>(resultCode.getCode(), message, null);
    }

    /** 带业务数据一起返回的失败（典型场景：409 冲突携带 queueCount / canQueue） */
    public static <T> R<T> fail(ResultCode resultCode, String message, T data) {
        return new R<>(resultCode.getCode(), message, data);
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }

    public static <T> R<T> fail(int code, String message, T data) {
        return new R<>(code, message, data);
    }

    /** 仅供服务端判断，不序列化到响应体（否则响应里会多出一个 success 字段） */
    @JsonIgnore
    public boolean isSuccess() {
        return this.code == ResultCode.SUCCESS.getCode();
    }
}
