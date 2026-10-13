package com.labreserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labreserve.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper。
 *
 * <p>只暴露 {@code insert} 与查询能力——审计日志不允许修改与删除。
 * 复杂查询（多条件筛选）在迭代 1 用 {@code LambdaQueryWrapper} 实现。
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
