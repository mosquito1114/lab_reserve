package com.labreserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labreserve.model.entity.RolePermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 角色权限 Mapper（{@code tb_role_permission}）。
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {

    /**
     * 按角色统计权限数，用于角色列表页展示「该角色有多少权限」。
     *
     * @return 每行 {@code {roleId, cnt}}
     */
    @Select("SELECT role_id AS roleId, COUNT(*) AS cnt FROM tb_role_permission GROUP BY role_id")
    List<Map<String, Object>> countGroupByRole();
}
