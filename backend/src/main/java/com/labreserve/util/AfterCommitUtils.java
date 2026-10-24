package com.labreserve.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 「事务提交后再执行」工具组件。
 *
 * <p>典型用法：审核通过 / 驳回之后给申请人发消息。
 * 消息如果先于事务提交发出，消费者（Redis Stream）可能读不到刚落库的数据；
 * 放到 afterCommit 里执行就不会出现"邮件丢了"。
 *
 * <p>没有活动事务时直接执行，方便在单测或非事务上下文里调用。
 */
@Slf4j
@Component
public class AfterCommitUtils {

    public void run(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (Exception e) {
                    log.error("[afterCommit] 回调执行失败：{}", e.getMessage(), e);
                }
            }
        });
    }
}
