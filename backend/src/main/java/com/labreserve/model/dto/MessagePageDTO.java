package com.labreserve.model.dto;

import com.labreserve.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 收件箱分页查询（{@code POST /api/message/get_page}）。
 *
 * <p>对应 {@code api.md} §8：分页从 Redis ZSet 取消息 ID，再批量查库。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "收件箱分页查询入参")
public class MessagePageDTO extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "消息类型：RESERVATION_AUDIT / QUEUE_POSITION / CANDIDATE_NOTICE / REPAIR / CHANGE_AUDIT / SYSTEM",
            example = "RESERVATION_AUDIT")
    private String type;

    @Schema(description = "是否已读：0 未读 / 1 已读；不传则全部", example = "0")
    private Integer isRead;

    @Schema(description = "开始时间", example = "2025-09-01 00:00:00")
    private LocalDateTime beginTime;

    @Schema(description = "结束时间", example = "2025-09-30 23:59:59")
    private LocalDateTime endTime;
}
