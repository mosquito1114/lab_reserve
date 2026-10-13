package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 权限点（勾选树里的叶子节点）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "权限点")
public class PermissionItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @Schema(description = "权限码", example = "reservation:audit")
    private String code;

    @Schema(description = "权限名称", example = "审核预约")
    private String name;
}
