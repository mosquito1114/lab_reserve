package com.labreserve.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labreserve.common.constant.RedisKeyConst;
import com.labreserve.common.exception.BizException;
import com.labreserve.mapper.ClassInfoMapper;
import com.labreserve.mapper.EquipmentMapper;
import com.labreserve.mapper.ExperimentTypeMapper;
import com.labreserve.mapper.LaboratoryMapper;
import com.labreserve.mapper.RepairOrderMapper;
import com.labreserve.mapper.ReservationMapper;
import com.labreserve.mapper.ResourceQueueMapper;
import com.labreserve.mapper.StudentInfoMapper;
import com.labreserve.mapper.TeacherInfoMapper;
import com.labreserve.mapper.TimeSlotMapper;
import com.labreserve.mapper.UserMapper;
import com.labreserve.model.dto.StatisticsQueryDTO;
import com.labreserve.model.entity.ClassInfo;
import com.labreserve.model.entity.Equipment;
import com.labreserve.model.entity.ExperimentType;
import com.labreserve.model.entity.Laboratory;
import com.labreserve.model.entity.RepairOrder;
import com.labreserve.model.entity.Reservation;
import com.labreserve.model.entity.ResourceQueue;
import com.labreserve.model.entity.StudentInfo;
import com.labreserve.model.entity.TeacherInfo;
import com.labreserve.model.entity.TimeSlot;
import com.labreserve.model.entity.User;
import com.labreserve.model.vo.ExperimentTypeExportVO;
import com.labreserve.model.vo.ExperimentTypeRatioVO;
import com.labreserve.model.vo.LabOccupancyExportVO;
import com.labreserve.model.vo.OccupancyVO;
import com.labreserve.model.vo.ReservationExportVO;
import com.labreserve.model.vo.ReservationTrendVO;
import com.labreserve.model.vo.ResourceRatioExportVO;
import com.labreserve.model.vo.ResourceRatioVO;
import com.labreserve.model.vo.StatisticsOverviewVO;
import com.labreserve.model.vo.SuccessRateExportVO;
import com.labreserve.model.vo.SuccessRateVO;
import com.labreserve.service.MessageService;
import com.labreserve.service.StatisticsService;
import com.labreserve.util.JsonUtils;
import com.labreserve.util.RedisUtils;
import com.labreserve.util.SecurityUtils;
import com.labreserve.util.WeekdayUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.OutputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 统计报表实现。
 *
 * <p>数据量按「1 千用户 / 50 实验室 / 500 设备 / 5 万预约」估算，
 * 聚合直接在 Java 里做（一次查出区间内的预约，再分组计算）——
 * 比写一堆 GROUP BY 更不容易出错，也方便 5 种报表共用同一份数据。
 */
@Slf4j
@Service
public class StatisticsServiceImpl implements StatisticsService {

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private ReservationMapper reservationMapper;

    @Autowired
    private LaboratoryMapper laboratoryMapper;

    @Autowired
    private EquipmentMapper equipmentMapper;

    @Autowired
    private ExperimentTypeMapper experimentTypeMapper;

    @Autowired
    private TimeSlotMapper timeSlotMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TeacherInfoMapper teacherInfoMapper;

    @Autowired
    private StudentInfoMapper studentInfoMapper;

    @Autowired
    private ClassInfoMapper classInfoMapper;

    @Autowired
    private ResourceQueueMapper resourceQueueMapper;

    @Autowired
    private RepairOrderMapper repairOrderMapper;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private JsonUtils jsonUtils;

    @Autowired
    private WeekdayUtils weekdayUtils;

    @Autowired
    private SecurityUtils securityUtils;

    @Autowired
    private MessageService messageService;

    // ============================================================
    // 总览
    // ============================================================

