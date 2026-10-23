package com.labreserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labreserve.model.entity.TimeSlot;
import org.apache.ibatis.annotations.Mapper;

/**
 * 固定时段 Mapper。
 */
@Mapper
public interface TimeSlotMapper extends BaseMapper<TimeSlot> {
}
