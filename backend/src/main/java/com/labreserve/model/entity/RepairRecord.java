package com.labreserve.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 维修进度记录（{@code tb_repair_record}）。
 *
 * <p>每次「更新进度」（status=1 维修中）或「维修完成」（status=2 已完成）都会追加一条，
 * 详情页的时间线直接按时间读它。
 */
@Data
@TableName("tb_repair_record")
public class RepairRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final int STATUS_REPAIRING = 1;
    public static final int STATUS_DONE = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long maintainerId;

    /** 1 维修中 / 2 已完成 */
    private Integer status;

    private String repairDesc;

    private String partsReplaced;

    private LocalDateTime createdAt;
}
