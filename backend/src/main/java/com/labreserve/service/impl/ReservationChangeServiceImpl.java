package com.labreserve.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labreserve.common.constant.RedisKeyConst;
import com.labreserve.common.enums.MessageType;
import com.labreserve.common.exception.BizException;
import com.labreserve.common.result.PageResult;
import com.labreserve.mapper.EquipmentMapper;
import com.labreserve.mapper.ReservationChangeRequestMapper;
import com.labreserve.mapper.ReservationEquipmentMapper;
import com.labreserve.mapper.ReservationMapper;
import com.labreserve.mapper.TimeSlotMapper;
import com.labreserve.mapper.UserMapper;
import com.labreserve.model.dto.ReservationChangeAuditDTO;
import com.labreserve.model.dto.ReservationChangeCreateDTO;
import com.labreserve.model.dto.ReservationChangePageDTO;
import com.labreserve.model.dto.ReservationEquipmentDTO;
import com.labreserve.model.entity.Equipment;
import com.labreserve.model.entity.Reservation;
import com.labreserve.model.entity.ReservationChangeRequest;
import com.labreserve.model.entity.ReservationEquipment;
import com.labreserve.model.entity.TimeSlot;
import com.labreserve.model.entity.User;
import com.labreserve.model.vo.ReservationChangeVO;
import com.labreserve.service.MessageService;
import com.labreserve.service.QueueCandidateService;
import com.labreserve.service.ReservationChangeService;
import com.labreserve.util.AfterCommitUtils;
import com.labreserve.util.JsonUtils;
import com.labreserve.util.RedisLockTemplate;
import com.labreserve.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 预约调整申请实现。
 */
@Slf4j
@Service
public class ReservationChangeServiceImpl implements ReservationChangeService {

    /** 发起调整需提前的小时数（yml: lab-reserve.reservation.change-ahead-hours） */
    @Value("${lab-reserve.reservation.change-ahead-hours:24}")
    private long changeAheadHours;

    @Autowired
    private ReservationChangeRequestMapper changeMapper;

    @Autowired
    private ReservationMapper reservationMapper;

    @Autowired
    private ReservationEquipmentMapper reservationEquipmentMapper;

    @Autowired
    private TimeSlotMapper timeSlotMapper;

    @Autowired
    private EquipmentMapper equipmentMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ReservationConflictChecker conflictChecker;

    @Autowired
    private RedisLockTemplate redisLockTemplate;

    @Autowired
    private SecurityUtils securityUtils;

    @Autowired
    private JsonUtils jsonUtils;

    @Autowired
    private MessageService messageService;

    @Autowired
    private AfterCommitUtils afterCommitUtils;

    @Autowired
    private QueueCandidateService queueCandidateService;

