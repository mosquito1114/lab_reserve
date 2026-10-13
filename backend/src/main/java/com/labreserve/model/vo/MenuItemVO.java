package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 侧边栏菜单项。
 *
 * <p>菜单由服务端按「当前角色拥有的权限码」过滤后下发，
 * 前端只负责渲染（{@code MainLayout.vue}），不在前端重复写权限判断。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "菜单项")
public class MenuItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "前端路由路径", example = "/system/role")
    private String path;

    @Schema(description = "菜单标题", example = "角色权限")
    private String title;

    @Schema(description = "Element Plus 图标组件名", example = "Lock")
    private String icon;

    @Schema(description = "子菜单；为空表示叶子节点")
    private List<MenuItemVO> children;
}
