package com.labreserve.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labreserve.common.constant.CommonConst;
import com.labreserve.common.constant.RedisKeyConst;
import com.labreserve.mapper.EquipmentMapper;
import com.labreserve.mapper.LaboratoryMapper;
import com.labreserve.mapper.QueueBanRecordMapper;
import com.labreserve.mapper.ReservationEquipmentMapper;
import com.labreserve.mapper.ReservationMapper;
import com.labreserve.mapper.TimeSlotMapper;
import com.labreserve.model.dto.ReservationEquipmentDTO;
import com.labreserve.model.entity.Equipment;
import com.labreserve.model.entity.Laboratory;
import com.labreserve.model.entity.QueueBanRecord;
import com.labreserve.model.entity.Reservation;
import com.labreserve.model.entity.ReservationEquipment;
import com.labreserve.model.entity.TimeSlot;
import com.labreserve.model.vo.ReservationCheckVO;
import com.labreserve.util.RedisUtils;
import com.labreserve.util.WeekdayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 预约冲突判定（预检 / 提交 / 审核 / 调整审批共用）。
 *
 * <p>判定顺序是<b>固定</b>的，先给"资格类"问题（角色、班级、资源状态、类型匹配），
 * 再给"时间类"问题（开放日、开放时间窗），最后给"占用类"问题（时段占用、库存）。
 * 这样用户先看到的是"你根本没资格约"，而不是"被别人占了"。
 *
 * <p>占用集合统一为 {@link Reservation#OCCUPYING_STATUS}。
 */
@Component
public class ReservationConflictChecker {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    @Autowired
    private LaboratoryMapper laboratoryMapper;

    @Autowired
    private EquipmentMapper equipmentMapper;

    @Autowired
    private TimeSlotMapper timeSlotMapper;

    @Autowired
    private ReservationMapper reservationMapper;

    @Autowired
    private ReservationEquipmentMapper reservationEquipmentMapper;

    @Autowired
    private QueueBanRecordMapper queueBanRecordMapper;

    @Autowired
    private WeekdayUtils weekdayUtils;

    @Autowired
    private RedisUtils redisUtils;

    /**
     * 冲突判定入参。
     *
     * @param applicantId 申请人（用于「本人同时段已有预约」与排队顺位）
     * @param checkBan    是否检查 7 天禁入（预检与提交要查，审核不必）
     */
    public record Params(Integer resourceType, Long labId, Long equipmentId,
                         List<ReservationEquipmentDTO> equipments, LocalDate reserveDate,
                         Long slotId, Integer usePeople, Long experimentTypeId,
                         List<Long> classIds, String roleCode, Long applicantId,
                         boolean checkBan, Long excludeReservationId) {
    }

    /** 判定结果：conflict 为 null 表示可提交 */
    public record Result(ReservationCheckVO.ConflictVO conflict, ReservationCheckVO.BanInfoVO banInfo) {
        public boolean ok() {
            return conflict == null;
        }

        public static Result pass() {
            return new Result(null, null);
        }
    }

    // ============================================================
    // 主流程
    // ============================================================

    public Result check(Params p) {
        if (!Objects.equals(p.reserveDate().isBefore(LocalDate.now()), false)) {
            return conflict("NOT_IN_OPEN_TIME", "预约日期不能早于今天");
        }
        TimeSlot slot = timeSlotMapper.selectById(p.slotId());
        if (slot == null || !Integer.valueOf(1).equals(slot.getStatus())) {
            return conflict("NOT_IN_OPEN_TIME", "时段不存在或已停用");
        }

        Laboratory lab = null;
        Equipment mainEquipment = null;

        if (Integer.valueOf(1).equals(p.resourceType())) {
            if (!CommonConst.ROLE_TEACHER.equals(p.roleCode())) {
                return conflict("LAB_RESERVE_TEACHER_ONLY", "教学实验室由教师为班级排课，学生不能直接预约实验室");
            }
            if (CollectionUtils.isEmpty(p.classIds())) {
                return conflict("CLASS_REQUIRED", "预约实验室必须选择至少一个上课班级");
            }
            lab = p.labId() == null ? null : laboratoryMapper.selectById(p.labId());
            if (lab == null) {
                return conflict("LAB_DISABLED", "实验室不存在");
            }
            if (!Integer.valueOf(1).equals(lab.getStatus())) {
                return conflict("LAB_DISABLED", "实验室已停用");
            }
            if (p.experimentTypeId() != null && !Objects.equals(lab.getExperimentTypeId(), p.experimentTypeId())) {
                return conflict("EXPERIMENT_TYPE_NOT_SUPPORTED",
                        "该实验室只开展实验类型 #" + lab.getExperimentTypeId() + "，与所选类型不一致");
            }
            int weekday = p.reserveDate().getDayOfWeek().getValue();
            if (!weekdayUtils.contains(lab.getOpenWeekdays(), weekday)) {
                return conflict("NOT_IN_OPEN_TIME", "该实验室" + weekdayUtils.nameOf(weekday) + "不开放");
            }
            if (slot.getStartTime().isBefore(lab.getOpenTimeStart())
                    || slot.getEndTime().isAfter(lab.getOpenTimeEnd())) {
                return conflict("NOT_IN_OPEN_TIME", "该时段不在实验室开放时间（"
                        + lab.getOpenTimeStart().format(HH_MM) + "-" + lab.getOpenTimeEnd().format(HH_MM) + "）内");
            }
            if (p.usePeople() != null && p.usePeople() > lab.getCapacity()) {
                return conflict("CAPACITY_EXCEEDED", "使用人数超过实验室容纳人数（" + lab.getCapacity() + "）");
            }
        } else {
            mainEquipment = p.equipmentId() == null ? null : equipmentMapper.selectById(p.equipmentId());
            if (mainEquipment == null) {
                return conflict("EQUIPMENT_UNAVAILABLE", "设备不存在");
            }
            if (!Integer.valueOf(1).equals(mainEquipment.getStatus())) {
                return conflict("EQUIPMENT_UNAVAILABLE", "设备当前不可借（状态：" + statusName(mainEquipment.getStatus()) + "）");
            }
        }

        // ---------- 占用类：时段 / 库存 ----------
        if (lab != null && isLabSlotOccupied(lab.getId(), p.reserveDate(), p.slotId(), p.excludeReservationId())) {
            String queueKey = RedisKeyConst.queueKey(1, lab.getId(), p.reserveDate(), p.slotId());
            return conflictWithQueue("SLOT_OCCUPIED", "该时段已被预约", queueKey, p.applicantId());
        }

        // 主资源是设备：数量直接占用该设备
        if (mainEquipment != null) {
            int need = 1;
            if (p.equipments() != null) {
                // resourceType=2 时忽略附带设备列表，统一用 1 台（主资源按 1 次预约占用）
                need = 1;
            }
            long used = usedCount(mainEquipment.getId(), p.reserveDate(), p.slotId());
            int stock = mainEquipment.getStock() == null ? 0 : mainEquipment.getStock();
            if (used + need > stock) {
                String queueKey = RedisKeyConst.queueKey(2, mainEquipment.getId(), p.reserveDate(), p.slotId());
                return conflictWithQueue("STOCK_NOT_ENOUGH",
                        "剩余 " + Math.max(0, stock - (int) used) + " 台，需求 " + need + " 台",
                        queueKey, p.applicantId());
            }
        }

        // 附带设备（实验室顺带借的）：逐条校验库存
        if (!CollectionUtils.isEmpty(p.equipments())) {
            Map<Long, Integer> needMap = p.equipments().stream()
                    .filter(item -> item.getEquipmentId() != null && item.getEquipmentCount() != null)
                    .collect(Collectors.toMap(ReservationEquipmentDTO::getEquipmentId,
                            ReservationEquipmentDTO::getEquipmentCount, Integer::sum));
            for (Map.Entry<Long, Integer> entry : needMap.entrySet()) {
                Equipment equipment = equipmentMapper.selectById(entry.getKey());
                if (equipment == null || !Integer.valueOf(1).equals(equipment.getStatus())) {
                    return conflict("EQUIPMENT_UNAVAILABLE",
                            "附带设备 " + (equipment == null ? "#" + entry.getKey() : equipment.getResourceNo())
                                    + " 当前不可借");
                }
                long used = usedCount(equipment.getId(), p.reserveDate(), p.slotId());
                int stock = equipment.getStock() == null ? 0 : equipment.getStock();
                if (used + entry.getValue() > stock) {
                    return conflict("STOCK_NOT_ENOUGH", "设备 " + equipment.getResourceNo() + " 剩余 "
                            + Math.max(0, stock - (int) used) + " 台，需求 " + entry.getValue() + " 台");
                }
            }
        }

        // ---------- 本人同时段重复预约 ----------
        if (p.applicantId() != null && hasOwnReservation(p.applicantId(), p.reserveDate(), p.slotId(),
                p.excludeReservationId())) {
            return conflict("ALREADY_RESERVED", "你在该时段已有预约，不能重复提交");
        }

        // ---------- 7 天禁入（迭代 6 写记录，本轮只读） ----------
        if (p.checkBan() && p.applicantId() != null) {
            QueueBanRecord ban = currentBan(p.applicantId(), p.resourceType(), p.labId(), p.equipmentId());
            if (ban != null) {
                ReservationCheckVO.BanInfoVO banInfo = new ReservationCheckVO.BanInfoVO();
                banInfo.setStartTime(ban.getStartTime());
                banInfo.setEndTime(ban.getEndTime());
                banInfo.setReason(ban.getReason());
                ReservationCheckVO.ConflictVO conflict = new ReservationCheckVO.ConflictVO();
                conflict.setCode("QUEUE_BANNED");
                conflict.setMessage("你已被限制加入该资源排队，解除时间 "
                        + ban.getEndTime().format(DateTimeFormatter.ofPattern(CommonConst.DATETIME_PATTERN)));
                conflict.setQueueCount(0L);
                return new Result(conflict, banInfo);
            }
        }

        return Result.pass();
    }

    // ============================================================
    // 查询工具（也供审核 / 调整审批复用）
    // ============================================================

    /** 实验室某时段是否已被占用 */
    public boolean isLabSlotOccupied(Long labId, LocalDate date, Long slotId, Long excludeReservationId) {
        return reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getLabId, labId)
                .eq(Reservation::getReserveDate, date)
                .eq(Reservation::getSlotId, slotId)
                .in(Reservation::getStatus, (Object[]) Reservation.OCCUPYING_STATUS)
                .ne(excludeReservationId != null, Reservation::getId, excludeReservationId)) > 0;
    }

    /**
     * 设备某时段已占用数量 = 直接预约的数量 + 作为「附带设备」被借走的数量。
     */
    public long usedCount(Long equipmentId, LocalDate date, Long slotId) {
        List<Reservation> occupying = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getReserveDate, date)
                .eq(Reservation::getSlotId, slotId)
                .in(Reservation::getStatus, (Object[]) Reservation.OCCUPYING_STATUS));
        long total = 0;
        for (Reservation reservation : occupying) {
            if (Objects.equals(reservation.getEquipmentId(), equipmentId)) {
                total += reservation.getEquipmentCount() == null ? 1 : reservation.getEquipmentCount();
            }
        }
        if (!occupying.isEmpty()) {
            List<Long> ids = occupying.stream().map(Reservation::getId).toList();
            List<ReservationEquipment> attached = reservationEquipmentMapper.selectList(
                    Wrappers.<ReservationEquipment>lambdaQuery()
                            .in(ReservationEquipment::getReservationId, ids)
                            .eq(ReservationEquipment::getEquipmentId, equipmentId));
            total += attached.stream()
                    .mapToLong(item -> item.getEquipmentCount() == null ? 1 : item.getEquipmentCount())
                    .sum();
        }
        return total;
    }

    /** 本人同一时间是否已有占用中的预约 */
    public boolean hasOwnReservation(Long applicantId, LocalDate date, Long slotId, Long excludeReservationId) {
        return reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getApplicantId, applicantId)
                .eq(Reservation::getReserveDate, date)
                .eq(Reservation::getSlotId, slotId)
                .in(Reservation::getStatus, (Object[]) Reservation.OCCUPYING_STATUS)
                .ne(excludeReservationId != null, Reservation::getId, excludeReservationId)) > 0;
    }

    /** 当前生效的禁入记录（按资源匹配） */
    public QueueBanRecord currentBan(Long userId, Integer resourceType, Long labId, Long equipmentId) {
        LocalDateTime now = LocalDateTime.now();
        List<QueueBanRecord> list = queueBanRecordMapper.selectList(Wrappers.<QueueBanRecord>lambdaQuery()
                .eq(QueueBanRecord::getUserId, userId)
                .eq(QueueBanRecord::getResourceType, resourceType)
                .gt(QueueBanRecord::getEndTime, now)
                .orderByDesc(QueueBanRecord::getEndTime));
        for (QueueBanRecord ban : list) {
            boolean sameResource = Integer.valueOf(1).equals(resourceType)
                    ? Objects.equals(ban.getLabId(), labId)
                    : Objects.equals(ban.getEquipmentId(), equipmentId);
            if (sameResource) {
                return ban;
            }
        }
        return null;
    }

    // ============================================================
    // 内部工具
    // ============================================================

    private Result conflict(String code, String message) {
        ReservationCheckVO.ConflictVO vo = new ReservationCheckVO.ConflictVO();
        vo.setCode(code);
        vo.setMessage(message);
        vo.setQueueCount(0L);
        return new Result(vo, null);
    }

    private Result conflictWithQueue(String code, String message, String queueKey, Long applicantId) {
        ReservationCheckVO.ConflictVO vo = new ReservationCheckVO.ConflictVO();
        vo.setCode(code);
        vo.setMessage(message);
        vo.setQueueCount(redisUtils.zCard(queueKey));
        if (applicantId != null) {
            long rank = redisUtils.zRank(queueKey, String.valueOf(applicantId));
            vo.setMyQueuePosition(rank < 0 ? null : (int) rank + 1);
        }
        return new Result(vo, null);
    }

    private String statusName(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 1 -> "正常";
            case 2 -> "维修中";
            case 3 -> "报废";
            case 0 -> "停用";
            default -> "未知";
        };
    }

    /** 给上层用的"可预约"结果 */
    public static ReservationCheckVO toCheckVO(Result result) {
        ReservationCheckVO vo = new ReservationCheckVO();
        vo.setAvailable(result.ok());
        vo.setConflict(result.conflict());
        vo.setBanInfo(result.banInfo());
        // 迭代 6 才开放排队：本轮仅有"占用/库存不足"允许排队，禁入与资格类问题不允许
        String code = result.conflict() == null ? null : result.conflict().getCode();
        vo.setCanQueue(code != null && StringUtils.hasText(code)
                && ("SLOT_OCCUPIED".equals(code) || "STOCK_NOT_ENOUGH".equals(code)));
        return vo;
    }
}
