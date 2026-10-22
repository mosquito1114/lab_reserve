package com.labreserve.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labreserve.common.result.PageResult;
import com.labreserve.mapper.EquipmentMapper;
import com.labreserve.mapper.LaboratoryMapper;
import com.labreserve.mapper.ResourceUpdateLogMapper;
import com.labreserve.mapper.UserMapper;
import com.labreserve.model.dto.ResourceLogPageDTO;
import com.labreserve.model.entity.Equipment;
import com.labreserve.model.entity.Laboratory;
import com.labreserve.model.entity.ResourceUpdateLog;
import com.labreserve.model.entity.User;
import com.labreserve.model.vo.ResourceLogVO;
import com.labreserve.service.ResourceLogService;
import com.labreserve.util.JsonUtils;
import com.labreserve.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 资源变更留痕实现。
 */
@Slf4j
@Service
public class ResourceLogServiceImpl implements ResourceLogService {

    @Autowired
    private ResourceUpdateLogMapper resourceUpdateLogMapper;

    @Autowired
    private LaboratoryMapper laboratoryMapper;

    @Autowired
    private EquipmentMapper equipmentMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JsonUtils jsonUtils;

    @Autowired
    private SecurityUtils securityUtils;

    @Override
    public void record(Integer resourceType, Long resourceId, Integer operation,
                       Map<String, Object> before, Map<String, Object> after, String updateReason) {
        if (!StringUtils.hasText(updateReason)) {
            // 兜底：正常路径由 DTO 的 @NotBlank 拦住，这里防止内部调用漏填
            throw new com.labreserve.common.exception.BizException(
                    com.labreserve.common.result.ResultCode.BAD_REQUEST, "更新原因不能为空");
        }
        ResourceUpdateLog entity = new ResourceUpdateLog();
        entity.setResourceType(resourceType);
        entity.setResourceId(resourceId);
        entity.setOperation(operation);
        entity.setBeforeJson(before == null ? null : jsonUtils.toJson(before));
        entity.setAfterJson(after == null ? null : jsonUtils.toJson(after));
        entity.setUpdateReason(updateReason.trim());
        entity.setOperatorId(securityUtils.getUserId());
        resourceUpdateLogMapper.insert(entity);
        log.info("[资源留痕] type={}, id={}, op={}, reason={}", resourceType, resourceId, operation, updateReason);
    }

    @Override
    public PageResult<ResourceLogVO> page(ResourceLogPageDTO dto) {
        LambdaQueryWrapper<ResourceUpdateLog> wrapper = Wrappers.<ResourceUpdateLog>lambdaQuery()
                .eq(dto.getResourceType() != null, ResourceUpdateLog::getResourceType, dto.getResourceType())
                .eq(dto.getResourceId() != null, ResourceUpdateLog::getResourceId, dto.getResourceId())
                .eq(dto.getOperation() != null, ResourceUpdateLog::getOperation, dto.getOperation())
                .eq(dto.getOperatorId() != null, ResourceUpdateLog::getOperatorId, dto.getOperatorId())
                .ge(dto.getBeginTime() != null, ResourceUpdateLog::getCreatedAt, dto.getBeginTime())
                .le(dto.getEndTime() != null, ResourceUpdateLog::getCreatedAt, dto.getEndTime())
                .orderByDesc(ResourceUpdateLog::getId);

        Page<ResourceUpdateLog> page = resourceUpdateLogMapper.selectPage(dto.toMpPage(), wrapper);
        List<ResourceUpdateLog> records = page.getRecords();
        Map<Long, User> operators = loadOperators(records.stream()
                .map(ResourceUpdateLog::getOperatorId).filter(Objects::nonNull).distinct().toList());
        Map<Long, Laboratory> labs = loadLabs(records.stream()
                .filter(item -> ResourceLogService.TYPE_LAB == item.getResourceType())
                .map(ResourceUpdateLog::getResourceId).distinct().toList());
        Map<Long, Equipment> equipments = loadEquipments(records.stream()
                .filter(item -> ResourceLogService.TYPE_EQUIPMENT == item.getResourceType())
                .map(ResourceUpdateLog::getResourceId).distinct().toList());

        return PageResult.of(page, item -> {
            ResourceLogVO vo = new ResourceLogVO();
            vo.setId(item.getId());
            vo.setResourceType(item.getResourceType());
            vo.setResourceTypeName(TYPE_LAB == item.getResourceType() ? "实验室" : "设备");
            vo.setResourceId(item.getResourceId());
            if (TYPE_LAB == item.getResourceType()) {
                Laboratory lab = labs.get(item.getResourceId());
                vo.setResourceNo(lab == null ? null : lab.getLabNo());
                vo.setResourceName(lab == null ? null : lab.getName());
            } else {
                Equipment equipment = equipments.get(item.getResourceId());
                vo.setResourceNo(equipment == null ? null : equipment.getResourceNo());
                vo.setResourceName(equipment == null ? null : equipment.getName());
            }
            vo.setOperation(item.getOperation());
            vo.setOperationName(operationName(item.getOperation()));
            vo.setBeforeJson(item.getBeforeJson());
            vo.setAfterJson(item.getAfterJson());
            vo.setUpdateReason(item.getUpdateReason());
            vo.setOperatorId(item.getOperatorId());
            User operator = operators.get(item.getOperatorId());
            vo.setOperatorName(operator == null ? null : operator.getRealName());
            vo.setCreatedAt(item.getCreatedAt());
            return vo;
        });
    }

    private String operationName(Integer operation) {
        if (operation == null) {
            return null;
        }
        return switch (operation) {
            case OP_CREATE -> "新增";
            case OP_UPDATE -> "修改";
            case OP_STATUS -> "状态变更";
            default -> "其他";
        };
    }

    private Map<Long, User> loadOperators(List<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, User> map = new LinkedHashMap<>();
        userMapper.selectBatchIds(ids).forEach(item -> map.put(item.getId(), item));
        return map;
    }

    private Map<Long, Laboratory> loadLabs(List<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Laboratory> map = new LinkedHashMap<>();
        laboratoryMapper.selectBatchIds(ids).forEach(item -> map.put(item.getId(), item));
        return map;
    }

    private Map<Long, Equipment> loadEquipments(List<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Equipment> map = new LinkedHashMap<>();
        equipmentMapper.selectBatchIds(ids).forEach(item -> map.put(item.getId(), item));
        return map;
    }
}
