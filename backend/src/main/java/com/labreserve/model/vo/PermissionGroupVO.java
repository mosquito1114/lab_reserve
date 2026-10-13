package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 权限勾选树的分组（{@code POST /api/permission/get_list}）。
 *
 * <p>按 {@code tb_permission.module} 分组，前端 Element Plus 的 {@code el-tree} 直接渲染。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "权限分组")
public class PermissionGroupVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "模块编码", example = "reservation")
    private String module;

    @Schema(description = "模块中文名", example = "预约管理")
    private String moduleName;

    private List<PermissionItemVO> items;
}
