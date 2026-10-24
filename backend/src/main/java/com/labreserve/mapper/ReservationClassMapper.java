package com.labreserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labreserve.model.entity.ReservationClass;
import org.apache.ibatis.annotations.Mapper;

/**
 * 预约上课班级 Mapper。
 */
@Mapper
public interface ReservationClassMapper extends BaseMapper<ReservationClass> {
}
