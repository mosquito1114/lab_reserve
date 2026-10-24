package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 维修人更新进度 / 完成维修（{@code update_progress} 与 {@code complete} 共用）。
 */
@Data
@Schema(description = "维修进度入参")
public class RepairProgressDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "工单 ID 不能为空")
    private Long id;

    @Schema(description = "维修说明（complete 时必填）", example = "已定位为主板电容损坏，等待配件")
    @Size(max = 1000, message = "维修说明不能超过 1000 个字符")
    private String repairDesc;

    @Schema(description = "配件更换记录（complete 时可填）", example = "电容 25V/470uF × 2")
    @Size(max = 500, message = "配件记录不能超过 500 个字符")
    private String partsReplaced;
}
