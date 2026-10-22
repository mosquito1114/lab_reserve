package com.labreserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labreserve.model.entity.ResourceUpdateLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资源更新日志 Mapper。
 */
@Mapper
public interface ResourceUpdateLogMapper extends BaseMapper<ResourceUpdateLog> {
}
