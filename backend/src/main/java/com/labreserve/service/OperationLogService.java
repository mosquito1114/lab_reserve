package com.labreserve.service;

import com.labreserve.common.result.PageResult;
import com.labreserve.model.dto.OperationLogPageDTO;
import com.labreserve.model.entity.OperationLog;
import com.labreserve.model.vo.OperationLogExportVO;
import com.labreserve.model.vo.OperationLogVO;

import java.util.List;

/**
 * 操作日志服务。
 *
 * <p>审计要求：<b>只增不改不删</b>，因此这里只有「写入」+「查询/导出」，
 * 没有 update / delete 方法（需求 Q28）。
 */
public interface OperationLogService {

    /**
     * 异步落库一条操作日志。
     *
     * <p>调用方（{@code OpLogAspect}）不关心结果——日志写失败绝不能影响业务。
     *
     * @param operationLog 已组装好的日志对象
     */
    void record(OperationLog operationLog);

    /**
     * 多条件分页查询。
     *
     * <p>操作人姓名与角色不在日志表里（反冗余原则），查询时 JOIN {@code tb_user} / {@code tb_role} 补齐。
     */
    PageResult<OperationLogVO> page(OperationLogPageDTO dto);

    /** 日志详情 */
    OperationLogVO getInfo(Long id);

    /**
     * 按同样的筛选条件取全部记录（不分页），供导出使用。
     *
     * <p>导出量按课设数据规模可控；若将来日志量很大，应改为流式分页写入 Excel。
     */
    List<OperationLogExportVO> listForExport(OperationLogPageDTO dto);
}
