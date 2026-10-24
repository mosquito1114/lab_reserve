package com.labreserve.job;

import com.labreserve.service.QueueCandidateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 候补超时扫描（默认每 30s 一次）。
 *
 * <p>扫 {@code lab:reserve:queue:notice:deadline} 里到期的记录 →
 * 通知记录 CAS（0→3）→ 排队状态 CAS（NOTIFIED→TIMEOUT）→ 写 7 天禁入 → 出队 → 顺延下一位。
 *
 * <p>并发安全：多实例部署时两条 CAS 只有一个能成功，失败的一方直接跳过；
 * 真正需要互斥的「顺延」动作在 {@code QueueCandidateService} 里用分布式锁保护。
 *
 * <p>扫描间隔可用 {@code QUEUE_SCAN_INTERVAL_MS} 覆盖，验收时调小即可快速看到超时效果。
 */
@Slf4j
@Component
public class QueueTimeoutJob {

    @Autowired
    private QueueCandidateService queueCandidateService;

    @Scheduled(fixedDelayString = "${lab-reserve.queue.scan-interval-ms:30000}")
    public void scan() {
        try {
            int handled = queueCandidateService.scanTimeout();
            if (handled > 0) {
                log.info("[候补] 本轮处理超时 {} 条", handled);
            }
        } catch (Exception e) {
            log.error("[候补] 超时扫描异常：{}", e.getMessage(), e);
        }
    }
}
