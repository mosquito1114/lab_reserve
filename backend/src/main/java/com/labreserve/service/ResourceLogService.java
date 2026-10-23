package com.labreserve.service;

import com.labreserve.common.result.PageResult;
import com.labreserve.model.dto.ResourceLogPageDTO;
import com.labreserve.model.vo.ResourceLogVO;

import java.util.Map;

/**
 * 资源变更留痕服务。
 *
 * <p>实验室 / 设备的每次写操作都必须调用 {@link #record}：
 * 强制记录「更新原因」+ before/after 快照，落 {@code tb_resource_update_log}。
 */
public interface ResourceLogService {

    int TYPE_LAB = 1;
    int TYPE_EQUIPMENT = 2;

    int OP_CREATE = 1;
    int OP_UPDATE = 2;
    int OP_STATUS = 3;

    /**
     * 记录一次资源变更。
     *
     * @param resourceType 1 实验室 / 2 设备
     * @param resourceId   资源 ID
     * @param operation    1 新增 / 2 修改 / 3 状态变更
     * @param before       变更前快照（新增时为 null）
     * @param after        变更后快照
     * @param updateReason 更新原因（必填）
     */
    void record(Integer resourceType, Long resourceId, Integer operation,
                Map<String, Object> before, Map<String, Object> after, String updateReason);

    /** 分页查询 */
    PageResult<ResourceLogVO> page(ResourceLogPageDTO dto);
}
