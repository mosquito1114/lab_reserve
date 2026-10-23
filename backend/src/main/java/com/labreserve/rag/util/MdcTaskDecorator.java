package com.labreserve.rag.util;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * {@code @Async} 跨线程 MDC 透传（design.md §11.1 第 5 项）。
 *
 * <p>Spring 的 {@code ThreadPoolTaskExecutor} 在派发任务到工作线程时不会拷贝
 * {@link MDC}，导致 {@code @Async} 内的业务日志丢失 traceId 等诊断字段。
 * 本类作为 {@link TaskDecorator} 注入：在派发瞬间<b>冻结</b>调用线程 MDC，
 * 工作线程进入时套回去，退出时再恢复原状（避免污染下一个共享线程）。
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
 * executor.setTaskDecorator(new MdcTaskDecorator());
 * executor.initialize();
 * }</pre>
 *
 * <p>{@link com.labreserve.rag.config.RagAsyncConfig#ragIngestExecutor(RagProperties)}
 * 已经注入并启用。
 *
 * <h3>为什么不在 finally 写死 MDC.clear()</h3>
 * 工作线程在池里<b>复用</b>，下一次任务可能来自其它线程（其 MDC 与本次不同）。
 * 因此 finally 步骤必须恢复"进入时"的快照，而不是简单的 clear。
 *
 * @author lab_reserve
 */
@Component
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // 派发瞬间冻结调用线程 MDC
        Map<String, String> snapshot = MDC.getCopyOfContextMap();
        return () -> {
            // 工作线程自有 MDC（一般是空的，但理论可能不空），保留以便 finally 恢复
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                if (snapshot != null) {
                    MDC.setContextMap(snapshot);
                } else {
                    MDC.clear();
                }
                runnable.run();
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        };
    }
}
