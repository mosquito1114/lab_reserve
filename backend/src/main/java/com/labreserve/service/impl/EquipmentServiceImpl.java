package com.labreserve.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labreserve.common.constant.RedisKeyConst;
import com.labreserve.common.exception.BizException;
import com.labreserve.common.result.PageResult;
import com.labreserve.mapper.EquipmentMapper;
import com.labreserve.mapper.LaboratoryMapper;
import com.labreserve.mapper.ReservationMapper;
import com.labreserve.mapper.TimeSlotMapper;
import com.labreserve.model.dto.AvailabilityQueryDTO;
import com.labreserve.model.dto.EquipmentOptionQueryDTO;
import com.labreserve.model.dto.EquipmentPageDTO;
import com.labreserve.model.dto.EquipmentSaveDTO;
import com.labreserve.model.dto.EquipmentStatusDTO;
import com.labreserve.model.entity.Equipment;
import com.labreserve.model.entity.Laboratory;
import com.labreserve.model.entity.Reservation;
import com.labreserve.model.entity.TimeSlot;
import com.labreserve.model.vo.EquipmentAvailabilityVO;
import com.labreserve.model.vo.EquipmentOptionVO;
import com.labreserve.model.vo.EquipmentSlotAvailabilityVO;
import com.labreserve.model.vo.EquipmentVO;
import com.labreserve.service.EquipmentService;
import com.labreserve.service.ResourceLogService;
import com.labreserve.util.RedisUtils;
import com.labreserve.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 设备服务实现。
 */
@Slf4j
@Service
public class EquipmentServiceImpl implements EquipmentService {

    @Autowired
    private EquipmentMapper equipmentMapper;

    @Autowired
    private LaboratoryMapper laboratoryMapper;

    @Autowired
    private ReservationMapper reservationMapper;

    @Autowired
    private TimeSlotMapper timeSlotMapper;

    @Autowired
    private ResourceLogService resourceLogService;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private SecurityUtils securityUtils;

    // ============================================================
    // 查询
    // ============================================================

    @Override
    public PageResult<EquipmentVO> page(EquipmentPageDTO dto) {
        LambdaQueryWrapper<Equipment> wrapper = Wrappers.<Equipment>lambdaQuery()
                .eq(dto.getLabId() != null, Equipment::getLabId, dto.getLabId())
                .eq(dto.getStatus() != null, Equipment::getStatus, dto.getStatus())
                .like(StringUtils.hasText(dto.getResourceNo()), Equipment::getResourceNo, dto.getResourceNo())
                .like(StringUtils.hasText(dto.getModel()), Equipment::getModel, dto.getModel())
                .and(StringUtils.hasText(dto.getKeyword()), condition -> condition
                        .like(Equipment::getName, dto.getKeyword())
                        .or().like(Equipment::getModel, dto.getKeyword())
                        .or().like(Equipment::getResourceNo, dto.getKeyword()))
                .orderByAsc(Equipment::getLabId)
                .orderByAsc(Equipment::getResourceNo);

        Page<Equipment> page = equipmentMapper.selectPage(dto.toMpPage(), wrapper);
        Map<Long, Laboratory> labs = loadLabs(page.getRecords());
        return PageResult.of(page, entity -> toVO(entity, labs.get(entity.getLabId())));
    }

    @Override
    public EquipmentVO getInfo(Long id) {
        Equipment equipment = requireExists(id);
        return toVO(equipment, laboratoryMapper.selectById(equipment.getLabId()));
    }

    @Override
    public List<EquipmentOptionVO> listOption(EquipmentOptionQueryDTO dto) {
        boolean onlyAvailable = dto == null || dto.getOnlyAvailable() == null || Boolean.TRUE.equals(dto.getOnlyAvailable());
        List<Equipment> list = equipmentMapper.selectList(Wrappers.<Equipment>lambdaQuery()
                .eq(dto != null && dto.getLabId() != null, Equipment::getLabId, dto == null ? null : dto.getLabId())
                .eq(onlyAvailable, Equipment::getStatus, 1)
                .orderByAsc(Equipment::getResourceNo));
        return list.stream().map(entity -> {
            EquipmentOptionVO vo = new EquipmentOptionVO();
            vo.setId(entity.getId());
            vo.setResourceNo(entity.getResourceNo());
            vo.setName(entity.getName());
            vo.setModel(entity.getModel());
            vo.setStock(entity.getStock());
            vo.setLabId(entity.getLabId());
            vo.setStatus(entity.getStatus());
            return vo;
        }).toList();
    }

