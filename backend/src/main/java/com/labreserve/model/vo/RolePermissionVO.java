package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 某角色的权限清单（{@code POST /api/role/get_permission_list}）。
 *
 * <p>前端权限勾选树用它回显已勾选项：{@code permissionIds} 直接喂给 {@code el-tree} 的
 * {@code default-checked-keys}。
 */
@Data
@Schema(description = "角色权限清单")
public class RolePermissionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long roleId;

    private String roleCode;

    private String roleName;

    @Schema(description = "已勾选的权限 ID")
    private List<Long> permissionIds;

    @Schema(description = "已勾选的权限码")
    private List<String> permissions;
}
