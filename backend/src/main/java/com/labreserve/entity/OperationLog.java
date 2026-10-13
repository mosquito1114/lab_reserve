package com.labreserve.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志（{@code tb_operation_log}）。
 *
 * <p><b>只增不改不删</b>：本表不提供修改与删除的 Mapper 方法。
 * 由 {@code infra/oplog/OpLogAspect} 异步写入。
 */
@Data
@TableName("tb_operation_log")
public class OperationLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人 ID（登录失败时为空） */
    private Long operatorId;

    /** 操作人姓名快照（防悬空） */
    private String operatorName;

    /** 角色编码快照 */
    private String roleCode;

    /** 模块编码，见 {@code OperationModule#getCode()} */
    private String module;

    /** 操作类型，见 {@code OperationType#name()} */
    private String operationType;

    /** 操作内容描述 */
    private String content;

    private String requestMethod;

    private String requestUrl;

    /** 请求参数（已脱敏） */
    private String requestParam;

    /** 客户端 IP */
    private String ip;

    private String userAgent;

    /** 1 成功 / 0 失败 */
    private Integer result;

    private String errorMsg;

    /** 耗时毫秒 */
    private Integer costMs;

    private LocalDateTime createdAt;
}
