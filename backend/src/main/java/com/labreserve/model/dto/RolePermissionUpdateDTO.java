package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 保存角色权限（{@code POST /api/role/update_permission}）。
 *
 * <p>全量覆盖式保存：先删该角色的全部关联，再按 {@code permissionIds} 插入。
 * 传空数组表示「取消该角色的所有权限」（系统管理员的关键权限除外，见 {@code PermissionConst.CRITICAL_PERMISSION}）。
 */
@Data
@Schema(description = "保存角色权限入参")
public class RolePermissionUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "角色 ID", example = "4")
    @NotNull(message = "角色 ID 不能为空")
    private Long roleId;

    @Schema(description = "勾选后的权限 ID 列表（全量覆盖）")
    private List<Long> permissionIds;
}
