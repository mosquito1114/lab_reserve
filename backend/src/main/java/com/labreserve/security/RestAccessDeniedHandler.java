package com.labreserve.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labreserve.common.result.Result;
import com.labreserve.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 已登录但无权限的统一响应（403）。
 *
 * <p>注意：业务权限校验走的是 {@code @RequirePerm} + AOP，抛的是 {@code BizException(403)}，
 * 由 {@code GlobalExceptionHandler} 处理；本类只兜底 Spring Security 自身抛出的拒绝
 * （例如后续用 {@code @PreAuthorize}、或 URL 级规则拦截）。
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
                Result.fail(ResultCode.FORBIDDEN, "无操作权限"));
    }
}
