package com.labreserve.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labreserve.common.constant.RedisKeyConst;
import com.labreserve.common.exception.BizException;
import com.labreserve.common.result.PageResult;
import com.labreserve.mapper.EquipmentMapper;
import com.labreserve.mapper.ExperimentTypeMapper;
import com.labreserve.mapper.LaboratoryMapper;
import com.labreserve.mapper.ReservationMapper;
import com.labreserve.mapper.TimeSlotMapper;
import com.labreserve.model.dto.AvailabilityQueryDTO;
import com.labreserve.model.dto.LaboratoryOptionQueryDTO;
import com.labreserve.model.dto.LaboratoryPageDTO;
import com.labreserve.model.dto.LaboratorySaveDTO;
import com.labreserve.model.dto.LaboratoryStatusDTO;
import com.labreserve.model.entity.Equipment;
import com.labreserve.model.entity.ExperimentType;
import com.labreserve.model.entity.Laboratory;
import com.labreserve.model.entity.Reservation;
import com.labreserve.model.entity.TimeSlot;
import com.labreserve.model.vo.EquipmentOptionVO;
import com.labreserve.model.vo.LaboratoryAvailabilityVO;
import com.labreserve.model.vo.LaboratoryDetailVO;
import com.labreserve.model.vo.LaboratoryOptionVO;
import com.labreserve.model.vo.LaboratoryVO;
import com.labreserve.model.vo.SlotAvailabilityVO;
import com.labreserve.service.LaboratoryService;
import com.labreserve.service.ResourceLogService;
import com.labreserve.util.RedisUtils;
import com.labreserve.util.SecurityUtils;
import com.labreserve.util.WeekdayUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 实验室服务实现。
 */
