package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 只带一个 id 的通用入参。
 *
 * <p>本项目的接口一律 POST + JSON body，因此「查详情 / 删一条」这类入参
 * 统一用本类，避免每个模块重复定义结构相同的 DTO。
 */
@Data
@Schema(description = "通用 id 入参")
public class IdDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键 ID")
    @NotNull(message = "id 不能为空")
    private Long id;
}
