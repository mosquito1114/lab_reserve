package com.labreserve.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labreserve.common.exception.BizException;
import com.labreserve.mapper.ReservationMapper;
import com.labreserve.mapper.TimeSlotMapper;
import com.labreserve.model.dto.DictQueryDTO;
import com.labreserve.model.dto.TimeSlotSaveDTO;
import com.labreserve.model.entity.Reservation;
import com.labreserve.model.entity.TimeSlot;
import com.labreserve.model.vo.TimeSlotVO;
import com.labreserve.service.TimeSlotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 固定时段字典实现。
 */
@Slf4j
@Service
public class TimeSlotServiceImpl implements TimeSlotService {

    @Autowired
    private TimeSlotMapper timeSlotMapper;

    @Autowired
    private ReservationMapper reservationMapper;

    @Override
    public List<TimeSlotVO> list(DictQueryDTO dto) {
        List<TimeSlot> list = timeSlotMapper.selectList(Wrappers.<TimeSlot>lambdaQuery()
                .eq(dto != null && dto.getStatus() != null, TimeSlot::getStatus, dto == null ? null : dto.getStatus())
                .orderByAsc(TimeSlot::getSort)
                .orderByAsc(TimeSlot::getId));
        return list.stream().map(TimeSlotServiceImpl::toVO).toList();
    }

    @Override
    public Long create(TimeSlotSaveDTO dto) {
        requireValidRange(dto);
        requireUniqueCode(dto.getCode(), null);

        TimeSlot entity = new TimeSlot();
        entity.setCode(dto.getCode().trim().toUpperCase());
        entity.setName(dto.getName().trim());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setSort(dto.getSort() == null ? 0 : dto.getSort());
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        timeSlotMapper.insert(entity);
        log.info("[字典] 新增时段 id={}, code={}", entity.getId(), entity.getCode());
        return entity.getId();
    }

    @Override
    public void update(TimeSlotSaveDTO dto) {
        if (dto.getId() == null) {
            throw BizException.badRequest("时段 ID 不能为空");
        }
        if (timeSlotMapper.selectById(dto.getId()) == null) {
            throw BizException.notFound("时段不存在");
        }
        requireValidRange(dto);
        requireUniqueCode(dto.getCode(), dto.getId());

        TimeSlot entity = new TimeSlot();
        entity.setId(dto.getId());
        entity.setCode(dto.getCode().trim().toUpperCase());
        entity.setName(dto.getName().trim());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setSort(dto.getSort());
        entity.setStatus(dto.getStatus());
        timeSlotMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        if (id == null) {
            throw BizException.badRequest("时段 ID 不能为空");
        }
        if (timeSlotMapper.selectById(id) == null) {
            throw BizException.notFound("时段不存在");
        }
        long reserveCount = reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getSlotId, id));
        if (reserveCount > 0) {
            throw BizException.conflict("该时段已被 " + reserveCount + " 条预约使用，不能删除，可改为停用");
        }
        timeSlotMapper.deleteById(id);
        log.info("[字典] 删除时段 id={}", id);
    }

    private void requireValidRange(TimeSlotSaveDTO dto) {
        if (!dto.getEndTime().isAfter(dto.getStartTime())) {
            throw BizException.badRequest("结束时间必须晚于开始时间");
        }
    }

    private void requireUniqueCode(String code, Long excludeId) {
        if (!StringUtils.hasText(code)) {
            throw BizException.badRequest("编码不能为空");
        }
        long count = timeSlotMapper.selectCount(Wrappers.<TimeSlot>lambdaQuery()
                .eq(TimeSlot::getCode, code.trim().toUpperCase())
                .ne(excludeId != null, TimeSlot::getId, excludeId));
        if (count > 0) {
            throw BizException.conflict("时段编码已存在：" + code.trim().toUpperCase());
        }
    }

    private static TimeSlotVO toVO(TimeSlot entity) {
        TimeSlotVO vo = new TimeSlotVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setStartTime(entity.getStartTime());
        vo.setEndTime(entity.getEndTime());
        vo.setSort(entity.getSort());
        vo.setStatus(entity.getStatus());
        return vo;
    }
}
