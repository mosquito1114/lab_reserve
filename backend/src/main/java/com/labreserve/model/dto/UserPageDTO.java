package com.labreserve.model.dto;

import com.labreserve.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 用户分页查询（{@code POST /api/user/get_page}）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户分页查询入参")
public class UserPageDTO extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "关键字：模糊匹配账号 / 姓名", example = "张")
    private String keyword;

    @Schema(description = "角色编码", example = "STUDENT")
    private String roleCode;

    @Schema(description = "学院（学生看班级、教师看所属学院）", example = "计算机学院")
    private String college;

    @Schema(description = "班级 ID（学生）")
    private Long classId;

    @Schema(description = "学号（精确）")
    private String studentNo;

    @Schema(description = "教师工号（精确）")
    private String teacherNo;

    @Schema(description = "管理类人员工号（精确）")
    private String staffNo;

    @Schema(description = "状态：1 正常 / 0 已注销")
    private Integer status;

    /**
     * 是否返回完整手机号。
     *
     * <p>需要 {@code user:view-phone} 权限（没有权限时忽略该参数），
     * 且会写一条 {@code QUERY_PHONE} 操作日志。
     */
    @Schema(description = "是否返回完整手机号（需 user:view-phone 权限）")
    private Boolean revealPhone = Boolean.FALSE;
}
