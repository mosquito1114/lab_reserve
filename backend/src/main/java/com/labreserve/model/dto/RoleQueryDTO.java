package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 按角色查询的入参（{@code POST /api/role/get_permission_list}）。
 */
@Data
@Schema(description = "角色 id 入参")
public class RoleQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "角色 ID", example = "4")
    @NotNull(message = "角色 ID 不能为空")
    private Long roleId;
}
