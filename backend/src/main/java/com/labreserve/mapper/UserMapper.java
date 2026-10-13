package com.labreserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labreserve.model.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 用户 Mapper（{@code tb_user}）。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 按角色统计用户数，用于角色列表页展示「该角色有多少人」。
     *
     * @return 每行 {@code {roleId, cnt}}
     */
    @Select("SELECT role_id AS roleId, COUNT(*) AS cnt FROM tb_user GROUP BY role_id")
    List<Map<String, Object>> countGroupByRole();
}
