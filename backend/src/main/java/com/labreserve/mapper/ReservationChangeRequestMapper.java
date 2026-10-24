package com.labreserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labreserve.model.entity.ReservationChangeRequest;
import org.apache.ibatis.annotations.Mapper;

/**
 * 预约调整申请 Mapper。
 */
@Mapper
public interface ReservationChangeRequestMapper extends BaseMapper<ReservationChangeRequest> {
}