    @Override
    public StatisticsOverviewVO overview() {
        StatisticsOverviewVO vo = new StatisticsOverviewVO();
        vo.setReservationTotal(reservationMapper.selectCount(null));
        vo.setPendingAudit(reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getStatus, "PENDING")));
        vo.setTodayReservation(reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getReserveDate, LocalDate.now())));
        vo.setQueueTotal(resourceQueueMapper.selectCount(Wrappers.<ResourceQueue>lambdaQuery()
                .in(ResourceQueue::getStatus, ResourceQueue.QUEUING, ResourceQueue.NOTIFIED)));
        vo.setRepairPending(repairOrderMapper.selectCount(Wrappers.<RepairOrder>lambdaQuery()
                .in(RepairOrder::getStatus, RepairOrder.SUBMITTED, RepairOrder.ASSIGNED,
                        RepairOrder.REPAIRING, RepairOrder.REPAIRED, RepairOrder.REOPENED)));
        vo.setLabTotal(laboratoryMapper.selectCount(null));
        vo.setEquipmentTotal(equipmentMapper.selectCount(null));
        vo.setUnreadMessage(messageService.unreadCount());
        return vo;
    }

    // ============================================================
    // 占用率
    // ============================================================

    @Override
    public OccupancyVO occupancy(StatisticsQueryDTO query) {
        StatisticsQueryDTO q = normalize(query);
        // 缓存：只对「无学院/类型/实验室筛选」的整体占用率做缓存，避免缓存键爆炸
        boolean cacheable = !StringUtils.hasText(q.getCollege())
                && q.getExperimentTypeId() == null && q.getLabId() == null;
        String cacheKey = RedisKeyConst.STAT_OCCUPANCY + q.getBeginDate() + ":" + q.getEndDate();
        if (cacheable) {
            String cached = redisUtils.get(cacheKey);
            if (StringUtils.hasText(cached)) {
                OccupancyVO cachedVo = jsonUtils.parse(cached, OccupancyVO.class);
                if (cachedVo != null) {
                    log.debug("[统计] 占用率命中缓存 {}", cacheKey);
                    return cachedVo;
                }
            }
        }

        List<Laboratory> labs = laboratoryMapper.selectList(Wrappers.<Laboratory>lambdaQuery()
                .eq(q.getLabId() != null, Laboratory::getId, q.getLabId()));
        List<TimeSlot> slots = timeSlotMapper.selectList(Wrappers.<TimeSlot>lambdaQuery()
                .eq(TimeSlot::getStatus, 1)
                .orderByAsc(TimeSlot::getSort));
        List<Reservation> reservations = listReservations(q);

        long totalOpen = 0;
        long totalUsed = 0;
        List<OccupancyVO.LabOccupancyVO> byLab = new ArrayList<>();
        for (Laboratory lab : labs) {
            long openSlots = countOpenSlots(lab, slots, q.getBeginDate(), q.getEndDate());
            long used = reservations.stream()
                    .filter(item -> Integer.valueOf(1).equals(item.getResourceType()))
                    .filter(item -> Objects.equals(item.getLabId(), lab.getId()))
                    .count();
            OccupancyVO.LabOccupancyVO item = new OccupancyVO.LabOccupancyVO();
            item.setLabId(lab.getId());
            item.setLabNo(lab.getLabNo());
            item.setLabName(lab.getName());
            item.setOpenSlots(openSlots);
            item.setUsedSlots(used);
            item.setOccupancyRate(rate(used, openSlots));
            byLab.add(item);
            totalOpen += openSlots;
            totalUsed += Math.min(used, openSlots);
        }
        byLab = byLab.stream().sorted(Comparator.comparing(OccupancyVO.LabOccupancyVO::getOccupancyRate).reversed())
                .toList();

        // 设备：分母 = 库存 × 该设备所属实验室的开放时段数
        List<Equipment> equipments = equipmentMapper.selectList(Wrappers.<Equipment>lambdaQuery());
        Map<Long, Laboratory> labMap = labMap();
        Map<Long, Integer> usedStock = new HashMap<>();
        reservations.stream()
                .filter(item -> Integer.valueOf(2).equals(item.getResourceType()))
                .forEach(item -> usedStock.merge(item.getEquipmentId(),
                        item.getEquipmentCount() == null ? 1 : item.getEquipmentCount(), Integer::sum));
        List<OccupancyVO.EquipmentOccupancyVO> byEquipment = new ArrayList<>();
        long equipTotal = 0;
        long equipUsed = 0;
        for (Equipment equipment : equipments) {
            Laboratory lab = labMap.get(equipment.getLabId());
            if (lab == null) {
                continue;
            }
            long openSlots = countOpenSlots(lab, slots, q.getBeginDate(), q.getEndDate());
            int stock = equipment.getStock() == null ? 0 : equipment.getStock();
            long totalSlots = openSlots * stock;
            long used = usedStock.getOrDefault(equipment.getId(), 0);
            OccupancyVO.EquipmentOccupancyVO item = new OccupancyVO.EquipmentOccupancyVO();
            item.setEquipmentId(equipment.getId());
            item.setResourceNo(equipment.getResourceNo());
            item.setName(equipment.getName());
            item.setStock(stock);
            item.setUsedSlots(used);
            item.setTotalSlots(totalSlots);
            item.setOccupancyRate(rate(used, totalSlots));
            byEquipment.add(item);
            equipTotal += totalSlots;
            equipUsed += Math.min(used, totalSlots);
        }
        byEquipment = byEquipment.stream()
                .sorted(Comparator.comparing(OccupancyVO.EquipmentOccupancyVO::getOccupancyRate).reversed())
                .limit(20).toList();

        OccupancyVO vo = new OccupancyVO();
        vo.setByLab(byLab);
        vo.setByEquipment(byEquipment);
        // 总体 = 实验室占用率与设备占用率的平均（直接拿两种分母相加会被库存数量级带偏）
        double labRate = rate(totalUsed, totalOpen);
        double equipRate = rate(equipUsed, equipTotal);
        vo.setOverall(Math.round(((labRate + equipRate) / 2) * 10000) / 10000.0);

        if (cacheable) {
            redisUtils.set(cacheKey, jsonUtils.toJson(vo), Duration.ofMinutes(10));
        }
        return vo;
    }

    // ============================================================
    // 实验类型占比 / 成功率 / 资源占比 / 趋势
    // ============================================================

    @Override
    public ExperimentTypeRatioVO experimentTypeRatio(StatisticsQueryDTO query) {
        List<Reservation> reservations = listReservations(query);
        Map<Long, Long> counts = reservations.stream()
                .filter(item -> item.getExperimentTypeId() != null)
                .collect(Collectors.groupingBy(Reservation::getExperimentTypeId, Collectors.counting()));
        Map<Long, ExperimentType> types = new LinkedHashMap<>();
        experimentTypeMapper.selectList(null).forEach(item -> types.put(item.getId(), item));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();

        List<ExperimentTypeRatioVO.Item> items = counts.entrySet().stream().map(entry -> {
            ExperimentTypeRatioVO.Item item = new ExperimentTypeRatioVO.Item();
            item.setExperimentTypeId(entry.getKey());
            ExperimentType type = types.get(entry.getKey());
            item.setCode(type == null ? null : type.getCode());
            item.setName(type == null ? "未知" : type.getName());
            item.setCount(entry.getValue());
            item.setRatio(rate(entry.getValue(), total));
            return item;
        }).sorted(Comparator.comparing(ExperimentTypeRatioVO.Item::getCount).reversed()).toList();

        ExperimentTypeRatioVO vo = new ExperimentTypeRatioVO();
        vo.setTotal(total);
        vo.setItems(items);
        return vo;
    }

    @Override
    public SuccessRateVO successRate(StatisticsQueryDTO query) {
        List<Reservation> reservations = listReservations(query);
        long total = reservations.size();
        long approved = reservations.stream().filter(item -> "APPROVED".equals(item.getStatus())).count();
        long rejected = reservations.stream().filter(item -> "REJECTED".equals(item.getStatus())).count();
        long canceled = reservations.stream().filter(item -> "CANCELED".equals(item.getStatus())).count();
        long fromQueue = reservations.stream().filter(item -> Integer.valueOf(1).equals(item.getFromQueue())).count();

        SuccessRateVO vo = new SuccessRateVO();
        vo.setTotal(total);
        vo.setApproved(approved);
        vo.setRejected(rejected);
        vo.setCanceled(canceled);
        vo.setFromQueue(fromQueue);
        vo.setSuccessRate(rate(approved, total));
        vo.setRejectRate(rate(rejected, total));
        vo.setCancelRate(rate(canceled, total));
        return vo;
    }

    @Override
    public ResourceRatioVO resourceRatio(StatisticsQueryDTO query) {
        List<Reservation> reservations = listReservations(query);
        long total = reservations.size();
        Map<Long, Laboratory> labs = labMap();
        Map<Long, Equipment> equipments = new LinkedHashMap<>();
        equipmentMapper.selectList(null).forEach(item -> equipments.put(item.getId(), item));

        Map<String, Long> counts = new LinkedHashMap<>();
        reservations.forEach(item -> {
            String key = item.getResourceType() + ":" + (Integer.valueOf(1).equals(item.getResourceType())
                    ? item.getLabId() : item.getEquipmentId());
            counts.merge(key, 1L, Long::sum);
        });
        List<ResourceRatioVO.Item> items = counts.entrySet().stream().map(entry -> {
            String[] parts = entry.getKey().split(":");
            Integer resourceType = Integer.valueOf(parts[0]);
            Long resourceId = Long.valueOf(parts[1]);
            ResourceRatioVO.Item item = new ResourceRatioVO.Item();
            item.setResourceType(resourceType);
            item.setResourceId(resourceId);
            if (Integer.valueOf(1).equals(resourceType)) {
                Laboratory lab = labs.get(resourceId);
                item.setResourceNo(lab == null ? null : lab.getLabNo());
                item.setResourceName(lab == null ? null : lab.getName());
            } else {
                Equipment equipment = equipments.get(resourceId);
                item.setResourceNo(equipment == null ? null : equipment.getResourceNo());
                item.setResourceName(equipment == null ? null : equipment.getName());
            }
            item.setCount(entry.getValue());
            item.setRatio(rate(entry.getValue(), total));
            return item;
        }).sorted(Comparator.comparing(ResourceRatioVO.Item::getCount).reversed()).limit(15).toList();

        ResourceRatioVO vo = new ResourceRatioVO();
        vo.setItems(items);
        return vo;
    }

    @Override
    public ReservationTrendVO reservationTrend(StatisticsQueryDTO query) {
        StatisticsQueryDTO q = normalize(query);
        String groupBy = StringUtils.hasText(q.getGroupBy()) ? q.getGroupBy().toLowerCase() : "day";
        List<Reservation> reservations = listReservations(q);
        Map<String, ReservationTrendVO.Item> items = new LinkedHashMap<>();
        for (Reservation reservation : reservations) {
            String period = periodOf(reservation.getReserveDate(), groupBy);
            ReservationTrendVO.Item item = items.computeIfAbsent(period, key -> {
                ReservationTrendVO.Item created = new ReservationTrendVO.Item();
                created.setPeriod(key);
                created.setTotal(0L);
                created.setApproved(0L);
                created.setRejected(0L);
                created.setCanceled(0L);
                return created;
            });
            item.setTotal(item.getTotal() + 1);
            if ("APPROVED".equals(reservation.getStatus())) {
                item.setApproved(item.getApproved() + 1);
            } else if ("REJECTED".equals(reservation.getStatus())) {
                item.setRejected(item.getRejected() + 1);
            } else if ("CANCELED".equals(reservation.getStatus())) {
                item.setCanceled(item.getCanceled() + 1);
            }
        }
        ReservationTrendVO vo = new ReservationTrendVO();
        vo.setGroupBy(groupBy);
        vo.setItems(items.values().stream().sorted(Comparator.comparing(ReservationTrendVO.Item::getPeriod)).toList());
        return vo;
    }

    // ============================================================
    // 导出
    // ============================================================

    @Override
    public String export(StatisticsQueryDTO query, OutputStream outputStream) {
        String type = StringUtils.hasText(query.getType()) ? query.getType() : "reservation";
        StatisticsQueryDTO q = normalize(query);
        switch (type) {
            case "occupancy" -> {
                OccupancyVO occupancy = occupancy(q);
                List<LabOccupancyExportVO> rows = occupancy.getByLab().stream().map(item -> {
                    LabOccupancyExportVO row = new LabOccupancyExportVO();
                    row.setLabNo(item.getLabNo());
                    row.setLabName(item.getLabName());
                    row.setOpenSlots(item.getOpenSlots());
                    row.setUsedSlots(item.getUsedSlots());
                    row.setOccupancyRate(percent(item.getOccupancyRate()));
                    return row;
                }).toList();
                EasyExcel.write(outputStream, LabOccupancyExportVO.class).sheet("资源占用率").doWrite(rows);
                return "资源占用率.xlsx";
            }
            case "experiment-type" -> {
                ExperimentTypeRatioVO ratio = experimentTypeRatio(q);
                List<ExperimentTypeExportVO> rows = ratio.getItems().stream().map(item -> {
                    ExperimentTypeExportVO row = new ExperimentTypeExportVO();
                    row.setCode(item.getCode());
                    row.setName(item.getName());
                    row.setCount(item.getCount());
                    row.setRatio(percent(item.getRatio()));
                    return row;
                }).toList();
                EasyExcel.write(outputStream, ExperimentTypeExportVO.class).sheet("实验类型占比").doWrite(rows);
                return "实验类型占比.xlsx";
            }
            case "success-rate" -> {
                SuccessRateVO rate = successRate(q);
                List<SuccessRateExportVO> rows = new ArrayList<>();
                rows.add(metric("提交总数", String.valueOf(rate.getTotal())));
                rows.add(metric("已通过", String.valueOf(rate.getApproved())));
                rows.add(metric("已驳回", String.valueOf(rate.getRejected())));
                rows.add(metric("已取消", String.valueOf(rate.getCanceled())));
                rows.add(metric("排队转正", String.valueOf(rate.getFromQueue())));
                rows.add(metric("成功率", percent(rate.getSuccessRate())));
                rows.add(metric("驳回率", percent(rate.getRejectRate())));
                rows.add(metric("取消率", percent(rate.getCancelRate())));
                EasyExcel.write(outputStream, SuccessRateExportVO.class).sheet("预约成功率").doWrite(rows);
                return "预约成功率.xlsx";
            }
            case "resource-ratio" -> {
                ResourceRatioVO ratio = resourceRatio(q);
                List<ResourceRatioExportVO> rows = ratio.getItems().stream().map(item -> {
                    ResourceRatioExportVO row = new ResourceRatioExportVO();
                    row.setResourceNo(item.getResourceNo());
                    row.setResourceName(item.getResourceName());
                    row.setCount(item.getCount());
                    row.setRatio(percent(item.getRatio()));
                    return row;
                }).toList();
                EasyExcel.write(outputStream, ResourceRatioExportVO.class).sheet("资源预约占比").doWrite(rows);
                return "资源预约占比.xlsx";
            }
            default -> {
                List<ReservationExportVO> rows = listReservations(q).stream().map(this::toExportRow).toList();
                EasyExcel.write(outputStream, ReservationExportVO.class).sheet("预约明细").doWrite(rows);
                return "预约明细.xlsx";
            }
        }
    }

    // ============================================================
    // 内部工具
    // ============================================================

    private StatisticsQueryDTO normalize(StatisticsQueryDTO query) {
        StatisticsQueryDTO q = query == null ? new StatisticsQueryDTO() : query;
        if (q.getEndDate() == null) {
            q.setEndDate(LocalDate.now());
        }
        if (q.getBeginDate() == null) {
            q.setBeginDate(q.getEndDate().withDayOfMonth(1));
        }
        if (q.getBeginDate().isAfter(q.getEndDate())) {
            throw BizException.badRequest("开始日期不能晚于结束日期");
        }
        return q;
    }

    /** 区间内符合筛选条件的预约（占用集合 + 已结束状态都要算进"提交总数"） */
    private List<Reservation> listReservations(StatisticsQueryDTO q) {
        List<Reservation> list = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .ge(Reservation::getReserveDate, q.getBeginDate())
                .le(Reservation::getReserveDate, q.getEndDate())
                .eq(q.getExperimentTypeId() != null, Reservation::getExperimentTypeId, q.getExperimentTypeId())
                .eq(q.getLabId() != null, Reservation::getLabId, q.getLabId()));
        if (!StringUtils.hasText(q.getCollege())) {
            return list;
        }
        Set<Long> applicantIds = applicantsOfCollege(q.getCollege());
        return list.stream().filter(item -> applicantIds.contains(item.getApplicantId())).toList();
    }

    /** 学院 → 申请人 id 集合（教师按教师档案，学生按班级学院） */
    private Set<Long> applicantsOfCollege(String college) {
        Set<Long> ids = teacherInfoMapper.selectList(Wrappers.<TeacherInfo>lambdaQuery()
                        .eq(TeacherInfo::getCollege, college))
                .stream().map(TeacherInfo::getUserId).collect(Collectors.toSet());
        List<StudentInfo> students = studentInfoMapper.selectList(null);
        if (!students.isEmpty()) {
            Map<Long, ClassInfo> classes = new LinkedHashMap<>();
            classInfoMapper.selectList(Wrappers.<ClassInfo>lambdaQuery().eq(ClassInfo::getCollege, college))
                    .forEach(item -> classes.put(item.getId(), item));
            students.stream()
                    .filter(student -> classes.containsKey(student.getClassId()))
                    .forEach(student -> ids.add(student.getUserId()));
        }
        return ids;
    }

    /** 某实验室在区间内的开放时段数 = Σ 每天(开放星期命中 且 时段落在开放时间窗内 的时段数) */
    private long countOpenSlots(Laboratory lab, List<TimeSlot> slots, LocalDate begin, LocalDate end) {
        long total = 0;
        for (LocalDate date = begin; !date.isAfter(end); date = date.plusDays(1)) {
            if (!weekdayUtils.contains(lab.getOpenWeekdays(), date.getDayOfWeek().getValue())) {
                continue;
            }
            for (TimeSlot slot : slots) {
                if (!slot.getStartTime().isBefore(lab.getOpenTimeStart())
                        && !slot.getEndTime().isAfter(lab.getOpenTimeEnd())) {
                    total++;
                }
            }
        }
        return total;
    }

    private Map<Long, Laboratory> labMap() {
        Map<Long, Laboratory> map = new LinkedHashMap<>();
        laboratoryMapper.selectList(null).forEach(item -> map.put(item.getId(), item));
        return map;
    }

    private String periodOf(LocalDate date, String groupBy) {
        return switch (groupBy) {
            case "month" -> YM.format(date);
            case "week" -> date.getYear() + "-W" + String.format("%02d",
                    date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            default -> YMD.format(date);
        };
    }

    private Double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        double value = (double) numerator / denominator;
        return Math.round(value * 10000) / 10000.0;
    }

    private String percent(Double value) {
        if (value == null) {
            return "0%";
        }
        return Math.round(value * 10000) / 100.0 + "%";
    }

    private SuccessRateExportVO metric(String name, String value) {
        SuccessRateExportVO row = new SuccessRateExportVO();
        row.setMetric(name);
        row.setValue(value);
        return row;
    }

    private ReservationExportVO toExportRow(Reservation item) {
        ReservationExportVO row = new ReservationExportVO();
        row.setReserveNo(item.getReserveNo());
        row.setReserveDate(item.getReserveDate() == null ? null : item.getReserveDate().toString());
        row.setUsePeople(item.getUsePeople());
        row.setCourseName(item.getCourseName());
        row.setPurpose(item.getPurpose());
        row.setStatusName(statusName(item.getStatus()));
        row.setFromQueueText(Integer.valueOf(1).equals(item.getFromQueue()) ? "是" : "否");
        row.setCreatedAt(item.getCreatedAt() == null ? null : item.getCreatedAt().toString());
        row.setResourceType(Integer.valueOf(1).equals(item.getResourceType()) ? "实验室" : "设备");
        User applicant = userMapper.selectById(item.getApplicantId());
        row.setApplicantName(applicant == null ? null : applicant.getRealName());
        if (Integer.valueOf(1).equals(item.getResourceType())) {
            Laboratory lab = laboratoryMapper.selectById(item.getLabId());
            row.setResourceName(lab == null ? null : lab.getLabNo() + " " + lab.getName());
        } else {
            Equipment equipment = equipmentMapper.selectById(item.getEquipmentId());
            row.setResourceName(equipment == null ? null : equipment.getResourceNo() + " " + equipment.getName());
        }
        TimeSlot slot = item.getSlotId() == null ? null : timeSlotMapper.selectById(item.getSlotId());
        row.setSlotName(slot == null ? null : slot.getName());
        ExperimentType type = item.getExperimentTypeId() == null
                ? null : experimentTypeMapper.selectById(item.getExperimentTypeId());
        row.setExperimentTypeName(type == null ? null : type.getName());
        row.setCollege(collegeOf(item.getApplicantId()));
        return row;
    }

    private String collegeOf(Long userId) {
        if (userId == null) {
            return null;
        }
        TeacherInfo teacher = teacherInfoMapper.selectOne(Wrappers.<TeacherInfo>lambdaQuery()
                .eq(TeacherInfo::getUserId, userId).last("limit 1"));
        if (teacher != null) {
            return teacher.getCollege();
        }
        StudentInfo student = studentInfoMapper.selectOne(Wrappers.<StudentInfo>lambdaQuery()
                .eq(StudentInfo::getUserId, userId).last("limit 1"));
        if (student == null || student.getClassId() == null) {
            return null;
        }
        ClassInfo classInfo = classInfoMapper.selectById(student.getClassId());
        return classInfo == null ? null : classInfo.getCollege();
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

    /** 当前登录用户（导出接口用它写不了日志，这里仅保留扩展点） */
    private Long currentUserId() {
        return securityUtils.getUserId();
    }
}
