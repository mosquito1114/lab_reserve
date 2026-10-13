package com.labreserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labreserve.model.entity.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限点 Mapper（{@code tb_permission}）。
 */
@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {

    /**
     * 查询某个角色拥有的全部权限码。
     *
     * <p>鉴权最高频的一条 SQL，结果由 {@code PermissionService} 缓存到 Redis
     * （{@code lab:reserve:perm:role:{roleId}}），因此这里保持「一次查询拿全」。
     */
    @Select("""
            SELECT p.code
            FROM tb_permission p
            JOIN tb_role_permission rp ON rp.permission_id = p.id
            WHERE rp.role_id = #{roleId}
            ORDER BY p.id
            """)
    List<String> selectCodesByRoleId(@Param("roleId") Long roleId);
}
