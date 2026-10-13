package com.labreserve.service;

import com.labreserve.model.entity.OperationLog;

/**
 * 操作日志服务。
 *
 * <p>只提供「写入」能力。查询能力（多条件分页 / 详情 / 导出）
 * 在迭代 1 随操作日志模块一起补齐。
 *
 * <p>审计要求：<b>只增不改不删</b>，因此这里没有 update / delete 方法。
 */
public interface OperationLogService {

    /**
     * 异步落库一条操作日志。
     *
     * <p>调用方（{@code OpLogAspect}）不关心结果——日志写失败绝不能影响业务。
     *
     * @param operationLog 已组装好的日志对象
     */
    void record(OperationLog operationLog);
}