@Slf4j
@Service
public class LaboratoryServiceImpl implements LaboratoryService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    @Autowired
    private LaboratoryMapper laboratoryMapper;

    @Autowired
    private EquipmentMapper equipmentMapper;

    @Autowired
    private ExperimentTypeMapper experimentTypeMapper;

    @Autowired
    private TimeSlotMapper timeSlotMapper;

    @Autowired
    private ReservationMapper reservationMapper;

    @Autowired
    private ResourceLogService resourceLogService;

    @Autowired
    private WeekdayUtils weekdayUtils;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private SecurityUtils securityUtils;

    // ============================================================
    // 查询
    // ============================================================

    @Override
    public PageResult<LaboratoryVO> page(LaboratoryPageDTO dto) {
        LambdaQueryWrapper<Laboratory> wrapper = Wrappers.<Laboratory>lambdaQuery()
                .eq(dto.getStatus() != null, Laboratory::getStatus, dto.getStatus())
                .eq(dto.getExperimentTypeId() != null, Laboratory::getExperimentTypeId, dto.getExperimentTypeId())
                .ge(dto.getMinCapacity() != null, Laboratory::getCapacity, dto.getMinCapacity())
                .like(StringUtils.hasText(dto.getLocation()), Laboratory::getLocation, dto.getLocation())
                .and(StringUtils.hasText(dto.getKeyword()), condition -> condition
                        .like(Laboratory::getLabNo, dto.getKeyword())
                        .or().like(Laboratory::getName, dto.getKeyword())
                        .or().like(Laboratory::getLocation, dto.getKeyword()))
                .orderByAsc(Laboratory::getLabNo);

        Page<Laboratory> page = laboratoryMapper.selectPage(dto.toMpPage(), wrapper);
        Map<Long, String> typeNames = loadExperimentTypeNames(page.getRecords());
        return PageResult.of(page, entity -> toVO(entity, typeNames.get(entity.getExperimentTypeId())));
    }

    @Override
    public LaboratoryDetailVO getInfo(Long id) {
        Laboratory lab = requireExists(id);
        Map<Long, String> typeNames = loadExperimentTypeNames(List.of(lab));

        LaboratoryDetailVO vo = new LaboratoryDetailVO();
        fill(vo, lab, typeNames.get(lab.getExperimentTypeId()));
        List<Equipment> equipments = equipmentMapper.selectList(Wrappers.<Equipment>lambdaQuery()
                .eq(Equipment::getLabId, id)
                .orderByAsc(Equipment::getResourceNo));
        vo.setEquipments(equipments.stream().map(entity -> {
            EquipmentOptionVO option = new EquipmentOptionVO();
            option.setId(entity.getId());
            option.setResourceNo(entity.getResourceNo());
            option.setName(entity.getName());
            option.setModel(entity.getModel());
            option.setStock(entity.getStock());
            option.setLabId(entity.getLabId());
            option.setStatus(entity.getStatus());
            return option;
        }).toList());
        return vo;
    }

    @Override
    public List<LaboratoryOptionVO> listOption(LaboratoryOptionQueryDTO dto) {
        Integer status = dto == null || dto.getStatus() == null ? Integer.valueOf(1) : dto.getStatus();
        List<Laboratory> list = laboratoryMapper.selectList(Wrappers.<Laboratory>lambdaQuery()
                .eq(status != null, Laboratory::getStatus, status)
                .eq(dto != null && dto.getExperimentTypeId() != null,
                        Laboratory::getExperimentTypeId, dto == null ? null : dto.getExperimentTypeId())
                .orderByAsc(Laboratory::getLabNo));
        return list.stream().map(entity -> {
            LaboratoryOptionVO vo = new LaboratoryOptionVO();
            vo.setId(entity.getId());
            vo.setLabNo(entity.getLabNo());
            vo.setName(entity.getName());
            vo.setCapacity(entity.getCapacity());
            vo.setExperimentTypeId(entity.getExperimentTypeId());
            vo.setStatus(entity.getStatus());
            return vo;
        }).toList();
    }

    // ============================================================
    // 写操作（全部留痕）
    // ============================================================

    @Override
    public Long create(LaboratorySaveDTO dto) {
        validate(dto);
        requireUniqueLabNo(dto.getLabNo(), null);

        Laboratory entity = new Laboratory();
        apply(entity, dto);
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        laboratoryMapper.insert(entity);

        resourceLogService.record(ResourceLogService.TYPE_LAB, entity.getId(), ResourceLogService.OP_CREATE,
                null, snapshot(entity), dto.getUpdateReason());
        log.info("[实验室] 新增 id={}, labNo={}", entity.getId(), entity.getLabNo());
        return entity.getId();
    }

    @Override
    public void update(LaboratorySaveDTO dto) {
        if (dto.getId() == null) {
            throw BizException.badRequest("实验室 ID 不能为空");
        }
        Laboratory exists = requireExists(dto.getId());
        validate(dto);
        requireUniqueLabNo(dto.getLabNo(), dto.getId());

        Laboratory entity = new Laboratory();
        entity.setId(dto.getId());
        apply(entity, dto);
        entity.setStatus(dto.getStatus());
        entity.setDescription(dto.getDescription());
        laboratoryMapper.updateById(entity);

        Laboratory latest = laboratoryMapper.selectById(dto.getId());
        resourceLogService.record(ResourceLogService.TYPE_LAB, dto.getId(), ResourceLogService.OP_UPDATE,
                snapshot(exists), snapshot(latest == null ? entity : latest), dto.getUpdateReason());
        log.info("[实验室] 修改 id={}", dto.getId());
    }

    @Override
    public void updateStatus(LaboratoryStatusDTO dto) {
        Laboratory exists = requireExists(dto.getId());
        if (Objects.equals(exists.getStatus(), dto.getStatus())) {
            throw BizException.conflict("实验室已经是该状态，无需重复操作");
        }
        Laboratory update = new Laboratory();
        update.setId(dto.getId());
        update.setStatus(dto.getStatus());
        laboratoryMapper.updateById(update);

        Laboratory latest = laboratoryMapper.selectById(dto.getId());
        resourceLogService.record(ResourceLogService.TYPE_LAB, dto.getId(), ResourceLogService.OP_STATUS,
                snapshot(exists), snapshot(latest == null ? update : latest), dto.getUpdateReason());
        log.info("[实验室] 状态变更 id={}, status={}", dto.getId(), dto.getStatus());
    }

    // ============================================================
    // 可用时段
    // ============================================================

    @Override
    public LaboratoryAvailabilityVO availability(AvailabilityQueryDTO dto) {
        Laboratory lab = requireExists(dto.getId());
        LocalDate date = dto.getDate();
        int weekday = date.getDayOfWeek().getValue();
        boolean openWeekday = weekdayUtils.contains(lab.getOpenWeekdays(), weekday);

        List<TimeSlot> slots = timeSlotMapper.selectList(Wrappers.<TimeSlot>lambdaQuery()
                .eq(TimeSlot::getStatus, 1)
                .orderByAsc(TimeSlot::getSort)
                .orderByAsc(TimeSlot::getId));

        // 占用集合 = { PENDING, APPROVED, CHANGE_PENDING }
        Set<Long> occupiedSlotIds = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                        .eq(Reservation::getLabId, lab.getId())
                        .eq(Reservation::getReserveDate, date)
                        .in(Reservation::getStatus, (Object[]) Reservation.OCCUPYING_STATUS))
                .stream().map(Reservation::getSlotId).filter(Objects::nonNull).collect(Collectors.toSet());

        Long userId = securityUtils.getUserId();
        boolean usable = Integer.valueOf(1).equals(lab.getStatus());
        boolean past = date.isBefore(LocalDate.now());

        List<SlotAvailabilityVO> slotVOs = slots.stream().map(slot -> {
            SlotAvailabilityVO vo = new SlotAvailabilityVO();
            vo.setSlotId(slot.getId());
            vo.setSlotName(slot.getName());
            vo.setStartTime(slot.getStartTime());
            vo.setEndTime(slot.getEndTime());

            String reason = null;
            if (!usable) {
                reason = "实验室已停用";
            } else if (past) {
                reason = "日期已过";
            } else if (!openWeekday) {
                reason = "该实验室" + weekdayUtils.nameOf(weekday) + "不开放";
            } else if (slot.getStartTime().isBefore(lab.getOpenTimeStart())
                    || slot.getEndTime().isAfter(lab.getOpenTimeEnd())) {
                reason = "不在实验室开放时间（" + lab.getOpenTimeStart().format(HH_MM)
                        + "-" + lab.getOpenTimeEnd().format(HH_MM) + "）内";
            } else if (occupiedSlotIds.contains(slot.getId())) {
                reason = "已被预约";
            }
            vo.setAvailable(reason == null);
            vo.setReason(reason);

            String queueKey = RedisKeyConst.queueKey(1, lab.getId(), date, slot.getId());
            vo.setQueueCount(redisUtils.zCard(queueKey));
            if (userId != null) {
                long rank = redisUtils.zRank(queueKey, String.valueOf(userId));
                vo.setMyQueuePosition(rank < 0 ? null : (int) rank + 1);
            }
            return vo;
        }).toList();

        LaboratoryAvailabilityVO vo = new LaboratoryAvailabilityVO();
        vo.setLabId(lab.getId());
        vo.setLabNo(lab.getLabNo());
        vo.setName(lab.getName());
        vo.setDate(date);
        vo.setOpenWeekday(openWeekday);
        vo.setOpenTimeText(lab.getOpenTimeStart().format(HH_MM) + "-" + lab.getOpenTimeEnd().format(HH_MM));
        vo.setSlots(slotVOs);
        return vo;
    }

    // ============================================================
    // 内部工具
    // ============================================================

    private Laboratory requireExists(Long id) {
        if (id == null) {
            throw BizException.badRequest("实验室 ID 不能为空");
        }
        Laboratory lab = laboratoryMapper.selectById(id);
        if (lab == null) {
            throw BizException.notFound("实验室不存在");
        }
        return lab;
    }

    private void validate(LaboratorySaveDTO dto) {
        if (!weekdayUtils.isValid(dto.getOpenWeekdays())) {
            throw BizException.badRequest("开放星期格式不正确，应为 1-7 的逗号分隔值，如 1,2,3,4,5");
        }
        if (!dto.getOpenTimeEnd().isAfter(dto.getOpenTimeStart())) {
            throw BizException.badRequest("开放结束时间必须晚于开始时间");
        }
        ExperimentType type = experimentTypeMapper.selectById(dto.getExperimentTypeId());
        if (type == null) {
            throw BizException.badRequest("实验类型不存在");
        }
        if (!Integer.valueOf(1).equals(type.getStatus())) {
            throw BizException.badRequest("实验类型「" + type.getName() + "」已停用，不能选用");
        }
    }

    private void requireUniqueLabNo(String labNo, Long excludeId) {
        long count = laboratoryMapper.selectCount(Wrappers.<Laboratory>lambdaQuery()
                .eq(Laboratory::getLabNo, labNo.trim().toUpperCase())
                .ne(excludeId != null, Laboratory::getId, excludeId));
        if (count > 0) {
            throw BizException.conflict("实验室编号已存在：" + labNo.trim().toUpperCase());
        }
    }

    private void apply(Laboratory entity, LaboratorySaveDTO dto) {
        entity.setLabNo(dto.getLabNo().trim().toUpperCase());
        entity.setName(dto.getName().trim());
        entity.setLocation(dto.getLocation().trim());
        entity.setOpenTimeStart(dto.getOpenTimeStart());
        entity.setOpenTimeEnd(dto.getOpenTimeEnd());
        entity.setOpenWeekdays(weekdayUtils.normalize(dto.getOpenWeekdays()));
        entity.setCapacity(dto.getCapacity());
        entity.setExperimentTypeId(dto.getExperimentTypeId());
        entity.setDescription(dto.getDescription());
    }

    private Map<Long, String> loadExperimentTypeNames(List<Laboratory> labs) {
        Set<Long> ids = labs.stream().map(Laboratory::getExperimentTypeId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return experimentTypeMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(ExperimentType::getId, ExperimentType::getName));
    }

    private LaboratoryVO toVO(Laboratory entity, String experimentTypeName) {
        LaboratoryVO vo = new LaboratoryVO();
        fill(vo, entity, experimentTypeName);
        return vo;
    }

    private void fill(LaboratoryVO vo, Laboratory entity, String experimentTypeName) {
        vo.setId(entity.getId());
        vo.setLabNo(entity.getLabNo());
        vo.setName(entity.getName());
        vo.setLocation(entity.getLocation());
        vo.setOpenTimeStart(entity.getOpenTimeStart());
        vo.setOpenTimeEnd(entity.getOpenTimeEnd());
        vo.setOpenWeekdays(entity.getOpenWeekdays());
        vo.setOpenWeekdaysText(weekdayUtils.toText(entity.getOpenWeekdays()));
        vo.setCapacity(entity.getCapacity());
        vo.setExperimentTypeId(entity.getExperimentTypeId());
        vo.setExperimentTypeName(experimentTypeName);
        vo.setStatus(entity.getStatus());
        vo.setDescription(entity.getDescription());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
    }

    /** 快照：只放业务字段，且时间统一成 HH:mm 字符串，方便前端直接做 diff 展示 */
    private Map<String, Object> snapshot(Laboratory lab) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("labNo", lab.getLabNo());
        map.put("name", lab.getName());
        map.put("location", lab.getLocation());
        map.put("openTimeStart", lab.getOpenTimeStart() == null ? null : lab.getOpenTimeStart().format(HH_MM));
        map.put("openTimeEnd", lab.getOpenTimeEnd() == null ? null : lab.getOpenTimeEnd().format(HH_MM));
        map.put("openWeekdays", lab.getOpenWeekdays());
        map.put("capacity", lab.getCapacity());
        map.put("experimentTypeId", lab.getExperimentTypeId());
        map.put("status", lab.getStatus());
        map.put("description", lab.getDescription());
        return map;
    }
}
