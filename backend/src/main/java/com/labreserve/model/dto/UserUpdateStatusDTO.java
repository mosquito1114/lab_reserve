package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 注销 / 恢复用户（{@code POST /api/user/update_status}）。
 *
 * <p>不物理删除：只把 {@code status} 置 0，历史日志与业务引用继续有效。
 */
@Data
@Schema(description = "修改用户状态入参")
public class UserUpdateStatusDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "用户 ID")
    @NotNull(message = "用户 ID 不能为空")
    private Long id;

    @Schema(description = "1 正常 / 0 已注销")
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态只能是 0 或 1")
    @Max(value = 1, message = "状态只能是 0 或 1")
    private Integer status;

    @Schema(description = "原因（会写入操作日志）", example = "学生已毕业")
    @Size(max = 200, message = "原因过长")
    private String reason;
}
