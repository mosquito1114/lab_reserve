package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户下拉项（{@code POST /api/user/list_option}）。
 *
 * <p>只给「选人」用的最小字段集，不返回手机号 / 邮箱等敏感信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户下拉项")
public class UserOptionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String realName;

    private String username;
}
