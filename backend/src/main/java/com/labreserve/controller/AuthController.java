package com.labreserve.controller;

import com.labreserve.common.annotation.OpLog;
import com.labreserve.common.enums.OperationModule;
import com.labreserve.common.enums.OperationType;
import com.labreserve.common.result.Result;
import com.labreserve.model.dto.LoginDTO;
import com.labreserve.model.dto.RefreshTokenDTO;
import com.labreserve.model.dto.UpdatePasswordDTO;
import com.labreserve.model.vo.CurrentUserVO;
import com.labreserve.model.vo.LoginVO;
import com.labreserve.security.JwtAuthenticationFilter;
import com.labreserve.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（{@code api.md} §1）。
 *
 * <p>白名单只有 {@code login} 与 {@code refresh}（见 {@code SecurityConfig}），
 * 其余三个接口都要求已登录。
 *
 * <p>操作日志：登录（成功与失败都记）、登出、改密都会落 {@code tb_operation_log}；
 * 这三个接口的入参含密码，因此 {@code saveParam = false} 不记录请求参数。
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "01-认证")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Operation(summary = "登录", description = "账号密码登录；连续失败 5 次锁定 15 分钟")
    @OpLog(module = OperationModule.AUTH, type = OperationType.LOGIN,
            content = "用户登录", saveParam = false)
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody @Valid LoginDTO dto) {
        return Result.ok(authService.login(dto));
    }

    @Operation(summary = "刷新 accessToken", description = "用 refreshToken 换新的 accessToken")
    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@RequestBody @Valid RefreshTokenDTO dto) {
        return Result.ok(authService.refresh(dto.getRefreshToken()));
    }

    @Operation(summary = "登出", description = "当前 accessToken 立即失效（写入黑名单）")
    @OpLog(module = OperationModule.AUTH, type = OperationType.LOGOUT,
            content = "退出登录", saveParam = false)
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        authService.logout(JwtAuthenticationFilter.resolveToken(request));
        return Result.ok();
    }

    @Operation(summary = "当前用户信息", description = "用户信息 + 权限码 + 菜单，前端刷新页面后调用")
    @PostMapping("/get_info")
    public Result<CurrentUserVO> getInfo() {
        return Result.ok(authService.getCurrentUser());
    }

    @Operation(summary = "修改密码", description = "改密成功后旧 token 全部失效，需要重新登录")
    @OpLog(module = OperationModule.AUTH, type = OperationType.UPDATE,
            content = "修改密码", saveParam = false)
    @PostMapping("/update_password")
    public Result<Void> updatePassword(@RequestBody @Valid UpdatePasswordDTO dto,
                                      HttpServletRequest request) {
        authService.updatePassword(dto, JwtAuthenticationFilter.resolveToken(request));
        return Result.ok();
    }
}
