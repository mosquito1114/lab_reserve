package com.labreserve.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labreserve.common.constant.CommonConst;
import com.labreserve.common.constant.RedisKeyConst;
import com.labreserve.common.enums.MessageType;
import com.labreserve.common.exception.BizException;
import com.labreserve.common.result.PageResult;
import com.labreserve.mapper.EquipmentMapper;
import com.labreserve.mapper.LaboratoryMapper;
import com.labreserve.mapper.RepairOrderMapper;
import com.labreserve.mapper.RepairRecordMapper;
import com.labreserve.mapper.RoleMapper;
import com.labreserve.mapper.UserMapper;
import com.labreserve.model.dto.RepairAcceptDTO;
import com.labreserve.model.dto.RepairAssignDTO;
import com.labreserve.model.dto.RepairCreateDTO;
import com.labreserve.model.dto.RepairPageDTO;
import com.labreserve.model.dto.RepairProgressDTO;
import com.labreserve.model.dto.RepairReviewDTO;
import com.labreserve.model.entity.Equipment;
import com.labreserve.model.entity.Laboratory;
import com.labreserve.model.entity.RepairOrder;
import com.labreserve.model.entity.RepairRecord;
import com.labreserve.model.entity.Role;
import com.labreserve.model.entity.User;
import com.labreserve.model.vo.RepairOrderDetailVO;
import com.labreserve.model.vo.RepairOrderVO;
import com.labreserve.model.vo.RepairRecordVO;
import com.labreserve.security.PermissionService;
import com.labreserve.service.MessageService;
import com.labreserve.service.RepairService;
import com.labreserve.util.AfterCommitUtils;
import com.labreserve.util.RedisUtils;
import com.labreserve.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 设备报修实现。
 */
