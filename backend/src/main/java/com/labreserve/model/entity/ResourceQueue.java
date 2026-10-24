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
 * 排队记录（{@code tb_resource_queue}）。
 *
 * <p>DB 只负责持久化与业务字段（谁、哪个资源、什么状态、什么时候入队），
 * <b>顺位不落库</b>：一律用 Redis ZSet 的 {@code ZRANK + 1} 实时算，
 * 避免「DB 里的 position 与队列不一致」。
 *
 * <p>状态机：{@code QUEUING → NOTIFIED → CONFIRMED / ABANDONED / TIMEOUT}，
 * {@code QUEUING → CANCELED}（自己取消，不写禁入）。
 */
@Data
@TableName("tb_resource_queue")
public class ResourceQueue implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String QUEUING = "QUEUING";
    public static final String NOTIFIED = "NOTIFIED";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String ABANDONED = "ABANDONED";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String CANCELED = "CANCELED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 排队号：P-资源编号-YYYYMMDD-序号 */
    private String queueNo;

    /** 1 实验室 / 2 设备 */
    private Integer resourceType;

    private Long labId;

    private Long equipmentId;

    private LocalDate reserveDate;

    private Long slotId;

    private Long applicantId;

    /** QUEUING / NOTIFIED / CONFIRMED / ABANDONED / TIMEOUT / CANCELED */
    private String status;

    /** 入队时间（与 Redis ZSet 的 score 一致） */
    private LocalDateTime joinTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 该排队记录的主资源 ID（实验室或设备） */
    public Long mainResourceId() {
        return Integer.valueOf(1).equals(resourceType) ? labId : equipmentId;
    }
}
