package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理类人员档案（用户详情里的 {@code staffInfo}）。
 *
 * <p>系统管理员 / 实验室管理员 / 维修人员共用；区分靠 {@code roleCode}。
 */
@Data
@Schema(description = "管理类人员档案")
public class StaffInfoVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "工号")
    private String staffNo;

    @Schema(description = "部门")
    private String department;

    @Schema(description = "维修人员类别：1 校内 / 2 校外（仅维修人员有值）")
    private Integer maintainerType;

    @Schema(description = "维修人员类别名称")
    private String maintainerTypeName;
}
