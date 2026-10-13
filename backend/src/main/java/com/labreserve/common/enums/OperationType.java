package com.labreserve.common.enums;

import lombok.Getter;

/**
 * 操作类型（{@code tb_operation_log.operation_type}）。
 *
 * <p>与 {@code agents/api.md} §10.3 的筛选取值保持一致。
 */
@Getter
public enum OperationType {

    LOGIN("登录"),
    LOGOUT("登出"),
    CREATE("新增"),
    UPDATE("修改"),
    DELETE("删除"),
    AUDIT("审核"),
    EXPORT("导出"),
    ASSIGN("派单"),
    ACCEPT("验收"),
    UPLOAD("上传"),
    /** 查看完整手机号（脱敏例外，必须留痕） */
    QUERY_PHONE("查看完整手机号"),
    OTHER("其他"),
    ;

    private final String description;

    OperationType(String description) {
        this.description = description;
    }
}
