package com.labreserve.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户下拉查询（{@code POST /api/user/list_option}）。
 */
@Data
@Schema(description = "用户下拉查询入参")
public class UserOptionQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "角色编码，缺省返回全部角色（仅正常状态用户）", example = "LAB_ADMIN")
    private String roleCode;

    @Schema(description = "姓名 / 账号模糊匹配")
    private String keyword;
}
