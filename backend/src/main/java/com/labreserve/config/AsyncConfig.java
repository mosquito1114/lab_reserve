package com.labreserve.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置。
 *
 * <p>目前唯一用途：操作日志异步落库（{@code infra/oplog}）。
 * 日志写入失败不能影响主业务流程，因此使用独立线程池并单独处理异常。
 */
@Slf4j
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    /** 操作日志专用线程池 */
    public static final String OPLOG_EXECUTOR = "opLogExecutor";

    @Bean(name = OPLOG_EXECUTOR)
    public ThreadPoolTaskExecutor opLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(2000);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("oplog-");
        // 队列满时由调用线程执行，保证日志不会静默丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅关闭：等待队列中的日志写完
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return opLogExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                log.error("[异步任务异常] 方法={}", method.getName(), ex);
    }
}