    // ============================================================
    // 写操作（全部留痕）
    // ============================================================

    @Override
    public Long create(EquipmentSaveDTO dto) {
        validate(dto);
        requireUniqueResourceNo(dto.getResourceNo(), null);

        Equipment entity = new Equipment();
        apply(entity, dto);
        entity.setStatus(1);
        equipmentMapper.insert(entity);

        resourceLogService.record(ResourceLogService.TYPE_EQUIPMENT, entity.getId(), ResourceLogService.OP_CREATE,
                null, snapshot(entity), dto.getUpdateReason());
        log.info("[设备] 新增 id={}, resourceNo={}", entity.getId(), entity.getResourceNo());
        return entity.getId();
    }

    @Override
    public void update(EquipmentSaveDTO dto) {
        if (dto.getId() == null) {
            throw BizException.badRequest("设备 ID 不能为空");
        }
        Equipment exists = requireExists(dto.getId());
        validate(dto);
        requireUniqueResourceNo(dto.getResourceNo(), dto.getId());

        Equipment entity = new Equipment();
        entity.setId(dto.getId());
        apply(entity, dto);
        entity.setNextCalibrationDate(dto.getNextCalibrationDate());
        entity.setRemark(dto.getRemark());
        equipmentMapper.updateById(entity);

        Equipment latest = equipmentMapper.selectById(dto.getId());
        resourceLogService.record(ResourceLogService.TYPE_EQUIPMENT, dto.getId(), ResourceLogService.OP_UPDATE,
                snapshot(exists), snapshot(latest == null ? entity : latest), dto.getUpdateReason());
        log.info("[设备] 修改 id={}", dto.getId());
    }

    @Override
    public void updateStatus(EquipmentStatusDTO dto) {
        Equipment exists = requireExists(dto.getId());
        if (Objects.equals(exists.getStatus(), dto.getStatus())) {
            throw BizException.conflict("设备已经是该状态，无需重复操作");
        }
        Equipment update = new Equipment();
        update.setId(dto.getId());
        update.setStatus(dto.getStatus());
        equipmentMapper.updateById(update);

        Equipment latest = equipmentMapper.selectById(dto.getId());
        resourceLogService.record(ResourceLogService.TYPE_EQUIPMENT, dto.getId(), ResourceLogService.OP_STATUS,
                snapshot(exists), snapshot(latest == null ? update : latest), dto.getUpdateReason());
        log.info("[设备] 状态变更 id={}, status={}", dto.getId(), dto.getStatus());
    }

    // ============================================================
    // 可用量
    // ============================================================

