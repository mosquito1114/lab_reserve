package com.labreserve.service;

import com.labreserve.common.result.PageResult;
import com.labreserve.model.dto.MaintainerQueryDTO;
import com.labreserve.model.dto.UserCreateDTO;
import com.labreserve.model.dto.UserOptionQueryDTO;
import com.labreserve.model.dto.UserPageDTO;
import com.labreserve.model.dto.UserUpdateDTO;
import com.labreserve.model.dto.UserUpdateSelfDTO;
import com.labreserve.model.dto.UserUpdateStatusDTO;
import com.labreserve.model.vo.MaintainerVO;
import com.labreserve.model.vo.UserDetailVO;
import com.labreserve.model.vo.UserOptionVO;
import com.labreserve.model.vo.UserVO;

import java.util.List;

/**
 * 用户管理服务（需求模块 2 的用户部分）。
 *
 * <h3>几条硬约束</h3>
 * <ul>
 *   <li><b>不物理删除</b>：注销只把 {@code status} 置 0，历史日志与业务引用继续有效</li>
 *   <li><b>角色不可改</b>：换角色等于换身份，历史数据引用会错位；确需变更请注销后重建</li>
 *   <li><b>手机号默认脱敏</b>：只有 {@code revealPhone=true} + {@code user:view-phone} 才返回明文，
 *       且必须写一条 {@code QUERY_PHONE} 操作日志；本人查看自己始终是明文</li>
 *   <li><b>唯一性</b>：账号 / 学号 / 教师工号 / 员工工号都唯一，冲突时给友好提示而不是 500</li>
 * </ul>
 */
public interface UserService {

    /** 分页查询用户（多条件 + 三类角色扩展字段） */
    PageResult<UserVO> page(UserPageDTO dto);

    /**
     * 用户详情。
     *
     * @param id          目标用户
     * @param revealPhone 是否请求明文手机号（需要 user:view-phone 权限；本人查看自己不受限）
     */
    UserDetailVO getInfo(Long id, Boolean revealPhone);

    /** 新增用户（仅系统管理员；LAB_ADMIN 无 user:create 权限，会被 403） */
    Long create(UserCreateDTO dto);

    /** 修改用户基本资料与对应扩展表（不允许改角色 / 账号 / 学号工号） */
    void update(UserUpdateDTO dto);

    /** 注销 / 恢复用户（不物理删除；不允许注销自己） */
    void updateStatus(UserUpdateStatusDTO dto);

    /** 修改自己的手机号 / 邮箱 / 邮件通知开关 */
    void updateSelf(UserUpdateSelfDTO dto);

    /** 我的完整档案（含扩展信息，手机号返回明文——本人查看自己） */
    UserDetailVO getSelfProfile();

    /** 维修人员下拉（派单用） */
    List<MaintainerVO> listMaintainer(MaintainerQueryDTO dto);

    /** 用户下拉（按角色过滤，只返回正常状态） */
    List<UserOptionVO> listOption(UserOptionQueryDTO dto);
}
