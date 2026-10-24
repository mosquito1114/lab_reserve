package com.labreserve.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labreserve.common.constant.CommonConst;
import com.labreserve.common.constant.RedisKeyConst;
import com.labreserve.common.enums.MessageType;
import com.labreserve.common.exception.BizException;
import com.labreserve.common.result.PageResult;
import com.labreserve.mapper.ClassInfoMapper;
import com.labreserve.mapper.EquipmentMapper;
import com.labreserve.mapper.ExperimentTypeMapper;
import com.labreserve.mapper.LaboratoryMapper;
import com.labreserve.mapper.ReservationChangeRequestMapper;
import com.labreserve.mapper.ReservationClassMapper;
import com.labreserve.mapper.ReservationEquipmentMapper;
import com.labreserve.mapper.ReservationMapper;
import com.labreserve.mapper.RoleMapper;
import com.labreserve.mapper.StudentInfoMapper;
import com.labreserve.mapper.TeacherInfoMapper;
import com.labreserve.mapper.TimeSlotMapper;
import com.labreserve.mapper.UserMapper;
import com.labreserve.model.dto.ReservationAuditDTO;
import com.labreserve.model.dto.ReservationCancelDTO;
import com.labreserve.model.dto.ReservationPageDTO;
import com.labreserve.model.dto.ReservationSubmitDTO;
import com.labreserve.model.dto.ReservationTimetableDTO;
import com.labreserve.model.entity.ClassInfo;
import com.labreserve.model.entity.Equipment;
import com.labreserve.model.entity.ExperimentType;
import com.labreserve.model.entity.Laboratory;
import com.labreserve.model.entity.Reservation;
import com.labreserve.model.entity.ReservationChangeRequest;
import com.labreserve.model.entity.ReservationClass;
import com.labreserve.model.entity.ReservationEquipment;
import com.labreserve.model.entity.Role;
import com.labreserve.model.entity.StudentInfo;
import com.labreserve.model.entity.TeacherInfo;
import com.labreserve.model.entity.TimeSlot;
import com.labreserve.model.entity.User;
import com.labreserve.model.vo.ReservationCheckVO;
import com.labreserve.model.vo.ReservationDetailVO;
import com.labreserve.model.vo.ReservationEquipmentVO;
import com.labreserve.model.vo.ReservationVO;
import com.labreserve.model.vo.TimetableVO;
import com.labreserve.security.PermissionService;
import com.labreserve.service.MessageService;
import com.labreserve.service.QueueCandidateService;
import com.labreserve.service.ReservationService;
import com.labreserve.util.AfterCommitUtils;
import com.labreserve.util.RedisLockTemplate;
import com.labreserve.util.RedisUtils;
import com.labreserve.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 预约服务实现。
 *
 * <p>并发双保险：
 * <ol>
 *   <li>Redisson 锁 {@code lock:reserve:{type}:{resId}:{date}:{slotId}}——挡住"同时提交"；</li>
 *   <li>{@code tb_reservation.uk_reservation_lab_occupy}（生成列唯一索引）——挡住锁失效/多实例漏锁。</li>
 * </ol>
 * 锁内判冲 → 插入；如果仍然撞唯一索引，捕获 {@link DuplicateKeyException} 转成 409。
 */
