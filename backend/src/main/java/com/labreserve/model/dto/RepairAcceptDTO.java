package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 验收（{@code POST /api/repair_order/accept}）。
 */
@Data
@Schema(description = "验收入参")
public class RepairAcceptDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "工单 ID 不能为空")
    private Long id;

    @Schema(description = "验收结果：PASS 通过 / FAIL 不通过", example = "PASS")
    @NotBlank(message = "验收结果不能为空")
    @Pattern(regexp = "^(PASS|FAIL)$", message = "验收结果只能是 PASS 或 FAIL")
    private String result;

    @Schema(description = "验收意见", example = "测试正常，验收通过")
    @Size(max = 255, message = "验收意见不能超过 255 个字符")
    private String opinion;
}
