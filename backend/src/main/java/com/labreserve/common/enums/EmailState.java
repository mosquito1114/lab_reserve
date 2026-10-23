package com.labreserve.common.enums;

import lombok.Getter;

/**
 * 邮件投递状态（{@code tb_message.email_state}）。
 *
 * <pre>
 * NULL（未入队） → SENDING（已入队，投递中） → OK（发送成功）
 *                                        └→ DEAD（重试 3 次仍失败，已进死信）
 * </pre>
 *
 * <p>只有 {@link #OK} / {@link #DEAD} 是终态，由 Stream 消费者回写；
 * 列宽 10 字符，枚举名最长 7 个字符（SENDING），无需迁移。
 */
@Getter
public enum EmailState {

    /** 已入队，消息在 Stream / PEL 中尚未发送成功 */
    SENDING("投递中"),

    /** SMTP 发送成功（终态） */
    OK("发送成功"),

    /** 重试次数用尽，已转入死信队列（终态） */
    DEAD("死信");

    private final String description;

    EmailState(String description) {
        this.description = description;
    }
}