    // ============================================================
    // 发起
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ReservationChangeCreateDTO dto) {
        Reservation reservation = requireExists(dto.getReservationId());
        if (!Objects.equals(reservation.getApplicantId(), securityUtils.getUserId())) {
            throw BizException.forbidden("只能对自己的预约发起调整");
        }
        if (!"APPROVED".equals(reservation.getStatus())) {
            throw BizException.conflict("只有已通过的预约可以发起调整（当前状态："
                    + statusName(reservation.getStatus()) + "）");
        }

        // 24 小时窗口
        LocalDateTime startAt = startAtOf(reservation);
        if (startAt != null && LocalDateTime.now().isAfter(startAt.minusHours(changeAheadHours))) {
            throw BizException.conflict("距实验开始不足 " + changeAheadHours + " 小时，无法调整");
        }

        // 同一预约只允许一条待审申请
        long pendingCount = changeMapper.selectCount(Wrappers.<ReservationChangeRequest>lambdaQuery()
                .eq(ReservationChangeRequest::getReservationId, reservation.getId())
                .eq(ReservationChangeRequest::getStatus, ReservationChangeRequest.STATUS_PENDING));
        if (pendingCount > 0) {
            throw BizException.conflict("该预约已有待审批的调整申请，请先等待审批");
        }

        boolean isCancel = ReservationChangeRequest.TYPE_CANCEL == dto.getChangeType();
        Map<String, Object> before = snapshotOf(reservation);
        Map<String, Object> after;

        if (isCancel) {
            after = before;
        } else {
            if (dto.getNewReserveDate() == null || dto.getNewSlotId() == null) {
                throw BizException.badRequest("修改预约必须填写新的日期与时段");
            }
            if (dto.getNewReserveDate().isBefore(LocalDate.now())) {
                throw BizException.badRequest("新的预约日期不能早于今天");
            }
            TimeSlot newSlot = timeSlotMapper.selectById(dto.getNewSlotId());
            if (newSlot == null || !Integer.valueOf(1).equals(newSlot.getStatus())) {
                throw BizException.badRequest("新的时段不存在或已停用");
            }
            // 发起时就预检一次，避免"提交了但注定通不过"
            if (Integer.valueOf(1).equals(reservation.getResourceType())) {
                boolean occupied = conflictChecker.isLabSlotOccupied(reservation.getLabId(),
                        dto.getNewReserveDate(), dto.getNewSlotId(), reservation.getId());
                if (occupied) {
                    throw BizException.conflict("目标时段已被预约，请换一个时段");
                }
            }
            after = snapshotOf(reservation);
            after.put("reserveDate", dto.getNewReserveDate().toString());
            after.put("slotId", dto.getNewSlotId());
            after.put("slotName", newSlot.getName());
            if (dto.getEquipments() != null) {
                after.put("equipments", equipmentSnapshot(dto.getEquipments()));
            }
        }

        ReservationChangeRequest entity = new ReservationChangeRequest();
        entity.setReservationId(reservation.getId());
        entity.setApplicantId(reservation.getApplicantId());
        entity.setChangeType(dto.getChangeType());
        entity.setBeforeJson(jsonUtils.toJson(before));
        entity.setAfterJson(jsonUtils.toJson(after));
        entity.setReason(dto.getReason());
        entity.setStatus(ReservationChangeRequest.STATUS_PENDING);
        changeMapper.insert(entity);

        // 只改状态，业务字段一律不动（design.md §4.2 的硬约束）
        Reservation update = new Reservation();
        update.setId(reservation.getId());
        update.setStatus("CHANGE_PENDING");
        reservationMapper.updateById(update);

        log.info("[调整申请] 发起 id={}, reservationId={}, type={}", entity.getId(),
                reservation.getId(), dto.getChangeType());
        return entity.getId();
    }

    // ============================================================
    // 查询
    // ============================================================

    @Override
    public List<ReservationChangeVO> listByReservation(Long reservationId) {
        Reservation reservation = requireExists(reservationId);
        Long userId = securityUtils.getUserId();
        if (!Objects.equals(reservation.getApplicantId(), userId)
                && !securityUtils.hasRole("SYS_ADMIN") && !securityUtils.hasRole("LAB_ADMIN")) {
            throw BizException.forbidden("无权查看该预约的调整记录");
        }
        List<ReservationChangeRequest> list = changeMapper.selectList(Wrappers.<ReservationChangeRequest>lambdaQuery()
                .eq(ReservationChangeRequest::getReservationId, reservationId)
                .orderByDesc(ReservationChangeRequest::getId));
        return toVOList(list);
    }

    @Override
    public PageResult<ReservationChangeVO> page(ReservationChangePageDTO dto) {
        LambdaQueryWrapper<ReservationChangeRequest> wrapper = Wrappers.<ReservationChangeRequest>lambdaQuery()
                .eq(dto.getStatus() != null, ReservationChangeRequest::getStatus, dto.getStatus())
                .eq(dto.getChangeType() != null, ReservationChangeRequest::getChangeType, dto.getChangeType())
                .eq(dto.getApplicantId() != null, ReservationChangeRequest::getApplicantId, dto.getApplicantId())
                .ge(dto.getBeginTime() != null, ReservationChangeRequest::getCreatedAt, dto.getBeginTime())
                .le(dto.getEndTime() != null, ReservationChangeRequest::getCreatedAt, dto.getEndTime())
                .orderByAsc(ReservationChangeRequest::getStatus)
                .orderByDesc(ReservationChangeRequest::getId);
        Page<ReservationChangeRequest> page = changeMapper.selectPage(dto.toMpPage(), wrapper);
        return PageResult.of(toVOList(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    // ============================================================
    // 审批 / 撤销
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(ReservationChangeAuditDTO dto) {
        ReservationChangeRequest change = requireChange(dto.getId());
        if (!Integer.valueOf(ReservationChangeRequest.STATUS_PENDING).equals(change.getStatus())) {
            throw BizException.conflict("该申请已处理，不能重复审批");
        }
        Reservation reservation = requireExists(change.getReservationId());
        Long auditorId = securityUtils.getUserId();

        if (ReservationChangeRequest.STATUS_REJECTED == dto.getResult()) {
            changeMapper.update(null, Wrappers.<ReservationChangeRequest>lambdaUpdate()
                    .eq(ReservationChangeRequest::getId, change.getId())
                    .set(ReservationChangeRequest::getStatus, ReservationChangeRequest.STATUS_REJECTED)
                    .set(ReservationChangeRequest::getAuditUserId, auditorId)
                    .set(ReservationChangeRequest::getAuditTime, LocalDateTime.now())
                    .set(ReservationChangeRequest::getAuditOpinion, dto.getOpinion()));
            // 预约单从 CHANGE_PENDING 回到 APPROVED，业务字段未变
            updateReservationStatus(reservation.getId(), "APPROVED");
            messageApplicant(reservation, "调整申请被驳回",
                    "你的预约 " + reservation.getReserveNo() + " 的调整申请被驳回："
                            + (StringUtils.hasText(dto.getOpinion()) ? dto.getOpinion() : "未填写原因"));
            log.info("[调整申请] 驳回 id={}", change.getId());
            return;
        }

        // ---------- 通过 ----------
        if (!"CHANGE_PENDING".equals(reservation.getStatus())) {
            throw BizException.conflict("预约当前不是「调整审批中」状态，无法通过");
        }
        Map<String, Object> after = parseSnapshot(change.getAfterJson());
        boolean isCancel = ReservationChangeRequest.TYPE_CANCEL == change.getChangeType();

        if (isCancel) {
            updateReservationStatus(reservation.getId(), "CANCELED");
        } else {
            LocalDate newDate = LocalDate.parse(String.valueOf(after.get("reserveDate")));
            Long newSlotId = Long.valueOf(String.valueOf(after.get("slotId")));
            String lockKey = RedisKeyConst.lockReserveKey(reservation.getResourceType(),
                    Integer.valueOf(1).equals(reservation.getResourceType())
                            ? reservation.getLabId() : reservation.getEquipmentId(),
                    newDate, newSlotId);
            redisLockTemplate.execute(lockKey, () -> {
                if (Integer.valueOf(1).equals(reservation.getResourceType())
                        && conflictChecker.isLabSlotOccupied(reservation.getLabId(), newDate, newSlotId,
                        reservation.getId())) {
                    throw BizException.conflict("目标时段已被占用，无法通过调整");
                }
                Reservation update = new Reservation();
                update.setId(reservation.getId());
                update.setReserveDate(newDate);
                update.setSlotId(newSlotId);
                update.setStatus("APPROVED");
                reservationMapper.updateById(update);

                // 附带设备按 after_json 覆盖
                Object equipments = after.get("equipments");
                if (equipments instanceof List<?> list) {
                    reservationEquipmentMapper.delete(Wrappers.<ReservationEquipment>lambdaQuery()
                            .eq(ReservationEquipment::getReservationId, reservation.getId()));
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> map) {
                            ReservationEquipment re = new ReservationEquipment();
                            re.setReservationId(reservation.getId());
                            re.setEquipmentId(Long.valueOf(String.valueOf(map.get("equipmentId"))));
                            re.setEquipmentCount(Integer.valueOf(String.valueOf(map.get("equipmentCount"))));
                            reservationEquipmentMapper.insert(re);
                        }
                    }
                }
            });
        }

        changeMapper.update(null, Wrappers.<ReservationChangeRequest>lambdaUpdate()
                .eq(ReservationChangeRequest::getId, change.getId())
                .set(ReservationChangeRequest::getStatus, ReservationChangeRequest.STATUS_APPROVED)
                .set(ReservationChangeRequest::getAuditUserId, auditorId)
                .set(ReservationChangeRequest::getAuditTime, LocalDateTime.now())
                .set(ReservationChangeRequest::getAuditOpinion, dto.getOpinion()));

        messageApplicant(reservation, "调整申请已通过",
                "你的预约 " + reservation.getReserveNo() + " 的调整申请已通过："
                        + (isCancel ? "预约已取消" : summaryOf(change)));
        // 原时段被释放（取消 / 换时间都一样）→ 触发候补顺延
        Long oldResourceId = Integer.valueOf(1).equals(reservation.getResourceType())
                ? reservation.getLabId() : reservation.getEquipmentId();
        afterCommitUtils.run(() -> queueCandidateService.triggerCandidate(
                reservation.getResourceType(), oldResourceId,
                reservation.getReserveDate(), reservation.getSlotId()));
        log.info("[调整申请] 通过 id={}, reservationId={}", change.getId(), reservation.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        ReservationChangeRequest change = requireChange(id);
        if (!Objects.equals(change.getApplicantId(), securityUtils.getUserId())) {
            throw BizException.forbidden("只能撤销自己的调整申请");
        }
        if (!Integer.valueOf(ReservationChangeRequest.STATUS_PENDING).equals(change.getStatus())) {
            throw BizException.conflict("该申请已处理，不能撤销");
        }
        changeMapper.update(null, Wrappers.<ReservationChangeRequest>lambdaUpdate()
                .eq(ReservationChangeRequest::getId, change.getId())
                .set(ReservationChangeRequest::getStatus, ReservationChangeRequest.STATUS_REJECTED)
                .set(ReservationChangeRequest::getAuditOpinion, "申请人主动撤销"));
        updateReservationStatus(change.getReservationId(), "APPROVED");
        log.info("[调整申请] 申请人撤销 id={}", id);
    }

    // ============================================================
    // 内部工具
    // ============================================================

    private Reservation requireExists(Long id) {
        Reservation reservation = id == null ? null : reservationMapper.selectById(id);
        if (reservation == null) {
            throw BizException.notFound("预约不存在");
        }
        return reservation;
    }

    private ReservationChangeRequest requireChange(Long id) {
        ReservationChangeRequest change = id == null ? null : changeMapper.selectById(id);
        if (change == null) {
            throw BizException.notFound("调整申请不存在");
        }
        return change;
    }

    private void updateReservationStatus(Long reservationId, String status) {
        Reservation update = new Reservation();
        update.setId(reservationId);
        update.setStatus(status);
        reservationMapper.updateById(update);
    }

    private void messageApplicant(Reservation reservation, String title, String content) {
        afterCommitUtils.run(() -> messageService.send(reservation.getApplicantId(),
                MessageType.CHANGE_AUDIT, title, content, "RESERVATION", reservation.getId()));
    }

    /** 预约开始时刻 = 日期 + 时段开始时间 */
    private LocalDateTime startAtOf(Reservation reservation) {
        TimeSlot slot = reservation.getSlotId() == null ? null : timeSlotMapper.selectById(reservation.getSlotId());
        if (slot == null || reservation.getReserveDate() == null) {
            return null;
        }
        LocalTime start = slot.getStartTime();
        return LocalDateTime.of(reservation.getReserveDate(), start);
    }

    /** before / after 快照：只放业务字段 */
    private Map<String, Object> snapshotOf(Reservation reservation) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("reserveDate", reservation.getReserveDate() == null ? null : reservation.getReserveDate().toString());
        map.put("slotId", reservation.getSlotId());
        TimeSlot slot = reservation.getSlotId() == null ? null : timeSlotMapper.selectById(reservation.getSlotId());
        map.put("slotName", slot == null ? null : slot.getName());
        map.put("usePeople", reservation.getUsePeople());
        map.put("courseName", reservation.getCourseName());
        map.put("purpose", reservation.getPurpose());
        map.put("equipments", equipmentSnapshotOfReservation(reservation.getId()));
        return map;
    }

    private List<Map<String, Object>> equipmentSnapshotOfReservation(Long reservationId) {
        List<ReservationEquipment> list = reservationEquipmentMapper.selectList(
                Wrappers.<ReservationEquipment>lambdaQuery()
                        .eq(ReservationEquipment::getReservationId, reservationId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ReservationEquipment item : list) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("equipmentId", item.getEquipmentId());
            map.put("equipmentCount", item.getEquipmentCount());
            Equipment equipment = equipmentMapper.selectById(item.getEquipmentId());
            map.put("resourceNo", equipment == null ? null : equipment.getResourceNo());
            result.add(map);
        }
        return result;
    }

    private List<Map<String, Object>> equipmentSnapshot(List<ReservationEquipmentDTO> equipments) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ReservationEquipmentDTO item : equipments) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("equipmentId", item.getEquipmentId());
            map.put("equipmentCount", item.getEquipmentCount());
            Equipment equipment = equipmentMapper.selectById(item.getEquipmentId());
            map.put("resourceNo", equipment == null ? null : equipment.getResourceNo());
            result.add(map);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSnapshot(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        return jsonUtils.parse(json, Map.class);
    }

    /** 人话摘要：2026-10-25 5-6节 → 2026-10-26 3-4节 / 取消预约 */
    private String summaryOf(ReservationChangeRequest change) {
        Map<String, Object> before = parseSnapshot(change.getBeforeJson());
        Map<String, Object> after = parseSnapshot(change.getAfterJson());
        if (ReservationChangeRequest.TYPE_CANCEL == change.getChangeType()) {
            return "取消预约";
        }
        return before.get("reserveDate") + " " + before.get("slotName") + " → "
                + after.get("reserveDate") + " " + after.get("slotName");
    }

    private List<ReservationChangeVO> toVOList(List<ReservationChangeRequest> list) {
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> reservationIds = list.stream().map(ReservationChangeRequest::getReservationId)
                .distinct().toList();
        Map<Long, Reservation> reservations = new LinkedHashMap<>();
        reservationMapper.selectBatchIds(reservationIds).forEach(item -> reservations.put(item.getId(), item));
        List<Long> userIds = new ArrayList<>();
        list.forEach(item -> {
            if (item.getApplicantId() != null) {
                userIds.add(item.getApplicantId());
            }
            if (item.getAuditUserId() != null) {
                userIds.add(item.getAuditUserId());
            }
        });
        Map<Long, User> users = new LinkedHashMap<>();
        if (!userIds.isEmpty()) {
            userMapper.selectBatchIds(userIds.stream().distinct().toList())
                    .forEach(item -> users.put(item.getId(), item));
        }
        return list.stream().map(item -> {
            ReservationChangeVO vo = new ReservationChangeVO();
            vo.setId(item.getId());
            vo.setReservationId(item.getReservationId());
            Reservation reservation = reservations.get(item.getReservationId());
            vo.setReserveNo(reservation == null ? null : reservation.getReserveNo());
            vo.setApplicantId(item.getApplicantId());
            User applicant = users.get(item.getApplicantId());
            vo.setApplicantName(applicant == null ? null : applicant.getRealName());
            vo.setChangeType(item.getChangeType());
            vo.setChangeTypeName(ReservationChangeRequest.TYPE_CANCEL == item.getChangeType() ? "取消预约" : "修改预约");
            vo.setReason(item.getReason());
            vo.setStatus(item.getStatus());
            vo.setStatusName(switch (item.getStatus()) {
                case ReservationChangeRequest.STATUS_PENDING -> "待审批";
                case ReservationChangeRequest.STATUS_APPROVED -> "已通过";
                default -> "已驳回";
            });
            vo.setBeforeJson(item.getBeforeJson());
            vo.setAfterJson(item.getAfterJson());
            vo.setSummary(summaryOf(item));
            vo.setAuditUserId(item.getAuditUserId());
            User auditor = users.get(item.getAuditUserId());
            vo.setAuditUserName(auditor == null ? null : auditor.getRealName());
            vo.setAuditTime(item.getAuditTime());
            vo.setAuditOpinion(item.getAuditOpinion());
            vo.setCreatedAt(item.getCreatedAt());
            return vo;
        }).toList();
    }

    private String statusName(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "PENDING" -> "待审核";
            case "APPROVED" -> "已通过";
            case "REJECTED" -> "已驳回";
            case "CANCELED" -> "已取消";
            case "CHANGE_PENDING" -> "调整审批中";
            case "EXPIRED" -> "已过期";
            default -> status;
        };
    }
}
