package com.labreserve.infra.sensitive;

import lombok.Getter;

/**
 * 脱敏类型。
 *
 * <p>需求硬约束：手机号必须脱敏展示（{@code 138****1234}）。
 * 其他类型作为通用能力预留。
 */
@Getter
public enum SensitiveType {

    /** 手机号：138****1234 */
    PHONE(3, 4),

    /** 邮箱：abc***@qq.com */
    EMAIL(3, 0),

    /** 姓名：张* */
    NAME(1, 0),

    /** 银行卡 / 长证件号：前 4 后 4 */
    BANK_CARD(4, 4),

    /** 默认：保留首尾各 1 位 */
    DEFAULT(1, 1),
    ;

    /** 保留的前缀长度 */
    private final int prefixKeep;

    /** 保留的后缀长度 */
    private final int suffixKeep;

    SensitiveType(int prefixKeep, int suffixKeep) {
        this.prefixKeep = prefixKeep;
        this.suffixKeep = suffixKeep;
    }
}
