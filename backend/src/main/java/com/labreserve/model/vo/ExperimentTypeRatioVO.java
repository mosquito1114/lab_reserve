package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 实验类型占比。
 */
@Data
@Schema(description = "实验类型占比")
public class ExperimentTypeRatioVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long total;

    private List<Item> items;

    @Data
    @Schema(description = "实验类型占比项")
    public static class Item implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long experimentTypeId;
        private String code;
        private String name;
        private Long count;
        @Schema(description = "占比（0~1）")
        private Double ratio;
    }
}
