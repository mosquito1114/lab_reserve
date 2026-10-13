package com.labreserve.service.impl;

import com.labreserve.config.AsyncConfig;
import com.labreserve.model.entity.OperationLog;
import com.labreserve.mapper.OperationLogMapper;
import com.labreserve.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 操作日志服务实现。
 *
 * <p>写入走独立线程池（{@link AsyncConfig#OPLOG_EXECUTOR}），
 * 且内部吞掉所有异常——日志是"旁路"，绝不能因为它失败而回滚业务事务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogMapper operationLogMapper;

    @Async(AsyncConfig.OPLOG_EXECUTOR)
    @Override
    public void record(OperationLog operationLog) {
        if (operationLog == null) {
            return;
        }
        try {
            operationLogMapper.insert(operationLog);
        } catch (Exception e) {
            log.error("[操作日志] 写入失败，module={}, type={}, content={}",
                    operationLog.getModule(), operationLog.getOperationType(),
                    operationLog.getContent(), e);
        }
    }
}
