package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 发送测试邮件（{@code POST /api/message/send_test_email}）。
 *
 * <p>联调用：不落 {@code tb_message}，直接走 SMTP 发一封，
 * 用来确认 {@code .env} 里的邮箱账号 / 授权码是否可用。
 */
@Data
@Schema(description = "发送测试邮件入参")
public class SendTestEmailDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "收件人邮箱", example = "test@qq.com")
    @NotBlank(message = "收件人邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String to;
}
