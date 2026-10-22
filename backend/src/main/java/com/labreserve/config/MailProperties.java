package com.labreserve.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 邮件投递配置（{@code application.yml} 的 {@code lab-reserve.mail}）。
 *
 * <p>SMTP 服务器与账号密码不在这里，走 Spring 标准的 {@code spring.mail.*}
 * （值来自 {@code .env} 的 {@code MAIL_*}，凭据不进 Git）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "lab-reserve.mail")
public class MailProperties {

    /** 发件人显示名 */
    private String fromName = "实验室预约管理系统";

    /** 最大重试次数，超过即进死信队列 */
    private int maxRetry = 3;

    /** 消费轮询间隔（毫秒），只影响「新消息多久被消费」 */
    private long pollIntervalMs = 2000L;

    /** 重投扫描间隔（毫秒），PEL 中消息的巡检周期 */
    private long reclaimIntervalMs = 30_000L;

    /** 重投最小空闲时间（秒）：消息在 PEL 里闲置超过这个时长才会被重新认领 */
    private long retryMinIdleSeconds = 60L;

    /** 未入队兜底扫描的延迟（秒）：消息落库超过这个时长仍为「未入队」就补投一次 */
    private long enqueueGraceSeconds = 60L;
}