@Slf4j
@Service
public class ReservationServiceImpl implements ReservationService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    @Autowired
    private ReservationMapper reservationMapper;

    @Autowired
    private ReservationClassMapper reservationClassMapper;

    @Autowired
    private ReservationEquipmentMapper reservationEquipmentMapper;

    @Autowired
    private ReservationChangeRequestMapper reservationChangeRequestMapper;

    @Autowired
    private LaboratoryMapper laboratoryMapper;

    @Autowired
    private EquipmentMapper equipmentMapper;

    @Autowired
    private TimeSlotMapper timeSlotMapper;

    @Autowired
    private ExperimentTypeMapper experimentTypeMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StudentInfoMapper studentInfoMapper;

    @Autowired
    private TeacherInfoMapper teacherInfoMapper;

    @Autowired
    private ClassInfoMapper classInfoMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private ReservationConflictChecker conflictChecker;

    @Autowired
    private RedisLockTemplate redisLockTemplate;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private SecurityUtils securityUtils;

    @Autowired
    private MessageService messageService;

    @Autowired
    private AfterCommitUtils afterCommitUtils;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private QueueCandidateService queueCandidateService;

    @Autowired
    private com.labreserve.util.ReserveNoUtils reserveNoUtils;

    // ============================================================
    // 预检 / 提交
    // ============================================================

    @Override
    public ReservationCheckVO check(ReservationSubmitDTO dto) {
        ReservationConflictChecker.Result result = conflictChecker.check(buildParams(dto, true, null));
        return ReservationConflictChecker.toCheckVO(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReservationDetailVO create(ReservationSubmitDTO dto) {
        Long userId = securityUtils.getUserId();
        String lockKey = lockKey(dto.getResourceType(), resIdOf(dto), dto.getReserveDate(), dto.getSlotId());

        Long reservationId = redisLockTemplate.execute(lockKey, () -> {
            ReservationConflictChecker.Result conflict = conflictChecker.check(buildParams(dto, true, null));
            if (!conflict.ok()) {
                ReservationCheckVO.ConflictVO c = conflict.conflict();
                throw BizException.conflict(c.getMessage(),
                        Map.of("conflictCode", c.getCode(),
                                "queueCount", c.getQueueCount() == null ? 0L : c.getQueueCount(),
                                "canQueue", "SLOT_OCCUPIED".equals(c.getCode()) || "STOCK_NOT_ENOUGH".equals(c.getCode())));
            }

            Reservation entity = new Reservation();
            entity.setReserveNo(nextReserveNo(dto.getReserveDate()));
            entity.setApplicantId(userId);
            entity.setResourceType(dto.getResourceType());
            entity.setLabId(dto.getResourceType() == 1 ? dto.getLabId() : null);
            entity.setEquipmentId(dto.getResourceType() == 2 ? dto.getEquipmentId() : null);
            entity.setEquipmentCount(dto.getResourceType() == 2 ? 1 : 1);
            entity.setReserveDate(dto.getReserveDate());
            entity.setSlotId(dto.getSlotId());
            entity.setUsePeople(dto.getUsePeople());
            entity.setExperimentTypeId(dto.getExperimentTypeId());
            entity.setCourseName(dto.getCourseName());
            entity.setPurpose(dto.getPurpose());
            entity.setStatus("PENDING");
            entity.setFromQueue(0);
            try {
                reservationMapper.insert(entity);
            } catch (DuplicateKeyException e) {
                // 唯一索引兜底：说明同一实验室同时段已经有人插入
                log.warn("[预约] 唯一索引兜底命中 labId={}, date={}, slotId={}",
                        dto.getLabId(), dto.getReserveDate(), dto.getSlotId());
                throw BizException.conflict("该时段刚被他人预约，请刷新后重试",
                        Map.of("conflictCode", "SLOT_OCCUPIED", "queueCount", 0L, "canQueue", true));
            }

            // 教师排课：写入上课班级（学生课表的数据来源）
            if (dto.getResourceType() == 1 && !CollectionUtils.isEmpty(dto.getClassIds())) {
                dto.getClassIds().stream().filter(Objects::nonNull).distinct().forEach(classId -> {
                    ReservationClass rc = new ReservationClass();
                    rc.setReservationId(entity.getId());
                    rc.setClassId(classId);
                    reservationClassMapper.insert(rc);
                });
            }
            // 附带设备
            if (!CollectionUtils.isEmpty(dto.getEquipments())) {
                dto.getEquipments().forEach(item -> {
                    ReservationEquipment re = new ReservationEquipment();
                    re.setReservationId(entity.getId());
                    re.setEquipmentId(item.getEquipmentId());
                    re.setEquipmentCount(item.getEquipmentCount());
                    reservationEquipmentMapper.insert(re);
                });
            }
            log.info("[预约] 提交成功 id={}, no={}, type={}, applicant={}",
                    entity.getId(), entity.getReserveNo(), dto.getResourceType(), userId);
            return entity.getId();
        });

        return getInfo(reservationId);
    }

    // ============================================================
    // 查询
    // ============================================================

    @Override
    public PageResult<ReservationVO> page(ReservationPageDTO dto) {
        return doPage(dto, false);
    }

    @Override
    public PageResult<ReservationVO> myPage(ReservationPageDTO dto) {
        return doPage(dto, true);
    }

    @Override
    public PageResult<ReservationVO> pendingPage(ReservationPageDTO dto) {
        dto.setStatus("PENDING");
        return doPage(dto, false);
    }

    private PageResult<ReservationVO> doPage(ReservationPageDTO dto, boolean onlyMine) {
        Long userId = securityUtils.getUserId();
        LambdaQueryWrapper<Reservation> wrapper = Wrappers.<Reservation>lambdaQuery()
                .eq(StringUtils.hasText(dto.getStatus()), Reservation::getStatus, dto.getStatus())
                .eq(dto.getResourceType() != null, Reservation::getResourceType, dto.getResourceType())
                .eq(dto.getLabId() != null, Reservation::getLabId, dto.getLabId())
                .eq(dto.getEquipmentId() != null, Reservation::getEquipmentId, dto.getEquipmentId())
                .eq(dto.getExperimentTypeId() != null, Reservation::getExperimentTypeId, dto.getExperimentTypeId())
                .eq(dto.getFromQueue() != null, Reservation::getFromQueue, dto.getFromQueue())
                .ge(dto.getReserveDateBegin() != null, Reservation::getReserveDate, dto.getReserveDateBegin())
                .le(dto.getReserveDateEnd() != null, Reservation::getReserveDate, dto.getReserveDateEnd())
                .orderByDesc(Reservation::getReserveDate)
                .orderByDesc(Reservation::getId);

        if (onlyMine) {
            wrapper.eq(Reservation::getApplicantId, userId);
        } else if (StringUtils.hasText(dto.getApplicantKeyword())) {
            List<Long> ids = userMapper.selectList(Wrappers.<User>lambdaQuery()
                            .like(User::getRealName, dto.getApplicantKeyword())
                            .or().like(User::getUsername, dto.getApplicantKeyword()))
                    .stream().map(User::getId).toList();
            if (ids.isEmpty()) {
                return PageResult.empty(dto.safePageNum(), dto.safePageSize());
            }
            wrapper.in(Reservation::getApplicantId, ids);
        }

        Page<Reservation> page = reservationMapper.selectPage(dto.toMpPage(), wrapper);
        // 按学院筛选：申请人 → 教师/学生档案 → 学院
        List<Reservation> records = page.getRecords();
        if (StringUtils.hasText(dto.getCollege()) && !records.isEmpty()) {
            List<Long> applicantIds = records.stream().map(Reservation::getApplicantId).distinct().toList();
            Map<Long, String> colleges = loadApplicantColleges(applicantIds);
            records = records.stream()
                    .filter(item -> dto.getCollege().equals(colleges.get(item.getApplicantId())))
                    .toList();
        }
        return PageResult.of(toVOList(records), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public ReservationDetailVO getInfo(Long id) {
        Reservation reservation = requireExists(id);
        Long userId = securityUtils.getUserId();
        boolean owner = Objects.equals(reservation.getApplicantId(), userId);
        if (!owner && !permissionService.hasPermission("reservation:view")) {
            throw BizException.forbidden("无权查看他人的预约");
        }
        return toDetailVO(reservation);
    }

    @Override
    public List<TimetableVO> timetable(ReservationTimetableDTO dto) {
        Long userId = securityUtils.getUserId();
        StudentInfo student = studentInfoMapper.selectOne(Wrappers.<StudentInfo>lambdaQuery()
                .eq(StudentInfo::getUserId, userId));
        if (student == null || student.getClassId() == null) {
            // 教师 / 维修人员调用：本接口面向学生，返回空
            return Collections.emptyList();
        }
        LocalDate begin = dto.getBeginDate() == null ? LocalDate.now().minusDays(30) : dto.getBeginDate();
        LocalDate end = dto.getEndDate() == null ? LocalDate.now().plusDays(30) : dto.getEndDate();

        List<Long> reservationIds = reservationClassMapper.selectList(Wrappers.<ReservationClass>lambdaQuery()
                        .eq(ReservationClass::getClassId, student.getClassId()))
                .stream().map(ReservationClass::getReservationId).distinct().toList();
        if (reservationIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Reservation> list = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .in(Reservation::getId, reservationIds)
                .eq(Reservation::getStatus, "APPROVED")
                .ge(Reservation::getReserveDate, begin)
                .le(Reservation::getReserveDate, end)
                .eq(dto.getLabId() != null, Reservation::getLabId, dto.getLabId())
                .orderByAsc(Reservation::getReserveDate)
                .orderByAsc(Reservation::getSlotId));
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Laboratory> labs = loadLabs(list);
        Map<Long, TimeSlot> slots = loadSlots(list);
        Map<Long, User> teachers = loadUsers(list.stream().map(Reservation::getApplicantId).distinct().toList());
        return list.stream().map(item -> {
            TimetableVO vo = new TimetableVO();
            vo.setReservationId(item.getId());
            vo.setReserveNo(item.getReserveNo());
            vo.setLabId(item.getLabId());
            Laboratory lab = labs.get(item.getLabId());
            if (lab != null) {
                vo.setLabNo(lab.getLabNo());
                vo.setLabName(lab.getName());
                vo.setLabLocation(lab.getLocation());
            }
            vo.setReserveDate(item.getReserveDate());
            TimeSlot slot = slots.get(item.getSlotId());
            if (slot != null) {
                vo.setSlotName(slot.getName());
                vo.setStartTime(slot.getStartTime());
                vo.setEndTime(slot.getEndTime());
            }
            vo.setCourseName(item.getCourseName());
            User teacher = teachers.get(item.getApplicantId());
            vo.setTeacherName(teacher == null ? null : teacher.getRealName());
            vo.setStatus(item.getStatus());
            return vo;
        }).toList();
    }

    // ============================================================
    // 审核 / 撤销 / 释放
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(ReservationAuditDTO dto) {
        Reservation reservation = requireExists(dto.getId());
        if (!"PENDING".equals(reservation.getStatus())) {
            throw BizException.conflict("只有待审核的预约可以审核（当前状态：" + statusName(reservation.getStatus()) + "）");
        }
        if ("REJECTED".equals(dto.getResult()) && !StringUtils.hasText(dto.getOpinion())) {
            throw BizException.badRequest("驳回必须填写原因");
        }

        Long auditorId = securityUtils.getUserId();
        String lockKey = lockKey(reservation.getResourceType(), resIdOf(reservation),
                reservation.getReserveDate(), reservation.getSlotId());
        redisLockTemplate.execute(lockKey, () -> {
            if ("APPROVED".equals(dto.getResult())) {
                // 锁内二次判冲：可能在这期间有人抢到了同一时段
                boolean occupied = Integer.valueOf(1).equals(reservation.getResourceType())
                        && conflictChecker.isLabSlotOccupied(reservation.getLabId(), reservation.getReserveDate(),
                        reservation.getSlotId(), reservation.getId());
                if (occupied) {
                    throw BizException.conflict("该时段已被其他预约占用，无法通过");
                }
                if (Integer.valueOf(2).equals(reservation.getResourceType())) {
                    long used = conflictChecker.usedCount(reservation.getEquipmentId(),
                            reservation.getReserveDate(), reservation.getSlotId());
                    Equipment equipment = equipmentMapper.selectById(reservation.getEquipmentId());
                    int stock = equipment == null || equipment.getStock() == null ? 0 : equipment.getStock();
                    int need = reservation.getEquipmentCount() == null ? 1 : reservation.getEquipmentCount();
                    if (used + need > stock) {
                        throw BizException.conflict("设备库存不足（剩余 " + Math.max(0, stock - (int) used) + " 台），无法通过");
                    }
                }
            }
            Reservation update = new Reservation();
            update.setId(reservation.getId());
            update.setStatus(dto.getResult());
            update.setAuditUserId(auditorId);
            update.setAuditTime(LocalDateTime.now());
            update.setRejectReason("REJECTED".equals(dto.getResult()) ? dto.getOpinion() : null);
            reservationMapper.updateById(update);
        });

        boolean approved = "APPROVED".equals(dto.getResult());
        String title = approved ? "预约审核通过" : "预约已被驳回";
        String content = approved
                ? "你的预约 " + reservation.getReserveNo() + " 已通过审核：" + describe(reservation) + "。"
                : "你的预约 " + reservation.getReserveNo() + " 被驳回，原因：" + dto.getOpinion();
        afterCommitUtils.run(() -> messageService.send(reservation.getApplicantId(),
                MessageType.RESERVATION_AUDIT, title, content, "RESERVATION", reservation.getId()));
        log.info("[预约] 审核 id={}, result={}, auditor={}", reservation.getId(), dto.getResult(), auditorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(ReservationCancelDTO dto) {
        Reservation reservation = requireExists(dto.getId());
        if (!Objects.equals(reservation.getApplicantId(), securityUtils.getUserId())) {
            throw BizException.forbidden("只能撤销自己的预约");
        }
        if (!"PENDING".equals(reservation.getStatus())) {
            throw BizException.conflict("只有待审核的预约可以直接撤销；已通过的预约请提交调整申请");
        }
        Reservation update = new Reservation();
        update.setId(reservation.getId());
        update.setStatus("CANCELED");
        update.setRejectReason(dto.getReason());
        reservationMapper.updateById(update);
        triggerCandidateAfterRelease(reservation);
        // 通知所有实验室管理员：待审核列表少了一条，需要让他们知道是谁撤的
        notifyRole(CommonConst.ROLE_LAB_ADMIN, "预约已撤销",
                "教师/学生撤销了预约 " + reservation.getReserveNo()
                        + (StringUtils.hasText(dto.getReason()) ? "，原因：" + dto.getReason() : "")
                        + "（" + describe(reservation) + "）。", reservation.getId());
        log.info("[预约] 撤销 id={}, reason={}", reservation.getId(), dto.getReason());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release(ReservationCancelDTO dto) {
        Reservation reservation = requireExists(dto.getId());
        if (!"PENDING".equals(reservation.getStatus()) && !"APPROVED".equals(reservation.getStatus())) {
            throw BizException.conflict("只有待审核或已通过的预约可以释放（当前状态："
                    + statusName(reservation.getStatus()) + "）");
        }
        String lockKey = lockKey(reservation.getResourceType(), resIdOf(reservation),
                reservation.getReserveDate(), reservation.getSlotId());
        redisLockTemplate.execute(lockKey, () -> {
            Reservation update = new Reservation();
            update.setId(reservation.getId());
            update.setStatus("CANCELED");
            update.setRejectReason(StringUtils.hasText(dto.getReason()) ? dto.getReason() : "管理员释放时段");
            reservationMapper.updateById(update);
        });
        triggerCandidateAfterRelease(reservation);
        afterCommitUtils.run(() -> messageService.send(reservation.getApplicantId(),
                MessageType.RESERVATION_AUDIT, "预约已被释放",
                "你的预约 " + reservation.getReserveNo() + " 已被管理员释放："
                        + (StringUtils.hasText(dto.getReason()) ? dto.getReason() : "管理员释放时段"),
                "RESERVATION", reservation.getId()));
        log.info("[预约] 释放 id={}, reason={}", reservation.getId(), dto.getReason());
    }

    // ============================================================
    // 内部工具
    // ============================================================

    public Reservation requireExists(Long id) {
        if (id == null) {
            throw BizException.badRequest("预约 ID 不能为空");
        }
        Reservation reservation = reservationMapper.selectById(id);
        if (reservation == null) {
            throw BizException.notFound("预约不存在");
        }
        return reservation;
    }

    private ReservationConflictChecker.Params buildParams(ReservationSubmitDTO dto, boolean checkBan,
                                                          Long excludeReservationId) {
        return new ReservationConflictChecker.Params(
                dto.getResourceType(),
                dto.getResourceType() == 1 ? dto.getLabId() : null,
                dto.getResourceType() == 2 ? dto.getEquipmentId() : null,
                dto.getEquipments(),
                dto.getReserveDate(),
                dto.getSlotId(),
                dto.getUsePeople(),
                dto.getExperimentTypeId(),
                dto.getClassIds(),
                securityUtils.getRoleCode(),
                securityUtils.getUserId(),
                checkBan,
                excludeReservationId);
    }

    /** 时段被释放后触发候补顺延（事务提交后执行，避免读到未提交的取消状态） */
    private void triggerCandidateAfterRelease(Reservation reservation) {
        afterCommitUtils.run(() -> queueCandidateService.triggerCandidate(
                reservation.getResourceType(),
                Integer.valueOf(1).equals(reservation.getResourceType())
                        ? reservation.getLabId() : reservation.getEquipmentId(),
                reservation.getReserveDate(), reservation.getSlotId()));
    }

    /** 通知某个角色的所有正常账号 */
    private void notifyRole(String roleCode, String title, String content, Long bizId) {
        Role role = roleMapper.selectOne(Wrappers.<Role>lambdaQuery().eq(Role::getCode, roleCode));
        if (role == null) {
            return;
        }
        List<Long> userIds = userMapper.selectList(Wrappers.<User>lambdaQuery()
                        .eq(User::getRoleId, role.getId())
                        .eq(User::getStatus, 1))
                .stream().map(User::getId).toList();
        if (userIds.isEmpty()) {
            return;
        }
        afterCommitUtils.run(() -> userIds.forEach(id -> {
            try {
                messageService.send(id, MessageType.RESERVATION_AUDIT, title, content, "RESERVATION", bizId);
            } catch (Exception e) {
                log.warn("[预约] 通知失败 userId={}, {}", id, e.getMessage());
            }
        }));
    }

    private String lockKey(Integer resourceType, Long resourceId, LocalDate date, Long slotId) {
        return RedisKeyConst.lockReserveKey(resourceType, resourceId, date, slotId);
    }

    private Long resIdOf(ReservationSubmitDTO dto) {
        return Integer.valueOf(1).equals(dto.getResourceType()) ? dto.getLabId() : dto.getEquipmentId();
    }

    private Long resIdOf(Reservation reservation) {
        return Integer.valueOf(1).equals(reservation.getResourceType())
                ? reservation.getLabId() : reservation.getEquipmentId();
    }

    private String nextReserveNo(LocalDate date) {
        // 与「候补确认转正」共用同一套编号规则
        return reserveNoUtils.next(date);
    }

    private String describe(Reservation reservation) {
        TimeSlot slot = timeSlotMapper.selectById(reservation.getSlotId());
        String slotText = slot == null ? "" : " " + slot.getName()
                + "（" + slot.getStartTime().format(HH_MM) + "-" + slot.getEndTime().format(HH_MM) + "）";
        String resource = Integer.valueOf(1).equals(reservation.getResourceType())
                ? "实验室 #" + reservation.getLabId() : "设备 #" + reservation.getEquipmentId();
        return "日期 " + reservation.getReserveDate() + slotText + "，" + resource;
    }

    // ---------- VO 装配 ----------

    private List<ReservationVO> toVOList(List<Reservation> list) {
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Laboratory> labs = loadLabs(list);
        Map<Long, Equipment> equipments = loadEquipments(list);
        Map<Long, TimeSlot> slots = loadSlots(list);
        Map<Long, ExperimentType> types = loadTypes(list);
        Map<Long, User> users = loadUsers(list.stream().map(Reservation::getApplicantId).distinct().toList());
        Map<Long, String> colleges = loadApplicantColleges(users.keySet().stream().toList());
        return list.stream().map(item -> fillVO(new ReservationVO(), item, labs, equipments, slots, types, users, colleges))
                .map(ReservationVO.class::cast).toList();
    }

    private ReservationDetailVO toDetailVO(Reservation reservation) {
        List<Reservation> single = List.of(reservation);
        Map<Long, Laboratory> labs = loadLabs(single);
        Map<Long, Equipment> equipments = loadEquipments(single);
        Map<Long, TimeSlot> slots = loadSlots(single);
        Map<Long, ExperimentType> types = loadTypes(single);
        Map<Long, User> users = loadUsers(single.stream().map(Reservation::getApplicantId).distinct().toList());
        Map<Long, String> colleges = loadApplicantColleges(users.keySet().stream().toList());

        ReservationDetailVO vo = new ReservationDetailVO();
        fillVO(vo, reservation, labs, equipments, slots, types, users, colleges);
        vo.setFromQueue(reservation.getFromQueue());

        // 附带设备
        List<ReservationEquipment> attached = reservationEquipmentMapper.selectList(
                Wrappers.<ReservationEquipment>lambdaQuery()
                        .eq(ReservationEquipment::getReservationId, reservation.getId()));
        if (!attached.isEmpty()) {
            Map<Long, Equipment> attachedEquipments = loadEquipmentsByIds(
                    attached.stream().map(ReservationEquipment::getEquipmentId).distinct().toList());
            vo.setEquipments(attached.stream().map(item -> {
                ReservationEquipmentVO ev = new ReservationEquipmentVO();
                ev.setEquipmentId(item.getEquipmentId());
                ev.setEquipmentCount(item.getEquipmentCount());
                Equipment equipment = attachedEquipments.get(item.getEquipmentId());
                if (equipment != null) {
                    ev.setResourceNo(equipment.getResourceNo());
                    ev.setName(equipment.getName());
                    ev.setModel(equipment.getModel());
                }
                return ev;
            }).toList());
        }

        if (reservation.getAuditUserId() != null) {
            User auditor = userMapper.selectById(reservation.getAuditUserId());
            vo.setAuditUserName(auditor == null ? null : auditor.getRealName());
        }
        ReservationChangeRequest pending = reservationChangeRequestMapper.selectOne(
                Wrappers.<ReservationChangeRequest>lambdaQuery()
                        .eq(ReservationChangeRequest::getReservationId, reservation.getId())
                        .eq(ReservationChangeRequest::getStatus, ReservationChangeRequest.STATUS_PENDING)
                        .orderByDesc(ReservationChangeRequest::getId)
                        .last("limit 1"));
        if (pending != null) {
            vo.setChangePending(toChangeVO(pending, users, colleges));
        }
        return vo;
    }

    private <T extends ReservationVO> T fillVO(T vo, Reservation item,
                                               Map<Long, Laboratory> labs,
                                               Map<Long, Equipment> equipments,
                                               Map<Long, TimeSlot> slots,
                                               Map<Long, ExperimentType> types,
                                               Map<Long, User> users,
                                               Map<Long, String> colleges) {
        vo.setId(item.getId());
        vo.setReserveNo(item.getReserveNo());
        vo.setStatus(item.getStatus());
        vo.setStatusName(statusName(item.getStatus()));
        vo.setApplicantId(item.getApplicantId());
        User applicant = users.get(item.getApplicantId());
        vo.setApplicantName(applicant == null ? null : applicant.getRealName());
        vo.setApplicantCollege(colleges.get(item.getApplicantId()));
        vo.setResourceType(item.getResourceType());
        if (Integer.valueOf(1).equals(item.getResourceType())) {
            Laboratory lab = labs.get(item.getLabId());
            if (lab != null) {
                vo.setResourceName(lab.getLabNo() + " " + lab.getName());
                vo.setLabNo(lab.getLabNo());
                vo.setLabLocation(lab.getLocation());
            }
            vo.setLabId(item.getLabId());
        } else {
            Equipment equipment = equipments.get(item.getEquipmentId());
            if (equipment != null) {
                vo.setResourceName(equipment.getResourceNo() + " " + equipment.getName());
                vo.setEquipmentNo(equipment.getResourceNo());
            }
            vo.setEquipmentId(item.getEquipmentId());
            vo.setEquipmentCount(item.getEquipmentCount());
        }
        vo.setReserveDate(item.getReserveDate());
        vo.setSlotId(item.getSlotId());
        TimeSlot slot = slots.get(item.getSlotId());
        if (slot != null) {
            vo.setSlotName(slot.getName());
            vo.setStartTime(slot.getStartTime());
            vo.setEndTime(slot.getEndTime());
        }
        vo.setUsePeople(item.getUsePeople());
        vo.setExperimentTypeId(item.getExperimentTypeId());
        ExperimentType type = types.get(item.getExperimentTypeId());
        vo.setExperimentTypeName(type == null ? null : type.getName());
        vo.setCourseName(item.getCourseName());
        vo.setPurpose(item.getPurpose());
        vo.setCreatedAt(item.getCreatedAt());
        return vo;
    }

    private ReservationChangeRequestVO toChangeVO(ReservationChangeRequest entity, Map<Long, User> users,
                                                  Map<Long, String> colleges) {
        ReservationChangeRequestVO vo = new ReservationChangeRequestVO();
        vo.setId(entity.getId());
        vo.setReservationId(entity.getReservationId());
        vo.setApplicantId(entity.getApplicantId());
        User applicant = users.get(entity.getApplicantId());
        vo.setApplicantName(applicant == null ? null : applicant.getRealName());
        vo.setChangeType(entity.getChangeType());
        vo.setReason(entity.getReason());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    /** 详情里展示"待审批的调整申请"，只需少量字段 */
    private static class ReservationChangeRequestVO extends com.labreserve.model.vo.ReservationChangeVO {
        @java.io.Serial
        private static final long serialVersionUID = 1L;
    }

    private Map<Long, Laboratory> loadLabs(List<Reservation> list) {
        List<Long> ids = list.stream().map(Reservation::getLabId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Laboratory> map = new LinkedHashMap<>();
        laboratoryMapper.selectBatchIds(ids).forEach(item -> map.put(item.getId(), item));
        return map;
    }

    private Map<Long, Equipment> loadEquipments(List<Reservation> list) {
        List<Long> ids = list.stream().map(Reservation::getEquipmentId).filter(Objects::nonNull).distinct().toList();
        return loadEquipmentsByIds(ids);
    }

    private Map<Long, Equipment> loadEquipmentsByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Equipment> map = new LinkedHashMap<>();
        equipmentMapper.selectBatchIds(ids).forEach(item -> map.put(item.getId(), item));
        return map;
    }

    private Map<Long, TimeSlot> loadSlots(List<Reservation> list) {
        List<Long> ids = list.stream().map(Reservation::getSlotId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, TimeSlot> map = new LinkedHashMap<>();
        timeSlotMapper.selectBatchIds(ids).forEach(item -> map.put(item.getId(), item));
        return map;
    }

    private Map<Long, ExperimentType> loadTypes(List<Reservation> list) {
        List<Long> ids = list.stream().map(Reservation::getExperimentTypeId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, ExperimentType> map = new LinkedHashMap<>();
        experimentTypeMapper.selectBatchIds(ids).forEach(item -> map.put(item.getId(), item));
        return map;
    }

    private Map<Long, User> loadUsers(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, User> map = new LinkedHashMap<>();
        userMapper.selectBatchIds(ids).forEach(item -> map.put(item.getId(), item));
        return map;
    }

    /** 申请人学院：教师档案优先，其次学生档案所属班级的学院 */
    private Map<Long, String> loadApplicantColleges(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new LinkedHashMap<>();
        teacherInfoMapper.selectList(Wrappers.<TeacherInfo>lambdaQuery()
                        .in(TeacherInfo::getUserId, userIds))
                .forEach(item -> map.put(item.getUserId(), item.getCollege()));
        List<StudentInfo> students = studentInfoMapper.selectList(Wrappers.<StudentInfo>lambdaQuery()
                .in(StudentInfo::getUserId, userIds));
        if (!students.isEmpty()) {
            Map<Long, ClassInfo> classes = new LinkedHashMap<>();
            classInfoMapper.selectBatchIds(students.stream().map(StudentInfo::getClassId)
                            .filter(Objects::nonNull).distinct().toList())
                    .forEach(item -> classes.put(item.getId(), item));
            students.forEach(student -> {
                ClassInfo classInfo = classes.get(student.getClassId());
                if (classInfo != null) {
                    map.putIfAbsent(student.getUserId(), classInfo.getCollege());
                }
            });
        }
        return map;
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

    /** 供调整申请模块复用：把预约单渲染成"人话"摘要 */
    public String summaryOf(Reservation reservation) {
        return reservation.getReserveDate() + " " + describe(reservation);
    }

    /** 供调整申请模块复用（避免重复注入） */
    public Map<Long, String> collegesOf(List<Long> userIds) {
        return loadApplicantColleges(userIds);
    }

    /** 供调整申请模块复用 */
    public String statusNameOf(String status) {
        return statusName(status);
    }

}