@Slf4j
@Service
public class RepairServiceImpl implements RepairService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private RepairOrderMapper repairOrderMapper;

    @Autowired
    private RepairRecordMapper repairRecordMapper;

    @Autowired
    private EquipmentMapper equipmentMapper;

    @Autowired
    private LaboratoryMapper laboratoryMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private SecurityUtils securityUtils;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private AfterCommitUtils afterCommitUtils;

    @Autowired
    private RedisUtils redisUtils;

    // ============================================================
    // 提交 / 查询
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RepairOrderVO create(RepairCreateDTO dto) {
        Equipment equipment = equipmentMapper.selectById(dto.getEquipmentId());
        if (equipment == null) {
            throw BizException.notFound("设备不存在");
        }
        Long userId = securityUtils.getUserId();

        RepairOrder order = new RepairOrder();
        order.setOrderNo(nextOrderNo());
        order.setSubmitterId(userId);
        order.setEquipmentId(equipment.getId());
        order.setFaultDesc(dto.getFaultDesc());
        order.setFaultScene(dto.getFaultScene());
        order.setFaultImages(CollectionUtils.isEmpty(dto.getFaultImages())
                ? null : String.join(",", dto.getFaultImages()));
        order.setPriority(2);
        order.setStatus(RepairOrder.SUBMITTED);
        repairOrderMapper.insert(order);

        // 通知所有实验室管理员（他们负责复核）
        notifyRole(CommonConst.ROLE_LAB_ADMIN, "新的报修工单",
                "设备 " + equipment.getResourceNo() + " 有新的报修工单 " + order.getOrderNo() + "，请及时复核。",
                order.getId());
        log.info("[报修] 提交 id={}, no={}, equipment={}", order.getId(), order.getOrderNo(), equipment.getResourceNo());
        return toVOList(List.of(order)).get(0);
    }

    @Override
    public PageResult<RepairOrderVO> page(RepairPageDTO dto) {
        LambdaQueryWrapper<RepairOrder> wrapper = baseWrapper(dto);
        Long userId = securityUtils.getUserId();
        // 没有 repair:list（管理端）权限的，只能看自己提交的
        if (!permissionService.hasPermission("repair:list")) {
            wrapper.eq(RepairOrder::getSubmitterId, userId);
        } else if (Boolean.TRUE.equals(dto.getOnlyMine())) {
            wrapper.eq(RepairOrder::getSubmitterId, userId);
        }
        Page<RepairOrder> page = repairOrderMapper.selectPage(dto.toMpPage(), wrapper);
        return PageResult.of(toVOList(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PageResult<RepairOrderVO> myAssignedPage(RepairPageDTO dto) {
        Long userId = securityUtils.getUserId();
        LambdaQueryWrapper<RepairOrder> wrapper = baseWrapper(dto).eq(RepairOrder::getAssigneeId, userId);
        Page<RepairOrder> page = repairOrderMapper.selectPage(dto.toMpPage(), wrapper);
        return PageResult.of(toVOList(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public RepairOrderDetailVO getInfo(Long id) {
        RepairOrder order = requireExists(id);
        Long userId = securityUtils.getUserId();
        boolean allowed = Objects.equals(order.getSubmitterId(), userId)
                || Objects.equals(order.getAssigneeId(), userId)
                || permissionService.hasPermission("repair:list")
                || permissionService.hasPermission("repair:list-assigned");
        if (!allowed) {
            throw BizException.forbidden("无权查看该工单");
        }
        List<RepairOrderVO> list = toVOList(List.of(order));
        RepairOrderDetailVO detail = new RepairOrderDetailVO();
        RepairOrderVO base = list.get(0);
        detail.setId(base.getId());
        detail.setOrderNo(base.getOrderNo());
        detail.setSubmitterId(base.getSubmitterId());
        detail.setSubmitterName(base.getSubmitterName());
        detail.setEquipmentId(base.getEquipmentId());
        detail.setEquipmentNo(base.getEquipmentNo());
        detail.setEquipmentName(base.getEquipmentName());
        detail.setLabNo(base.getLabNo());
        detail.setFaultDesc(base.getFaultDesc());
        detail.setFaultScene(base.getFaultScene());
        detail.setPriority(base.getPriority());
        detail.setPriorityName(base.getPriorityName());
        detail.setStatus(base.getStatus());
        detail.setStatusName(base.getStatusName());
        detail.setReviewerId(base.getReviewerId());
        detail.setReviewerName(base.getReviewerName());
        detail.setReviewTime(base.getReviewTime());
        detail.setReviewOpinion(base.getReviewOpinion());
        detail.setAssigneeId(base.getAssigneeId());
        detail.setAssigneeName(base.getAssigneeName());
        detail.setAssignTime(base.getAssignTime());
        detail.setAcceptTime(base.getAcceptTime());
        detail.setAcceptResult(base.getAcceptResult());
        detail.setAcceptOpinion(base.getAcceptOpinion());
        detail.setArchiveTime(base.getArchiveTime());
        detail.setCreatedAt(base.getCreatedAt());
        detail.setFaultImages(StringUtils.hasText(order.getFaultImages())
                ? Arrays.stream(order.getFaultImages().split(",")).filter(StringUtils::hasText).toList()
                : Collections.emptyList());
        detail.setRecords(recordList(order.getId()));
        return detail;
    }

    @Override
    public List<RepairRecordVO> recordList(Long orderId) {
        requireExists(orderId);
        List<RepairRecord> records = repairRecordMapper.selectList(Wrappers.<RepairRecord>lambdaQuery()
                .eq(RepairRecord::getOrderId, orderId)
                .orderByAsc(RepairRecord::getId));
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, User> users = loadUsers(records.stream().map(RepairRecord::getMaintainerId).distinct().toList());
        return records.stream().map(item -> {
            RepairRecordVO vo = new RepairRecordVO();
            vo.setId(item.getId());
            vo.setOrderId(item.getOrderId());
            vo.setMaintainerId(item.getMaintainerId());
            User user = users.get(item.getMaintainerId());
            vo.setMaintainerName(user == null ? null : user.getRealName());
            vo.setStatus(item.getStatus());
            vo.setStatusName(Integer.valueOf(RepairRecord.STATUS_DONE).equals(item.getStatus()) ? "已完成" : "维修中");
            vo.setRepairDesc(item.getRepairDesc());
            vo.setPartsReplaced(item.getPartsReplaced());
            vo.setCreatedAt(item.getCreatedAt());
            return vo;
        }).toList();
    }

    // ============================================================
    // 状态流转
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void review(RepairReviewDTO dto) {
        RepairOrder order = requireExists(dto.getId());
        if (!RepairOrder.SUBMITTED.equals(order.getStatus()) && !RepairOrder.REOPENED.equals(order.getStatus())) {
            throw BizException.conflict("只有待复核 / 已重开的工单可以复核（当前：" + statusName(order.getStatus()) + "）");
        }
        boolean rejected = "REJECTED".equals(dto.getResult());
        if (rejected && !StringUtils.hasText(dto.getOpinion())) {
            throw BizException.badRequest("驳回必须填写原因");
        }
        RepairOrder update = new RepairOrder();
        update.setId(order.getId());
        update.setReviewerId(securityUtils.getUserId());
        update.setReviewTime(LocalDateTime.now());
        update.setReviewOpinion(dto.getOpinion());
        update.setPriority(dto.getPriority() == null ? order.getPriority() : dto.getPriority());
        if (rejected) {
            update.setStatus(RepairOrder.REJECTED);
        }
        repairOrderMapper.updateById(update);

        if (!rejected && dto.getEquipmentStatus() != null) {
            Equipment equipment = new Equipment();
            equipment.setId(order.getEquipmentId());
            equipment.setStatus(dto.getEquipmentStatus());
            equipmentMapper.updateById(equipment);
        }
        String title = rejected ? "报修工单已驳回" : "报修工单已复核";
        String content = rejected
                ? "你的报修工单 " + order.getOrderNo() + " 被驳回：" + dto.getOpinion()
                : "你的报修工单 " + order.getOrderNo() + " 已确认故障（优先级：" + priorityName(update.getPriority()) + "），等待派单。";
        message(order.getSubmitterId(), title, content, order.getId());
        log.info("[报修] 复核 id={}, result={}", order.getId(), dto.getResult());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(RepairAssignDTO dto) {
        RepairOrder order = requireExists(dto.getId());
        if (RepairOrder.ACCEPTED.equals(order.getStatus()) || RepairOrder.REJECTED.equals(order.getStatus())) {
            throw BizException.conflict("该工单已归档或已驳回，不能派单（当前：" + statusName(order.getStatus()) + "）");
        }
        User assignee = userMapper.selectById(dto.getAssigneeId());
        if (assignee == null) {
            throw BizException.notFound("维修人员不存在");
        }
        if (!Integer.valueOf(1).equals(assignee.getStatus())) {
            throw BizException.conflict("该维修人员账号已停用，不能派单");
        }
        Role role = roleMapper.selectById(assignee.getRoleId());
        if (role == null || !CommonConst.ROLE_MAINTAINER.equals(role.getCode())) {
            throw BizException.badRequest("只能派给维修人员（MAINTAINER）");
        }
        RepairOrder update = new RepairOrder();
        update.setId(order.getId());
        update.setAssigneeId(assignee.getId());
        update.setAssignTime(LocalDateTime.now());
        update.setStatus(RepairOrder.ASSIGNED);
        repairOrderMapper.updateById(update);

        Equipment equipment = equipmentMapper.selectById(order.getEquipmentId());
        message(assignee.getId(), "你有一个新的维修任务",
                "工单 " + order.getOrderNo() + "（设备 "
                        + (equipment == null ? order.getEquipmentId() : equipment.getResourceNo())
                        + "）已指派给你，请及时处理。", order.getId());
        message(order.getSubmitterId(), "报修工单已派单",
                "你的报修工单 " + order.getOrderNo() + " 已指派给 " + assignee.getRealName() + "。", order.getId());
        log.info("[报修] 派单 id={}, assignee={}", order.getId(), assignee.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProgress(RepairProgressDTO dto) {
        RepairOrder order = requireAssignee(dto.getId());
        if (!RepairOrder.ASSIGNED.equals(order.getStatus())
                && !RepairOrder.REPAIRING.equals(order.getStatus())
                && !RepairOrder.REOPENED.equals(order.getStatus())) {
            throw BizException.conflict("当前状态不能更新维修进度（" + statusName(order.getStatus()) + "）");
        }
        RepairOrder update = new RepairOrder();
        update.setId(order.getId());
        update.setStatus(RepairOrder.REPAIRING);
        repairOrderMapper.updateById(update);

        RepairRecord record = new RepairRecord();
        record.setOrderId(order.getId());
        record.setMaintainerId(securityUtils.getUserId());
        record.setStatus(RepairRecord.STATUS_REPAIRING);
        record.setRepairDesc(dto.getRepairDesc());
        record.setPartsReplaced(dto.getPartsReplaced());
        repairRecordMapper.insert(record);

        message(order.getSubmitterId(), "维修进度更新",
                "你的报修工单 " + order.getOrderNo() + " 维修中：" + dto.getRepairDesc(), order.getId());
        log.info("[报修] 更新进度 id={}", order.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(RepairProgressDTO dto) {
        RepairOrder order = requireAssignee(dto.getId());
        if (!RepairOrder.REPAIRING.equals(order.getStatus())
                && !RepairOrder.REOPENED.equals(order.getStatus())
                && !RepairOrder.ASSIGNED.equals(order.getStatus())) {
            throw BizException.conflict("当前状态不能完成维修（" + statusName(order.getStatus()) + "）");
        }
        if (!StringUtils.hasText(dto.getRepairDesc())) {
            throw BizException.badRequest("请填写维修说明");
        }
        RepairOrder update = new RepairOrder();
        update.setId(order.getId());
        update.setStatus(RepairOrder.REPAIRED);
        repairOrderMapper.updateById(update);

        RepairRecord record = new RepairRecord();
        record.setOrderId(order.getId());
        record.setMaintainerId(securityUtils.getUserId());
        record.setStatus(RepairRecord.STATUS_DONE);
        record.setRepairDesc(dto.getRepairDesc());
        record.setPartsReplaced(dto.getPartsReplaced());
        repairRecordMapper.insert(record);

        notifyRole(CommonConst.ROLE_LAB_ADMIN, "维修完成待验收",
                "工单 " + order.getOrderNo() + " 已由维修人员完成，请及时验收。", order.getId());
        message(order.getSubmitterId(), "维修已完成",
                "你的报修工单 " + order.getOrderNo() + " 已维修完成，等待管理员验收。", order.getId());
        log.info("[报修] 完成维修 id={}", order.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void accept(RepairAcceptDTO dto) {
        RepairOrder order = requireExists(dto.getId());
        if (!RepairOrder.REPAIRED.equals(order.getStatus())) {
            throw BizException.conflict("只有已维修完成的工单可以验收（当前：" + statusName(order.getStatus()) + "）");
        }
        boolean pass = "PASS".equals(dto.getResult());
        RepairOrder update = new RepairOrder();
        update.setId(order.getId());
        update.setAcceptUserId(securityUtils.getUserId());
        update.setAcceptTime(LocalDateTime.now());
        update.setAcceptResult(pass ? 1 : 2);
        update.setAcceptOpinion(dto.getOpinion());
        if (pass) {
            update.setStatus(RepairOrder.ACCEPTED);
            update.setArchiveTime(LocalDateTime.now());
        } else {
            update.setStatus(RepairOrder.REOPENED);
        }
        repairOrderMapper.updateById(update);

        if (pass) {
            Equipment equipment = new Equipment();
            equipment.setId(order.getEquipmentId());
            equipment.setStatus(1);
            equipmentMapper.updateById(equipment);
        }
        if (order.getAssigneeId() != null) {
            message(order.getAssigneeId(), pass ? "维修通过验收" : "维修未通过验收",
                    pass ? "工单 " + order.getOrderNo() + " 已验收通过，辛苦啦。"
                            : "工单 " + order.getOrderNo() + " 验收未通过："
                              + (StringUtils.hasText(dto.getOpinion()) ? dto.getOpinion() : "请继续处理"),
                    order.getId());
        }
        message(order.getSubmitterId(), pass ? "报修已验收归档" : "报修将重新维修",
                pass ? "你的报修工单 " + order.getOrderNo() + " 已验收通过并归档。"
                        : "你的报修工单 " + order.getOrderNo() + " 验收未通过：" + dto.getOpinion(),
                order.getId());
        log.info("[报修] 验收 id={}, result={}", order.getId(), dto.getResult());
    }

    // ============================================================
    // 内部工具
    // ============================================================

    private RepairOrder requireExists(Long id) {
        RepairOrder order = id == null ? null : repairOrderMapper.selectById(id);
        if (order == null) {
            throw BizException.notFound("工单不存在");
        }
        return order;
    }

    /** 只有被指派的维修人能操作 */
    private RepairOrder requireAssignee(Long id) {
        RepairOrder order = requireExists(id);
        if (!Objects.equals(order.getAssigneeId(), securityUtils.getUserId())) {
            throw BizException.forbidden("只能操作指派给自己的工单");
        }
        return order;
    }

    private LambdaQueryWrapper<RepairOrder> baseWrapper(RepairPageDTO dto) {
        LambdaQueryWrapper<RepairOrder> wrapper = Wrappers.<RepairOrder>lambdaQuery()
                .eq(StringUtils.hasText(dto.getStatus()), RepairOrder::getStatus, dto.getStatus())
                .eq(dto.getPriority() != null, RepairOrder::getPriority, dto.getPriority())
                .eq(dto.getEquipmentId() != null, RepairOrder::getEquipmentId, dto.getEquipmentId())
                .eq(dto.getSubmitterId() != null, RepairOrder::getSubmitterId, dto.getSubmitterId())
                .eq(dto.getAssigneeId() != null, RepairOrder::getAssigneeId, dto.getAssigneeId())
                .ge(dto.getBeginTime() != null, RepairOrder::getCreatedAt, dto.getBeginTime())
                .le(dto.getEndTime() != null, RepairOrder::getCreatedAt, dto.getEndTime())
                .orderByDesc(RepairOrder::getId);
        if (StringUtils.hasText(dto.getEquipmentNo())) {
            List<Long> ids = equipmentMapper.selectList(Wrappers.<Equipment>lambdaQuery()
                            .like(Equipment::getResourceNo, dto.getEquipmentNo()))
                    .stream().map(Equipment::getId).toList();
            if (ids.isEmpty()) {
                wrapper.eq(RepairOrder::getId, -1L);
            } else {
                wrapper.in(RepairOrder::getEquipmentId, ids);
            }
        }
        return wrapper;
    }

    private List<RepairOrderVO> toVOList(List<RepairOrder> list) {
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Equipment> equipments = new LinkedHashMap<>();
        equipmentMapper.selectBatchIds(list.stream().map(RepairOrder::getEquipmentId)
                        .filter(Objects::nonNull).distinct().toList())
                .forEach(item -> equipments.put(item.getId(), item));
        Map<Long, Laboratory> labs = new LinkedHashMap<>();
        List<Long> labIds = equipments.values().stream().map(Equipment::getLabId)
                .filter(Objects::nonNull).distinct().toList();
        if (!labIds.isEmpty()) {
            laboratoryMapper.selectBatchIds(labIds).forEach(item -> labs.put(item.getId(), item));
        }
        List<Long> userIds = new java.util.ArrayList<>();
        list.forEach(item -> {
            if (item.getSubmitterId() != null) {
                userIds.add(item.getSubmitterId());
            }
            if (item.getAssigneeId() != null) {
                userIds.add(item.getAssigneeId());
            }
            if (item.getReviewerId() != null) {
                userIds.add(item.getReviewerId());
            }
        });
        Map<Long, User> users = loadUsers(userIds);

        return list.stream().map(item -> {
            RepairOrderVO vo = new RepairOrderVO();
            vo.setId(item.getId());
            vo.setOrderNo(item.getOrderNo());
            vo.setSubmitterId(item.getSubmitterId());
            User submitter = users.get(item.getSubmitterId());
            vo.setSubmitterName(submitter == null ? null : submitter.getRealName());
            vo.setEquipmentId(item.getEquipmentId());
            Equipment equipment = equipments.get(item.getEquipmentId());
            if (equipment != null) {
                vo.setEquipmentNo(equipment.getResourceNo());
                vo.setEquipmentName(equipment.getName());
                Laboratory lab = labs.get(equipment.getLabId());
                vo.setLabNo(lab == null ? null : lab.getLabNo());
            }
            vo.setFaultDesc(item.getFaultDesc());
            vo.setFaultScene(item.getFaultScene());
            vo.setPriority(item.getPriority());
            vo.setPriorityName(priorityName(item.getPriority()));
            vo.setStatus(item.getStatus());
            vo.setStatusName(statusName(item.getStatus()));
            vo.setReviewerId(item.getReviewerId());
            User reviewer = users.get(item.getReviewerId());
            vo.setReviewerName(reviewer == null ? null : reviewer.getRealName());
            vo.setReviewTime(item.getReviewTime());
            vo.setReviewOpinion(item.getReviewOpinion());
            vo.setAssigneeId(item.getAssigneeId());
            User assignee = users.get(item.getAssigneeId());
            vo.setAssigneeName(assignee == null ? null : assignee.getRealName());
            vo.setAssignTime(item.getAssignTime());
            vo.setAcceptTime(item.getAcceptTime());
            vo.setAcceptResult(item.getAcceptResult());
            vo.setAcceptOpinion(item.getAcceptOpinion());
            vo.setArchiveTime(item.getArchiveTime());
            vo.setCreatedAt(item.getCreatedAt());
            return vo;
        }).toList();
    }

    private Map<Long, User> loadUsers(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, User> map = new LinkedHashMap<>();
        userMapper.selectBatchIds(ids.stream().distinct().toList()).forEach(item -> map.put(item.getId(), item));
        return map;
    }

    private String nextOrderNo() {
        LocalDate today = LocalDate.now();
        long seq = redisUtils.incrementWithTtl(RedisKeyConst.repairOrderSeqKey(today), Duration.ofHours(48));
        return "WX-" + YYYYMMDD.format(today) + "-" + String.format("%04d", seq);
    }

    private void message(Long receiverId, String title, String content, Long orderId) {
        if (receiverId == null) {
            return;
        }
        afterCommitUtils.run(() -> messageService.send(receiverId, MessageType.REPAIR, title, content,
                "REPAIR_ORDER", orderId));
    }

    /** 通知某个角色的所有正常账号（如所有实验室管理员） */
    private void notifyRole(String roleCode, String title, String content, Long orderId) {
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
                messageService.send(id, MessageType.REPAIR, title, content, "REPAIR_ORDER", orderId);
            } catch (Exception e) {
                log.warn("[报修] 通知失败 userId={}, {}", id, e.getMessage());
            }
        }));
    }

    private String statusName(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case RepairOrder.SUBMITTED -> "待复核";
            case RepairOrder.REJECTED -> "已驳回";
            case RepairOrder.ASSIGNED -> "已派单";
            case RepairOrder.REPAIRING -> "维修中";
            case RepairOrder.REPAIRED -> "待验收";
            case RepairOrder.ACCEPTED -> "已验收";
            case RepairOrder.REOPENED -> "已重开";
            default -> status;
        };
    }

    private String priorityName(Integer priority) {
        if (priority == null) {
            return "中";
        }
        return switch (priority) {
            case 1 -> "低";
            case 3 -> "高";
            case 4 -> "紧急";
            default -> "中";
        };
    }
}
