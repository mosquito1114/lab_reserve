package com.labreserve.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 「事务提交后再执行」工具组件。
 *
 * <p>典型用法：审核通过 / 驳回之后给申请人发消息、释放时段后触发候补顺延。
 * 消息如果先于事务提交发出，Redis Stream 消费者可能读不到刚落库的数据；
 * 放到 afterCommit 里执行就不会出现"邮件丢了"。
 *
 * <p><b>嵌套调用的坑</b>：在 afterCommit 回调里再次调用本方法时，
 * Spring 仍认为"同步器是激活的"（要等 afterCompletion 才清理），但此时**新注册的
 * 同步器永远不会被触发**，动作会静默丢失。因此这里用 ThreadLocal 标记当前线程是否
 * 正在执行 afterCommit 回调：是则立即执行，避免"候补通知悄悄不发"。
 *
 * <p>没有活动事务时直接执行，方便单测或非事务上下文调用。
 */
@Slf4j
@Component
public class AfterCommitUtils {

    /** 当前线程正在执行的 afterCommit 回调层数 */
    private static final ThreadLocal<Integer> IN_AFTER_COMMIT = ThreadLocal.withInitial(() -> 0);

    public void run(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive() || IN_AFTER_COMMIT.get() > 0) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                IN_AFTER_COMMIT.set(IN_AFTER_COMMIT.get() + 1);
                try {
                    action.run();
                } catch (Exception e) {
                    log.error("[afterCommit] 回调执行失败：{}", e.getMessage(), e);
                } finally {
                    int depth = IN_AFTER_COMMIT.get() - 1;
                    if (depth <= 0) {
                        IN_AFTER_COMMIT.remove();
                    } else {
                        IN_AFTER_COMMIT.set(depth);
                    }
                }
            }
        });
    }
}
