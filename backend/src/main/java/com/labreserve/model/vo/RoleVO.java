package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 角色列表项（{@code POST /api/role/get_list}）。
 */
@Data
@Schema(description = "角色列表项")
public class RoleVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @Schema(description = "角色编码", example = "LAB_ADMIN")
    private String code;

    @Schema(description = "角色名称", example = "实验室管理员")
    private String name;

    private String description;

    @Schema(description = "该角色拥有的权限数")
    private Long permissionCount;

    @Schema(description = "该角色下的用户数")
    private Long userCount;
}
