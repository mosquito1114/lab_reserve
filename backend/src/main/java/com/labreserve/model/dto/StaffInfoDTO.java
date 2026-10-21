package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理类人员档案入参（新增用户时的 {@code staffInfo}）。
 *
 * <p>系统管理员 / 实验室管理员 / 维修人员共用；类型由用户的 {@code roleCode} 决定，
 * 这里不提交（避免双真相源）。
 */
@Data
@Schema(description = "管理类人员档案入参")
public class StaffInfoDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "工号", example = "M0003")
    @NotBlank(message = "工号不能为空")
    @Size(max = 20, message = "工号长度不能超过 20")
    private String staffNo;

    @Schema(description = "部门", example = "后勤维修科")
    @Size(max = 50, message = "部门名称过长")
    private String department;

    @Schema(description = "维修人员类别：1 校内 / 2 校外（仅维修人员需要）")
    private Integer maintainerType;
}
