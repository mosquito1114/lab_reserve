package com.labreserve.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 排队禁入记录（{@code tb_queue_ban_record}，7 天）。
 *
 * <p>迭代 5 只做「预检时告知已禁入」，写入禁入记录发生在迭代 6（候补超时/放弃）。
 */
@Data
@TableName("tb_queue_ban_record")
public class QueueBanRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Integer resourceType;

    private Long labId;

    private Long equipmentId;

    private String reason;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime createdAt;
}
