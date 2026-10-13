package com.labreserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labreserve.model.entity.Role;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色 Mapper（{@code tb_role}）。
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}
