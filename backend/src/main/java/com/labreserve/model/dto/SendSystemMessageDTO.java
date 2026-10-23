package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 发送系统通知（{@code POST /api/message/send}）。
 *
 * <p>管理员主动给指定用户发通知，落 {@code tb_message}（类型 {@code SYSTEM}）：
 * 用户开了邮件通知就走邮件队列，没开就只发站内信。
 *
 * <p>后续迭代的「审核结果 / 候补提醒 / 派单」都由业务代码直接调
 * {@code MessageService.send(...)}，不走这个接口。
 */
@Data
@Schema(description = "发送系统通知入参")
public class SendSystemMessageDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "接收人 ID 列表", example = "[5, 6]")
    @NotEmpty(message = "接收人不能为空")
    private List<Long> receiverIds;

    @Schema(description = "标题", example = "实验室开放时间调整通知")
    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题不能超过 100 个字符")
    private String title;

    @Schema(description = "正文", example = "本周六 8:00-12:00 例行维护，暂停开放。")
    @NotBlank(message = "正文不能为空")
    @Size(max = 1000, message = "正文不能超过 1000 个字符")
    private String content;
}
