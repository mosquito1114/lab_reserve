package com.labreserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labreserve.model.entity.Reservation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 预约 Mapper。
 */
@Mapper
public interface ReservationMapper extends BaseMapper<Reservation> {
}
