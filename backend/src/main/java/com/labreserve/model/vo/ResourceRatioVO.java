package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 资源预约占比。
 */
@Data
@Schema(description = "资源预约占比")
public class ResourceRatioVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<Item> items;

    @Data
    @Schema(description = "资源预约占比项")
    public static class Item implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Integer resourceType;
        private Long resourceId;
        private String resourceNo;
        private String resourceName;
        private Long count;
        private Double ratio;
    }
}
