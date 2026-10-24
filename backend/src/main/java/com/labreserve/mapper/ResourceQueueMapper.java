package com.labreserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labreserve.model.entity.ResourceQueue;
import org.apache.ibatis.annotations.Mapper;

/**
 * 排队记录 Mapper。
 */
@Mapper
public interface ResourceQueueMapper extends BaseMapper<ResourceQueue> {
}
