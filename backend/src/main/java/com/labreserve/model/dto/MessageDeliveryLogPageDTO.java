package com.labreserve.model.dto;

import com.labreserve.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 邮件投递记录分页查询（{@code POST /api/message/get_delivery_log_page}）。
 *
 * <p>只有 {@code message:delivery-log} 角色（系统管理员）能看，
 * 用来排查「站内信到了但邮件没到」的问题。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "邮件投递记录分页查询入参")
public class MessageDeliveryLogPageDTO extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "邮件状态：SENDING 投递中 / OK 成功 / DEAD 死信；不传则全部",
            example = "DEAD")
    private String emailState;

    @Schema(description = "收件人姓名 / 账号模糊匹配", example = "张三")
    private String receiverKeyword;

    @Schema(description = "开始时间", example = "2025-09-01 00:00:00")
    private LocalDateTime beginTime;

    @Schema(description = "结束时间", example = "2025-09-30 23:59:59")
    private LocalDateTime endTime;
}
