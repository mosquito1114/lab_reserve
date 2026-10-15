package com.labreserve.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labreserve.common.result.Result;
import com.labreserve.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 未登录 / token 失效的统一响应（401）。
 *
 * <p>默认实现会返回 HTML 或重定向到登录页，前后端分离项目不需要；
 * 这里统一输出与业务接口一致的 {@code Result} JSON，前端 axios 拦截器据此跳登录页。
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
                Result.fail(ResultCode.UNAUTHORIZED, "未登录或登录已失效，请重新登录"));
    }
}
