package com.labreserve.common.enums;

import lombok.Getter;

/**
 * 消息类型（{@code tb_message.type}）。
 *
 * <p>与 {@code agents/api.md} §8 的类型筛选取值保持一致；
 * 各业务模块发消息时一律用本枚举，不要手写字符串。
 */
@Getter
public enum MessageType {

    /** 预约审核结果（通过 / 驳回） */
    RESERVATION_AUDIT("预约审核", "你的预约申请已审核，点击查看结果"),

    /** 排队顺位变化 */
    QUEUE_POSITION("排队提醒", "你的排队顺位有更新，点击查看当前顺位"),

    /** 轮到候补，限时确认 */
    CANDIDATE_NOTICE("候补通知", "已轮到你候补，请在规定时间内确认"),

    /** 报修工单（派单 / 维修 / 验收） */
    REPAIR("工单通知", "你的报修工单有新进展，点击查看"),

    /** 调整申请审核结果 */
    CHANGE_AUDIT("调整审核", "你的调整申请已审核，点击查看结果"),

    /** 系统通知（公告、账号变更等） */
    SYSTEM("系统通知", "系统通知，点击查看详情"),
    ;

    /** 中文名，用于前端展示与日志 */
    private final String description;

    /** 邮件正文的引导语（站内信 content 才是渲染邮件的唯一来源，这里只作默认补充） */
    private final String mailHint;

    MessageType(String description, String mailHint) {
        this.description = description;
        this.mailHint = mailHint;
    }

    /**
     * 按 code 取枚举，未知返回 {@code null}（落库时校验用，避免脏数据）。
     */
    public static MessageType of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (MessageType type : values()) {
            if (type.name().equalsIgnoreCase(code.trim())) {
                return type;
            }
        }
        return null;
    }
}
