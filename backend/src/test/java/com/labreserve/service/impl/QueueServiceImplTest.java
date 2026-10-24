package com.labreserve.service.impl;

import com.labreserve.common.exception.BizException;
import com.labreserve.mapper.CandidateNoticeLogMapper;
import com.labreserve.mapper.EquipmentMapper;
import com.labreserve.mapper.LaboratoryMapper;
import com.labreserve.mapper.QueueBanRecordMapper;
import com.labreserve.mapper.ReservationEquipmentMapper;
import com.labreserve.mapper.ReservationMapper;
import com.labreserve.mapper.TimeSlotMapper;
import com.labreserve.mapper.UserMapper;
import com.labreserve.model.dto.QueueCreateDTO;
import com.labreserve.model.entity.Laboratory;
import com.labreserve.model.entity.QueueBanRecord;
import com.labreserve.model.entity.Reservation;
import com.labreserve.model.entity.TimeSlot;
import com.labreserve.util.RedisLockTemplate;
import com.labreserve.util.RedisUtils;
import com.labreserve.util.ReserveNoUtils;
import com.labreserve.util.SecurityUtils;
import com.labreserve.mapper.ResourceQueueMapper;
import com.labreserve.service.MessageService;
import com.labreserve.util.AfterCommitUtils;
import com.labreserve.config.QueueProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * {@link QueueServiceImpl} 入队前置校验单元测试。
 *
 * <p>覆盖 plan.md §8.1 要求的四条硬规则：
 * 角色（学生不能排实验室的队）、禁入、有位置不排队、重复入队。
 * 顺位与顺延链路是 Redis + DB 的行为，放在迭代 6 的验收脚本里跑真环境。
 */
@ExtendWith(MockitoExtension.class)
class QueueServiceImplTest {

    private static final Long USER_ID = 8L;
    private static final Long LAB_ID = 1L;
    private static final Long SLOT_ID = 2L;
    private static final LocalDate DATE = LocalDate.now().plusDays(3);

    @Mock
    private ResourceQueueMapper queueMapper;
    @Mock
    private CandidateNoticeLogMapper noticeLogMapper;
    @Mock
    private QueueBanRecordMapper queueBanRecordMapper;
    @Mock
    private ReservationMapper reservationMapper;
    @Mock
    private ReservationEquipmentMapper reservationEquipmentMapper;
    @Mock
    private LaboratoryMapper laboratoryMapper;
    @Mock
    private EquipmentMapper equipmentMapper;
    @Mock
    private TimeSlotMapper timeSlotMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private com.labreserve.service.QueueCandidateService queueCandidateService;
    @Mock
    private RedisLockTemplate redisLockTemplate;
    @Mock
    private RedisUtils redisUtils;
    @Mock
    private ReserveNoUtils reserveNoUtils;
    @Mock
    private MessageService messageService;
    @Mock
    private AfterCommitUtils afterCommitUtils;
    @Mock
    private QueueProperties queueProperties;
    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private QueueServiceImpl queueService;

    private QueueCreateDTO dto() {
        QueueCreateDTO dto = new QueueCreateDTO();
        dto.setResourceType(1);
        dto.setLabId(LAB_ID);
        dto.setReserveDate(DATE);
        dto.setSlotId(SLOT_ID);
        return dto;
    }

    private TimeSlot slot() {
        TimeSlot slot = new TimeSlot();
        slot.setId(SLOT_ID);
        slot.setStatus(1);
        slot.setName("3-4节");
        slot.setStartTime(LocalTime.of(10, 0));
        slot.setEndTime(LocalTime.of(11, 40));
        return slot;
    }

    private Laboratory lab() {
        Laboratory lab = new Laboratory();
        lab.setId(LAB_ID);
        lab.setLabNo("LAB-01");
        lab.setName("计算机基础实验室");
        lab.setStatus(1);
        return lab;
    }

    @Test
    @DisplayName("学生排实验室的队 -> LAB_RESERVE_TEACHER_ONLY")
    void studentCannotQueueLab() {
        when(securityUtils.getUserId()).thenReturn(USER_ID);
        when(securityUtils.getRoleCode()).thenReturn("STUDENT");

        BizException ex = assertThrows(BizException.class, () -> queueService.create(dto()));
        assertEquals("实验室由教师为班级排课，学生无需排队", ex.getMessage());
    }

    @Test
    @DisplayName("禁入期内入队 -> QUEUE_BANNED")
    void bannedCannotQueue() {
        when(securityUtils.getUserId()).thenReturn(USER_ID);
        when(securityUtils.getRoleCode()).thenReturn("TEACHER");
        when(laboratoryMapper.selectById(LAB_ID)).thenReturn(lab());
        when(timeSlotMapper.selectById(SLOT_ID)).thenReturn(slot());
        QueueBanRecord ban = new QueueBanRecord();
        ban.setEndTime(java.time.LocalDateTime.now().plusDays(3));
        when(queueBanRecordMapper.selectOne(any())).thenReturn(ban);

        BizException ex = assertThrows(BizException.class, () -> queueService.create(dto()));
        assertEquals(true, ex.getMessage().startsWith("你已被限制加入该资源排队"));
    }

    @Test
    @DisplayName("时段空闲时入队 -> SLOT_AVAILABLE（应该直接预约）")
    void availableSlotCannotQueue() {
        when(securityUtils.getUserId()).thenReturn(USER_ID);
        when(securityUtils.getRoleCode()).thenReturn("TEACHER");
        when(laboratoryMapper.selectById(LAB_ID)).thenReturn(lab());
        when(timeSlotMapper.selectById(SLOT_ID)).thenReturn(slot());
        when(queueBanRecordMapper.selectOne(any())).thenReturn(null);
        when(reservationMapper.selectList(any())).thenReturn(List.of());

        BizException ex = assertThrows(BizException.class, () -> queueService.create(dto()));
        assertEquals("该时段当前可直接预约，无需排队", ex.getMessage());
    }

    @Test
    @DisplayName("重复入队 -> ALREADY_QUEUED（锁内检测）")
    void duplicateQueueRejected() {
        when(securityUtils.getUserId()).thenReturn(USER_ID);
        when(securityUtils.getRoleCode()).thenReturn("TEACHER");
        when(laboratoryMapper.selectById(LAB_ID)).thenReturn(lab());
        when(timeSlotMapper.selectById(SLOT_ID)).thenReturn(slot());
        when(queueBanRecordMapper.selectOne(any())).thenReturn(null);
        Reservation occupying = new Reservation();
        occupying.setId(1L);
        occupying.setLabId(LAB_ID);
        occupying.setEquipmentCount(1);
        when(reservationMapper.selectList(any())).thenReturn(List.of(occupying));
        lenient().when(redisUtils.zRank(anyString(), anyString())).thenReturn(0L);
        // 锁直接放行
        lenient().when(redisLockTemplate.execute(anyString(), any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        // 已存在活动队列
        com.labreserve.model.entity.ResourceQueue existing = new com.labreserve.model.entity.ResourceQueue();
        existing.setId(9L);
        existing.setApplicantId(USER_ID);
        existing.setStatus(com.labreserve.model.entity.ResourceQueue.QUEUING);
        when(queueMapper.selectOne(any())).thenReturn(existing);

        BizException ex = assertThrows(BizException.class, () -> queueService.create(dto()));
        assertEquals(true, ex.getMessage().startsWith("你已在队列中"));
    }
}
