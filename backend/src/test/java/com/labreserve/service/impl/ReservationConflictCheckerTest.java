package com.labreserve.service.impl;

import com.labreserve.mapper.EquipmentMapper;
import com.labreserve.mapper.LaboratoryMapper;
import com.labreserve.mapper.QueueBanRecordMapper;
import com.labreserve.mapper.ReservationEquipmentMapper;
import com.labreserve.mapper.ReservationMapper;
import com.labreserve.mapper.TimeSlotMapper;
import com.labreserve.model.entity.Equipment;
import com.labreserve.model.entity.Laboratory;
import com.labreserve.model.entity.Reservation;
import com.labreserve.model.entity.TimeSlot;
import com.labreserve.model.vo.ReservationCheckVO;
import com.labreserve.util.RedisUtils;
import com.labreserve.util.WeekdayUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * {@link ReservationConflictChecker} 单元测试。
 *
 * <p>覆盖迭代 5 的三类核心校验：
 * <ol>
 *   <li>角色与班级（学生约实验室 / 教师不选班级）</li>
 *   <li>实验类型与实验室匹配、实验室停用</li>
 *   <li>占用与库存边界（时段被占、库存刚好占满 / 差一台）</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ReservationConflictCheckerTest {

    private static final Long LAB_ID = 1L;
    private static final Long EQUIP_ID = 10L;
    private static final Long SLOT_ID = 2L;
    private static final Long USER_ID = 5L;
    private static final LocalDate DATE = LocalDate.now().plusDays(3);

    @Mock
    private LaboratoryMapper laboratoryMapper;
    @Mock
    private EquipmentMapper equipmentMapper;
    @Mock
    private TimeSlotMapper timeSlotMapper;
    @Mock
    private ReservationMapper reservationMapper;
    @Mock
    private ReservationEquipmentMapper reservationEquipmentMapper;
    @Mock
    private QueueBanRecordMapper queueBanRecordMapper;
    @Mock
    private RedisUtils redisUtils;

    @Spy
    private WeekdayUtils weekdayUtils = new WeekdayUtils();

    @InjectMocks
    private ReservationConflictChecker checker;

    private TimeSlot slot() {
        TimeSlot slot = new TimeSlot();
        slot.setId(SLOT_ID);
        slot.setName("3-4节");
        slot.setStartTime(LocalTime.of(10, 0));
        slot.setEndTime(LocalTime.of(11, 40));
        slot.setStatus(1);
        return slot;
    }

    private Laboratory lab(int capacity, Long experimentTypeId) {
        Laboratory lab = new Laboratory();
        lab.setId(LAB_ID);
        lab.setLabNo("LAB-01");
        lab.setName("计算机基础实验室");
        lab.setStatus(1);
        lab.setCapacity(capacity);
        lab.setExperimentTypeId(experimentTypeId);
        lab.setOpenTimeStart(LocalTime.of(8, 0));
        lab.setOpenTimeEnd(LocalTime.of(18, 0));
        lab.setOpenWeekdays("1,2,3,4,5,6,7");
        return lab;
    }

    private ReservationConflictChecker.Params labParams(String roleCode, List<Long> classIds, Long experimentTypeId) {
        return new ReservationConflictChecker.Params(1, LAB_ID, null, null, DATE, SLOT_ID,
                30, experimentTypeId, classIds, roleCode, USER_ID, true, null);
    }

    private ReservationConflictChecker.Params equipParams(String roleCode) {
        return new ReservationConflictChecker.Params(2, LAB_ID, EQUIP_ID, null, DATE, SLOT_ID,
                1, 2L, null, roleCode, USER_ID, true, null);
    }

    @Test
    @DisplayName("学生预约实验室 -> LAB_RESERVE_TEACHER_ONLY")
    void studentCannotReserveLab() {
        lenient().when(timeSlotMapper.selectById(SLOT_ID)).thenReturn(slot());

        ReservationConflictChecker.Result result = checker.check(
                labParams("STUDENT", List.of(1L), 2L));

        assertFalse(result.ok());
        assertEquals("LAB_RESERVE_TEACHER_ONLY", result.conflict().getCode());
    }

    @Test
    @DisplayName("教师不选上课班级 -> CLASS_REQUIRED")
    void teacherNeedsClasses() {
        lenient().when(timeSlotMapper.selectById(SLOT_ID)).thenReturn(slot());

        ReservationConflictChecker.Result result = checker.check(labParams("TEACHER", List.of(), 2L));

        assertFalse(result.ok());
        assertEquals("CLASS_REQUIRED", result.conflict().getCode());
    }

    @Test
    @DisplayName("实验类型与实验室不匹配 -> EXPERIMENT_TYPE_NOT_SUPPORTED")
    void experimentTypeMustMatchLab() {
        when(timeSlotMapper.selectById(SLOT_ID)).thenReturn(slot());
        when(laboratoryMapper.selectById(LAB_ID)).thenReturn(lab(60, 2L));

        ReservationConflictChecker.Result result = checker.check(labParams("TEACHER", List.of(1L), 3L));

        assertFalse(result.ok());
        assertEquals("EXPERIMENT_TYPE_NOT_SUPPORTED", result.conflict().getCode());
    }

    @Test
    @DisplayName("实验室停用 -> LAB_DISABLED")
    void disabledLab() {
        Laboratory lab = lab(60, 2L);
        lab.setStatus(0);
        when(timeSlotMapper.selectById(SLOT_ID)).thenReturn(slot());
        when(laboratoryMapper.selectById(LAB_ID)).thenReturn(lab);

        ReservationConflictChecker.Result result = checker.check(labParams("TEACHER", List.of(1L), 2L));

        assertEquals("LAB_DISABLED", result.conflict().getCode());
    }

    @Test
    @DisplayName("时段已被预约 -> SLOT_OCCUPIED，并带出排队人数")
    void labSlotOccupied() {
        when(timeSlotMapper.selectById(SLOT_ID)).thenReturn(slot());
        when(laboratoryMapper.selectById(LAB_ID)).thenReturn(lab(60, 2L));
        when(reservationMapper.selectCount(any())).thenReturn(1L);
        when(redisUtils.zCard(any())).thenReturn(3L);

        ReservationConflictChecker.Result result = checker.check(labParams("TEACHER", List.of(1L), 2L));

        assertEquals("SLOT_OCCUPIED", result.conflict().getCode());
        assertEquals(3L, result.conflict().getQueueCount());
    }

    @Test
    @DisplayName("设备库存刚好占满（used 4 + need 1 = 5）-> 可通过")
    void equipmentStockBoundaryOk() {
        Equipment equipment = new Equipment();
        equipment.setId(EQUIP_ID);
        equipment.setResourceNo("ZY-01-001");
        equipment.setStatus(1);
        equipment.setStock(5);

        when(timeSlotMapper.selectById(SLOT_ID)).thenReturn(slot());
        when(equipmentMapper.selectById(EQUIP_ID)).thenReturn(equipment);
        Reservation occupying = new Reservation();
        occupying.setId(1L);
        occupying.setEquipmentId(EQUIP_ID);
        occupying.setEquipmentCount(4);
        when(reservationMapper.selectList(any())).thenReturn(List.of(occupying));
        lenient().when(reservationEquipmentMapper.selectList(any())).thenReturn(List.of());
        when(reservationMapper.selectCount(any())).thenReturn(0L);

        ReservationConflictChecker.Result result = checker.check(equipParams("STUDENT"));

        assertTrue(result.ok(), () -> "expected ok but got " + result.conflict());
    }

    @Test
    @DisplayName("设备库存差一台（used 5 + need 1 > 5）-> STOCK_NOT_ENOUGH")
    void equipmentStockNotEnough() {
        Equipment equipment = new Equipment();
        equipment.setId(EQUIP_ID);
        equipment.setResourceNo("ZY-01-001");
        equipment.setStatus(1);
        equipment.setStock(5);

        when(timeSlotMapper.selectById(SLOT_ID)).thenReturn(slot());
        when(equipmentMapper.selectById(EQUIP_ID)).thenReturn(equipment);
        Reservation occupying = new Reservation();
        occupying.setId(1L);
        occupying.setEquipmentId(EQUIP_ID);
        occupying.setEquipmentCount(5);
        when(reservationMapper.selectList(any())).thenReturn(List.of(occupying));
        lenient().when(reservationEquipmentMapper.selectList(any())).thenReturn(List.of());

        ReservationConflictChecker.Result result = checker.check(equipParams("STUDENT"));

        assertEquals("STOCK_NOT_ENOUGH", result.conflict().getCode());
    }

    @Test
    @DisplayName("设备维修中 -> EQUIPMENT_UNAVAILABLE")
    void equipmentUnavailable() {
        Equipment equipment = new Equipment();
        equipment.setId(EQUIP_ID);
        equipment.setStatus(2);
        equipment.setStock(5);

        when(timeSlotMapper.selectById(SLOT_ID)).thenReturn(slot());
        when(equipmentMapper.selectById(EQUIP_ID)).thenReturn(equipment);

        ReservationConflictChecker.Result result = checker.check(equipParams("STUDENT"));

        assertEquals("EQUIPMENT_UNAVAILABLE", result.conflict().getCode());
    }

    @Test
    @DisplayName("预约日期早于今天 -> NOT_IN_OPEN_TIME")
    void pastDate() {
        ReservationConflictChecker.Params params = new ReservationConflictChecker.Params(1, LAB_ID, null, null,
                LocalDate.now().minusDays(1), SLOT_ID, 30, 2L, List.of(1L), "TEACHER", USER_ID, true, null);

        ReservationConflictChecker.Result result = checker.check(params);

        assertEquals("NOT_IN_OPEN_TIME", result.conflict().getCode());
    }

    @Test
    @DisplayName("toCheckVO：只有占用/库存不足允许排队")
    void canQueueOnlyForOccupancy() {
        ReservationCheckVO.ConflictVO occupied = new ReservationCheckVO.ConflictVO();
        occupied.setCode("SLOT_OCCUPIED");
        ReservationCheckVO.ConflictVO banned = new ReservationCheckVO.ConflictVO();
        banned.setCode("QUEUE_BANNED");

        assertTrue(ReservationConflictChecker.toCheckVO(
                new ReservationConflictChecker.Result(occupied, null)).getCanQueue());
        assertFalse(ReservationConflictChecker.toCheckVO(
                new ReservationConflictChecker.Result(banned, null)).getCanQueue());
        lenient().when(timeSlotMapper.selectById(anyLong())).thenReturn(slot());
    }
}
