package com.labreserve.enums;

import lombok.Getter;

/**
 * 操作所属模块（{@code tb_operation_log.module}）。
 *
 * <p>与 {@code agents/api.md} §10.3 的筛选取值保持一致。
 */
@Getter
public enum OperationModule {

    AUTH("auth", "认证"),
    USER("user", "用户管理"),
    RESOURCE("resource", "资源管理"),
    RESERVATION("reservation", "预约管理"),
    QUEUE("queue", "排队候补"),
    REPAIR("repair", "设备运维"),
    MESSAGE("message", "消息通知"),
    STATISTICS("statistics", "数据统计"),
    SYSTEM("system", "系统管理"),
    ;

    /** 存入数据库的编码 */
    private final String code;
    private final String description;

    OperationModule(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
