package com.labreserve.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labreserve.common.constant.CommonConst;
import com.labreserve.common.enums.OperationModule;
import com.labreserve.common.enums.OperationType;
import com.labreserve.common.exception.BizException;
import com.labreserve.common.result.PageResult;
import com.labreserve.config.AsyncConfig;
import com.labreserve.mapper.OperationLogMapper;
import com.labreserve.mapper.RoleMapper;
import com.labreserve.mapper.UserMapper;
import com.labreserve.model.dto.OperationLogPageDTO;
import com.labreserve.model.entity.OperationLog;
import com.labreserve.model.entity.Role;
import com.labreserve.model.entity.User;
import com.labreserve.model.vo.OperationLogExportVO;
import com.labreserve.model.vo.OperationLogVO;
import com.labreserve.service.OperationLogService;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 操作日志服务实现。
 *
 * <p>写入走独立线程池（{@link AsyncConfig#OPLOG_EXECUTOR}），
 * 且内部吞掉所有异常——日志是"旁路"，绝不能因为它失败而回滚业务事务。
 *
 * <p>查询侧的两点设计：
 * <ul>
 *   <li><b>按操作人姓名筛选</b>：日志表只存 {@code operator_id}，
 *       因此先用姓名/账号在 {@code tb_user} 里捞出 id 集合，再用 {@code IN} 过滤
 *       （比拼 SQL 子查询清晰，也避免手写字符串 SQL）</li>
 *   <li><b>补齐姓名与角色</b>：分页拿到日志后，按 operatorId 批量查用户与角色，
 *       两次额外查询即可，不会产生 N+1</li>
 * </ul>
 */
@Slf4j
@Service
public class OperationLogServiceImpl implements OperationLogService {

    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern(CommonConst.DATETIME_PATTERN);

    @Autowired
    private OperationLogMapper operationLogMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private RoleMapper roleMapper;

    // ------------------------------------------------------------
    // 写入
    // ------------------------------------------------------------

    @Async(AsyncConfig.OPLOG_EXECUTOR)
    @Override
    public void record(OperationLog operationLog) {
        if (operationLog == null) {
            return;
        }
        try {
            operationLogMapper.insert(operationLog);
        } catch (Exception e) {
            log.error("[操作日志] 写入失败，module={}, type={}, content={}",
                    operationLog.getModule(), operationLog.getOperationType(),
                    operationLog.getContent(), e);
        }
    }

    // ------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------

    @Override
    public PageResult<OperationLogVO> page(OperationLogPageDTO dto) {
        Page<OperationLog> page = operationLogMapper.selectPage(dto.toMpPage(), buildWrapper(dto));
        List<OperationLogVO> records = toVOList(page.getRecords());
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public OperationLogVO getInfo(Long id) {
        OperationLog entity = id == null ? null : operationLogMapper.selectById(id);
        BizException.requireFound(entity, "日志不存在");
        return toVOList(List.of(entity)).get(0);
    }

    @Override
    public List<OperationLogExportVO> listForExport(OperationLogPageDTO dto) {
        List<OperationLog> logs = operationLogMapper.selectList(buildWrapper(dto));
        return toVOList(logs).stream().map(OperationLogServiceImpl::toExportVO).toList();
    }

    // ------------------------------------------------------------
    // 私有方法
    // ------------------------------------------------------------

    private LambdaQueryWrapper<OperationLog> buildWrapper(OperationLogPageDTO dto) {
        LambdaQueryWrapper<OperationLog> wrapper = Wrappers.<OperationLog>lambdaQuery()
                .eq(StringUtils.hasText(dto.getOperationType()), OperationLog::getOperationType, dto.getOperationType())
                .eq(StringUtils.hasText(dto.getModule()), OperationLog::getModule, dto.getModule())
                .eq(dto.getOperatorId() != null, OperationLog::getOperatorId, dto.getOperatorId())
                .eq(dto.getResult() != null, OperationLog::getResult, dto.getResult())
                .like(StringUtils.hasText(dto.getIp()), OperationLog::getIp, dto.getIp())
                .ge(dto.getBeginTime() != null, OperationLog::getCreatedAt, dto.getBeginTime())
                .le(dto.getEndTime() != null, OperationLog::getCreatedAt, dto.getEndTime())
                .orderByDesc(OperationLog::getId);

        if (StringUtils.hasText(dto.getOperatorKeyword())) {
            String keyword = dto.getOperatorKeyword().trim();
            List<Long> operatorIds = userMapper.selectList(Wrappers.<User>lambdaQuery()
                            .select(User::getId)
                            .and(condition -> condition
                                    .like(User::getRealName, keyword)
                                    .or()
                                    .like(User::getUsername, keyword)))
                    .stream()
                    .map(User::getId)
                    .toList();
            if (operatorIds.isEmpty()) {
                // 没有任何匹配的操作人 → 直接返回空结果，避免 IN () 这种非法 SQL
                wrapper.eq(OperationLog::getId, -1L);
            } else {
                wrapper.in(OperationLog::getOperatorId, operatorIds);
            }
        }
        return wrapper;
    }

    /** 实体 → VO，批量补齐操作人姓名与角色编码 */
    private List<OperationLogVO> toVOList(List<OperationLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return List.of();
        }

        Set<Long> operatorIds = logs.stream()
                .map(OperationLog::getOperatorId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, User> userMap = operatorIds.isEmpty()
                ? Map.of()
                : userMapper.selectBatchIds(operatorIds).stream()
                        .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a));

        Set<Long> roleIds = userMap.values().stream()
                .map(User::getRoleId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> roleCodeMap = roleIds.isEmpty()
                ? Map.of()
                : roleMapper.selectBatchIds(roleIds).stream()
                        .collect(Collectors.toMap(Role::getId, Role::getCode, (a, b) -> a));

        List<OperationLogVO> result = new ArrayList<>(logs.size());
        for (OperationLog entity : logs) {
            OperationLogVO vo = new OperationLogVO();
            vo.setId(entity.getId());
            vo.setOperatorId(entity.getOperatorId());

            User operator = entity.getOperatorId() == null ? null : userMap.get(entity.getOperatorId());
            if (operator != null) {
                vo.setOperatorName(operator.getRealName());
                vo.setRoleCode(operator.getRoleId() == null ? null : roleCodeMap.get(operator.getRoleId()));
            }

            vo.setModule(entity.getModule());
            vo.setModuleName(moduleName(entity.getModule()));
            vo.setOperationType(entity.getOperationType());
            vo.setOperationTypeName(operationTypeName(entity.getOperationType()));
            vo.setContent(entity.getContent());
            vo.setRequestMethod(entity.getRequestMethod());
            vo.setRequestUrl(entity.getRequestUrl());
            vo.setRequestParam(entity.getRequestParam());
            vo.setIp(entity.getIp());
            vo.setUserAgent(entity.getUserAgent());
            vo.setResult(entity.getResult());
            vo.setErrorMsg(entity.getErrorMsg());
            vo.setCostMs(entity.getCostMs());
            vo.setCreatedAt(entity.getCreatedAt());
            result.add(vo);
        }
        return result;
    }

    private static OperationLogExportVO toExportVO(OperationLogVO vo) {
        OperationLogExportVO export = new OperationLogExportVO();
        export.setId(vo.getId());
        export.setOperatorName(vo.getOperatorName());
        export.setRoleName(vo.getRoleCode());
        export.setModuleName(vo.getModuleName());
        export.setOperationTypeName(vo.getOperationTypeName());
        export.setContent(vo.getContent());
        export.setRequestMethod(vo.getRequestMethod());
        export.setRequestUrl(vo.getRequestUrl());
        export.setIp(vo.getIp());
        export.setResultText(vo.getResult() != null && vo.getResult() == 1 ? "成功" : "失败");
        export.setCostMs(vo.getCostMs());
        export.setErrorMsg(vo.getErrorMsg());
        export.setCreatedAt(vo.getCreatedAt() == null ? null : DATETIME.format(vo.getCreatedAt()));
        return export;
    }

    /** 模块编码 → 中文名 */
    public static String moduleName(String code) {
        if (code == null) {
            return null;
        }
        for (OperationModule module : OperationModule.values()) {
            if (module.getCode().equals(code)) {
                return module.getDescription();
            }
        }
        return code;
    }

    /** 操作类型 → 中文名 */
    public static String operationTypeName(String name) {
        if (name == null) {
            return null;
        }
        for (OperationType type : OperationType.values()) {
            if (type.name().equals(name)) {
                return type.getDescription();
            }
        }
        return name;
    }
}
