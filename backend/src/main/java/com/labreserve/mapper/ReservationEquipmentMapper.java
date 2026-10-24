package com.labreserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labreserve.model.entity.ReservationEquipment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 预约附带设备 Mapper。
 */
@Mapper
public interface ReservationEquipmentMapper extends BaseMapper<ReservationEquipment> {
}
