package com.labreserve.common.exception;

import com.labreserve.common.result.Result;
import com.labreserve.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * <p>约定：<b>返回的 HTTP 状态码 = 响应体里的 code</b>。
 * 前端 axios 拦截器读响应体的 {@code code} 即可，不必依赖 HTTP 层。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ------------------------------------------------------------
    // 业务异常（可预期）
    // ------------------------------------------------------------

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Object>> handleBizException(BizException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getCode());
        if (status == null) {
            status = HttpStatus.BAD_REQUEST;
        }
        log.warn("[业务异常] {} {} -> {} {}", request.getMethod(), request.getRequestURI(),
                ex.getCode(), ex.getMessage());
        return ResponseEntity.status(status)
                .body(Result.fail(ex.getCode(), ex.getMessage(), ex.getData()));
    }

    // ------------------------------------------------------------
    // 参数校验
    // ------------------------------------------------------------

    /** {@code @RequestBody @Valid} 校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::formatFieldError)
                .collect(Collectors.joining("；"));
        log.warn("[参数校验失败] {}", message);
        return badRequest(message.isEmpty() ? ResultCode.BAD_REQUEST.getMessage() : message);
    }

    /** 表单 / 查询对象绑定校验失败 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Object>> handleBindException(BindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::formatFieldError)
                .collect(Collectors.joining("；"));
        log.warn("[参数绑定失败] {}", message);
        return badRequest(message.isEmpty() ? ResultCode.BAD_REQUEST.getMessage() : message);
    }

    /** {@code @Validated} 修饰的类上的方法参数校验失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Object>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("；"));
        log.warn("[约束校验失败] {}", message);
        return badRequest(message.isEmpty() ? ResultCode.BAD_REQUEST.getMessage() : message);
    }

    /** 缺少必填参数（本项目参数都在 body，出现此异常通常是前端调用方式不对） */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        return badRequest("缺少必填参数：" + ex.getParameterName());
    }

    /** 请求体不是合法 JSON */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Object>> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("[请求体解析失败] {}", ex.getMessage());
        return badRequest("请求体格式错误，应为合法的 JSON");
    }

    // ------------------------------------------------------------
    // HTTP 层
    // ------------------------------------------------------------

    /** 本项目所有接口都是 POST，用错方法时给出明确提示 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("[请求方法不支持] {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Result.fail(ResultCode.METHOD_NOT_ALLOWED,
                        "本系统接口统一使用 POST，收到 " + ex.getMethod()));
    }

    /** 上传文件超过限制 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("[上传文件过大] {}", ex.getMessage());
        return badRequest("上传文件过大，请压缩后重试");
    }

    /** 已登录但无权限（通常由 Spring Security 抛出，权限注解抛的是 BizException） */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Object>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("[无权限] {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Result.fail(ResultCode.FORBIDDEN));
    }

    // ------------------------------------------------------------
    // 数据层兜底
    // ------------------------------------------------------------

    /**
     * 唯一索引冲突。
     *
     * <p>这是并发场景的最后一道防线：
     * 分布式锁没拦住时，靠 {@code uk_*} 唯一索引兜底，把数据库异常翻译成 409。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<Object>> handleDuplicateKey(DuplicateKeyException ex) {
        log.warn("[唯一索引冲突] {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.fail(ResultCode.CONFLICT, "数据已存在，请勿重复提交"));
    }

    // ------------------------------------------------------------
    // 兜底
    // ------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Object>> handleException(Exception ex, HttpServletRequest request) {
        log.error("[系统异常] {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(ResultCode.INTERNAL_ERROR));
    }

    // ------------------------------------------------------------

    private static ResponseEntity<Result<Object>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(ResultCode.BAD_REQUEST.getCode(), message));
    }

    private static String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