    @Override
    public EquipmentAvailabilityVO availability(AvailabilityQueryDTO dto) {
        Equipment equipment = requireExists(dto.getId());
        LocalDate date = dto.getDate();

        LambdaQueryWrapper<TimeSlot> slotWrapper = Wrappers.<TimeSlot>lambdaQuery()
                .eq(TimeSlot::getStatus, 1)
                .in(dto.getSlotIds() != null && !dto.getSlotIds().isEmpty(), TimeSlot::getId, dto.getSlotIds())
                .orderByAsc(TimeSlot::getSort)
                .orderByAsc(TimeSlot::getId);
        List<TimeSlot> slots = timeSlotMapper.selectList(slotWrapper);

        // 已占用的数量（按 slot 分组求和）
        List<Reservation> reservations = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getEquipmentId, equipment.getId())
                .eq(Reservation::getReserveDate, date)
                .in(Reservation::getStatus, (Object[]) Reservation.OCCUPYING_STATUS));
        Map<Long, Integer> usedBySlot = new HashMap<>();
        for (Reservation reservation : reservations) {
            if (reservation.getSlotId() == null) {
                continue;
            }
            int count = reservation.getEquipmentCount() == null ? 1 : reservation.getEquipmentCount();
            usedBySlot.merge(reservation.getSlotId(), count, Integer::sum);
        }

        Long userId = securityUtils.getUserId();
        int stock = equipment.getStock() == null ? 0 : equipment.getStock();
        boolean usable = Integer.valueOf(1).equals(equipment.getStatus());

        List<EquipmentSlotAvailabilityVO> slotVOs = slots.stream().map(slot -> {
            EquipmentSlotAvailabilityVO vo = new EquipmentSlotAvailabilityVO();
            vo.setSlotId(slot.getId());
            vo.setSlotName(slot.getName());
            vo.setStartTime(slot.getStartTime());
            vo.setEndTime(slot.getEndTime());
            int used = usedBySlot.getOrDefault(slot.getId(), 0);
            int remain = Math.max(0, stock - used);
            vo.setUsedCount(used);
            vo.setRemainCount(remain);
            vo.setAvailable(usable && remain > 0);

            String queueKey = RedisKeyConst.queueKey(2, equipment.getId(), date, slot.getId());
            vo.setQueueCount(redisUtils.zCard(queueKey));
            if (userId != null) {
                long rank = redisUtils.zRank(queueKey, String.valueOf(userId));
                vo.setMyQueuePosition(rank < 0 ? null : (int) rank + 1);
            }
            return vo;
        }).toList();

        EquipmentAvailabilityVO vo = new EquipmentAvailabilityVO();
        vo.setEquipmentId(equipment.getId());
        vo.setResourceNo(equipment.getResourceNo());
        vo.setName(equipment.getName());
        vo.setStock(stock);
        vo.setDate(date);
        vo.setSlots(slotVOs);
        return vo;
    }

    // ============================================================
    // 内部工具
    // ============================================================

    private Equipment requireExists(Long id) {
        if (id == null) {
            throw BizException.badRequest("设备 ID 不能为空");
        }
        Equipment equipment = equipmentMapper.selectById(id);
        if (equipment == null) {
            throw BizException.notFound("设备不存在");
        }
        return equipment;
    }

    private void validate(EquipmentSaveDTO dto) {
        Laboratory lab = laboratoryMapper.selectById(dto.getLabId());
        if (lab == null) {
            throw BizException.badRequest("所属实验室不存在");
        }
    }

    private void requireUniqueResourceNo(String resourceNo, Long excludeId) {
        long count = equipmentMapper.selectCount(Wrappers.<Equipment>lambdaQuery()
                .eq(Equipment::getResourceNo, resourceNo.trim().toUpperCase())
                .ne(excludeId != null, Equipment::getId, excludeId));
        if (count > 0) {
            throw BizException.conflict("资源编号已存在：" + resourceNo.trim().toUpperCase());
        }
    }

    private void apply(Equipment entity, EquipmentSaveDTO dto) {
        entity.setResourceNo(dto.getResourceNo().trim().toUpperCase());
        entity.setName(dto.getName().trim());
        entity.setModel(dto.getModel());
        entity.setStock(dto.getStock());
        entity.setLabId(dto.getLabId());
        entity.setNextCalibrationDate(dto.getNextCalibrationDate());
        entity.setRemark(dto.getRemark());
    }

    private Map<Long, Laboratory> loadLabs(List<Equipment> equipments) {
        Set<Long> ids = equipments.stream().map(Equipment::getLabId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return laboratoryMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Laboratory::getId, item -> item));
    }

    private EquipmentVO toVO(Equipment entity, Laboratory lab) {
        EquipmentVO vo = new EquipmentVO();
        vo.setId(entity.getId());
        vo.setResourceNo(entity.getResourceNo());
        vo.setName(entity.getName());
        vo.setModel(entity.getModel());
        vo.setStock(entity.getStock());
        vo.setLabId(entity.getLabId());
        vo.setLabNo(lab == null ? null : lab.getLabNo());
        vo.setLabName(lab == null ? null : lab.getName());
        vo.setStatus(entity.getStatus());
        vo.setStatusName(statusName(entity.getStatus()));
        vo.setNextCalibrationDate(entity.getNextCalibrationDate());
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    private String statusName(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case 1 -> "正常";
            case 2 -> "维修中";
            case 3 -> "报废";
            case 0 -> "停用";
            default -> "未知";
        };
    }

    /** 快照：业务字段，日期转字符串便于前端 diff */
    private Map<String, Object> snapshot(Equipment equipment) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("resourceNo", equipment.getResourceNo());
        map.put("name", equipment.getName());
        map.put("model", equipment.getModel());
        map.put("stock", equipment.getStock());
        map.put("labId", equipment.getLabId());
        map.put("status", equipment.getStatus());
        map.put("nextCalibrationDate", equipment.getNextCalibrationDate() == null
                ? null : equipment.getNextCalibrationDate().toString());
        map.put("remark", equipment.getRemark());
        return map;
    }
}
