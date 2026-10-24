package com.labreserve.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 排队与候补配置（{@code lab-reserve.queue}）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "lab-reserve.queue")
public class QueueProperties {

    /** 候补确认时限（分钟）：通知发出后多久内必须确认，超时进 7 天禁入 */
    private int candidateTimeoutMinutes = 30;

    /** 候补超时 / 放弃后的禁入天数 */
    private int banDays = 7;

    /** 超时扫描间隔（毫秒），默认 30s */
    private long scanIntervalMs = 30_000L;
}
