package com.labreserve.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预约申请（{@code tb_reservation}）。
 *
 * <p>迭代 4 只用到「占用判定」相关的字段（资源 + 日期 + 时段 + 状态 + 设备数量），
 * 完整状态机与业务校验在迭代 5 落地。
 *
 * <p>注意：表里还有一个生成列 {@code occupy_key}（并发兜底唯一索引），
 * 它由数据库自己维护，实体里不映射。
 */
@Data
@TableName("tb_reservation")
public class Reservation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 占用资源的状态集合（冲突判定与库存计算都按它） */
    public static final String[] OCCUPYING_STATUS = {"PENDING", "APPROVED", "CHANGE_PENDING"};

    @TableId(type = IdType.AUTO)
    private Long id;

    private String reserveNo;

    private Long applicantId;

    /** 1 实验室 / 2 设备 */
    private Integer resourceType;

    private Long labId;

    private Long equipmentId;

    private Integer equipmentCount;

    private LocalDate reserveDate;

    private Long slotId;

    private Integer usePeople;

    private Long experimentTypeId;

    private String courseName;

    private String purpose;

    private String status;

    private Long auditUserId;

    private LocalDateTime auditTime;

    private String rejectReason;

    private Integer fromQueue;

    private Long sourceQueueId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
