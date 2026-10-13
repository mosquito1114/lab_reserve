package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 当前登录用户信息（{@code POST /api/auth/get_info}）。
 *
 * <p>与登录响应相比多了 {@code menus}：前端刷新页面后靠它重建侧边栏。
 */
@Data
@Schema(description = "当前登录用户信息")
public class CurrentUserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private LoginUserVO user;

    @Schema(description = "权限码集合")
    private List<String> permissions;

    @Schema(description = "可见菜单（服务端按权限过滤）")
    private List<MenuItemVO> menus;
}
