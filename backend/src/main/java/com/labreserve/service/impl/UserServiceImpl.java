package com.labreserve.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labreserve.common.constant.CommonConst;
import com.labreserve.common.constant.PermissionConst;
import com.labreserve.common.enums.OperationModule;
import com.labreserve.common.enums.OperationType;
import com.labreserve.common.enums.SensitiveType;
import com.labreserve.common.exception.BizException;
import com.labreserve.common.result.PageResult;
import com.labreserve.mapper.ClassInfoMapper;
import com.labreserve.mapper.RoleMapper;
import com.labreserve.mapper.StaffInfoMapper;
import com.labreserve.mapper.StudentInfoMapper;
import com.labreserve.mapper.TeacherInfoMapper;
import com.labreserve.mapper.UserMapper;
import com.labreserve.model.dto.MaintainerQueryDTO;
import com.labreserve.model.dto.UserCreateDTO;
import com.labreserve.model.dto.UserOptionQueryDTO;
import com.labreserve.model.dto.UserPageDTO;
import com.labreserve.model.dto.UserUpdateDTO;
import com.labreserve.model.dto.UserUpdateSelfDTO;
import com.labreserve.model.dto.UserUpdateStatusDTO;
import com.labreserve.model.entity.ClassInfo;
import com.labreserve.model.entity.OperationLog;
import com.labreserve.model.entity.Role;
import com.labreserve.model.entity.StaffInfo;
import com.labreserve.model.entity.StudentInfo;
import com.labreserve.model.entity.TeacherInfo;
import com.labreserve.model.entity.User;
import com.labreserve.model.vo.MaintainerVO;
import com.labreserve.model.vo.StaffInfoVO;
import com.labreserve.model.vo.StudentInfoVO;
import com.labreserve.model.vo.TeacherInfoVO;
import com.labreserve.model.vo.UserDetailVO;
import com.labreserve.model.vo.UserOptionVO;
import com.labreserve.model.vo.UserVO;
import com.labreserve.security.PermissionService;
import com.labreserve.service.OperationLogService;
import com.labreserve.service.UserService;
import com.labreserve.util.IpUtils;
import com.labreserve.util.SecurityUtils;
import com.labreserve.util.SensitiveUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Set;

/**
 * 用户管理实现。
 *
 * <h3>脱敏与留痕</h3>
 * <pre>
 * 场景                                 手机号返回        写 QUERY_PHONE 日志
 * 本人查看自己                          明文             否
 * 管理员 revealPhone=true 且有权限        明文             是（每次查询一条，记 IP/UA）
 * 其他任何情况（含无权限却传了 revealPhone） 138****1234      否
 * </pre>
 *
 * <h3>为什么角色不可改</h3>
 * 用户的所有历史数据（预约、工单、日志）都是按 {@code user_id} 关联的，
 * 但角色的语义贯穿业务（学生不能约实验室、维修人员只能处理派给自己的工单）。
 * 中途换角色会让「历史数据的角色语境」与「当前角色」不一致，因此只允许注销后重建。
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private StudentInfoMapper studentInfoMapper;

    @Autowired
    private TeacherInfoMapper teacherInfoMapper;

    @Autowired
    private StaffInfoMapper staffInfoMapper;

    @Autowired
    private ClassInfoMapper classInfoMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private SensitiveUtils sensitiveUtils;

    @Autowired
    private SecurityUtils securityUtils;

    @Autowired
    private IpUtils ipUtils;

    // ------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------

    @Override
    public PageResult<UserVO> page(UserPageDTO dto) {
        Page<UserVO> page = new Page<>(dto.safePageNum(), dto.safePageSize());
        IPage<UserVO> result = userMapper.selectUserPage(page, dto);

        // 只有「显式要求 + 真的有权限」才返回明文，否则一律脱敏
        boolean reveal = Boolean.TRUE.equals(dto.getRevealPhone())
                && permissionService.hasPermission(PermissionConst.USER_VIEW_PHONE);
        if (reveal) {
            recordQueryPhone("查看用户列表完整手机号");
        } else {
            result.getRecords().forEach(vo -> vo.setPhone(maskPhone(vo.getPhone())));
        }
        return PageResult.of(result);
    }

    @Override
    public UserDetailVO getInfo(Long id, Boolean revealPhone) {
        User user = requireUser(id);
        Role role = roleMapper.selectById(user.getRoleId());

        Long currentUserId = securityUtils.getUserId();
        boolean self = user.getId().equals(currentUserId);
        boolean reveal = self
                || (Boolean.TRUE.equals(revealPhone)
                    && permissionService.hasPermission(PermissionConst.USER_VIEW_PHONE));
        if (!self && reveal) {
            recordQueryPhone("查看用户完整手机号：userId=" + user.getId());
        }

        UserDetailVO vo = toDetailVO(user, role);
        vo.setPhone(reveal ? user.getPhone() : maskPhone(user.getPhone()));
        return vo;
    }

    @Override
    public UserDetailVO getSelfProfile() {
        Long currentUserId = securityUtils.getUserId();
        User user = requireUser(currentUserId);
        Role role = roleMapper.selectById(user.getRoleId());
        UserDetailVO vo = toDetailVO(user, role);
        vo.setPhone(user.getPhone());
        return vo;
    }

    @Override
    public List<MaintainerVO> listMaintainer(MaintainerQueryDTO dto) {
        List<MaintainerVO> list = userMapper.selectMaintainers(dto.getMaintainerType());
        list.forEach(item -> item.setMaintainerTypeName(maintainerTypeName(item.getMaintainerType())));
        return list;
    }

    @Override
    public List<UserOptionVO> listOption(UserOptionQueryDTO dto) {
        String keyword = StringUtils.hasText(dto.getKeyword()) ? dto.getKeyword().trim() : null;
        return StringUtils.hasText(dto.getRoleCode())
                ? userMapper.selectOptionsByRole(dto.getRoleCode(), keyword)
                : userMapper.selectAllOptions(keyword);
    }

    // ------------------------------------------------------------
    // 新增 / 修改
    // ------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(UserCreateDTO dto) {
        Role role = roleMapper.selectOne(Wrappers.<Role>lambdaQuery().eq(Role::getCode, dto.getRoleCode()));
        BizException.requireFound(role, "角色不存在：" + dto.getRoleCode());

        // 账号唯一
        Long sameUsername = userMapper.selectCount(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, dto.getUsername()));
        if (sameUsername != null && sameUsername > 0) {
            throw BizException.badRequest("账号已存在：" + dto.getUsername());
        }

        User user = new User();
        user.setUsername(dto.getUsername().trim());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRealName(dto.getRealName().trim());
        user.setRoleId(role.getId());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setNotifyEmail(dto.getNotifyEmail() == null ? 0 : dto.getNotifyEmail());
        user.setStatus(1);
        userMapper.insert(user);

        // 按角色写扩展表（角色与扩展块必须匹配，避免出现「学生没有学号」这种脏数据）
        String code = role.getCode();
        if (CommonConst.ROLE_STUDENT.equals(code)) {
            insertStudent(user.getId(), dto);
        } else if (CommonConst.ROLE_TEACHER.equals(code)) {
            insertTeacher(user.getId(), dto);
        } else {
            insertStaff(user.getId(), dto, code);
        }

        log.info("[用户] 新增 userId={}, username={}, role={}", user.getId(), user.getUsername(), code);
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UserUpdateDTO dto) {
        User user = requireUser(dto.getId());

        User update = new User();
        update.setId(user.getId());
        update.setRealName(dto.getRealName());
        update.setPhone(dto.getPhone());
        update.setEmail(dto.getEmail());
        update.setNotifyEmail(dto.getNotifyEmail());
        userMapper.updateById(update);

        String code = roleCodeOf(user.getRoleId());
        if (CommonConst.ROLE_STUDENT.equals(code) && dto.getStudentInfo() != null) {
            StudentInfo info = studentInfoMapper.selectOne(
                    Wrappers.<StudentInfo>lambdaQuery().eq(StudentInfo::getUserId, user.getId()));
            BizException.requireFound(info, "该学生档案不存在");
            requireClass(dto.getStudentInfo().getClassId());
            StudentInfo patch = new StudentInfo();
            patch.setId(info.getId());
            patch.setClassId(dto.getStudentInfo().getClassId());
            studentInfoMapper.updateById(patch);
        } else if (CommonConst.ROLE_TEACHER.equals(code) && dto.getTeacherInfo() != null) {
            TeacherInfo info = teacherInfoMapper.selectOne(
                    Wrappers.<TeacherInfo>lambdaQuery().eq(TeacherInfo::getUserId, user.getId()));
            BizException.requireFound(info, "该教师档案不存在");
            TeacherInfo patch = new TeacherInfo();
            patch.setId(info.getId());
            patch.setCollege(dto.getTeacherInfo().getCollege());
            patch.setTitle(dto.getTeacherInfo().getTitle());
            teacherInfoMapper.updateById(patch);
        } else if (dto.getStaffInfo() != null) {
            StaffInfo info = staffInfoMapper.selectOne(
                    Wrappers.<StaffInfo>lambdaQuery().eq(StaffInfo::getUserId, user.getId()));
            BizException.requireFound(info, "该人员档案不存在");
            StaffInfo patch = new StaffInfo();
            patch.setId(info.getId());
            patch.setDepartment(dto.getStaffInfo().getDepartment());
            patch.setMaintainerType(dto.getStaffInfo().getMaintainerType());
            staffInfoMapper.updateById(patch);
        }

        log.info("[用户] 修改 userId={}", user.getId());
    }

    @Override
    public void updateStatus(UserUpdateStatusDTO dto) {
        User user = requireUser(dto.getId());

        if (user.getId().equals(securityUtils.getUserId())) {
            throw BizException.badRequest("不能注销自己的账号");
        }
        if (user.getStatus() != null && user.getStatus().equals(dto.getStatus())) {
            throw BizException.badRequest(dto.getStatus() == 1 ? "该账号已是正常状态" : "该账号已被注销");
        }

        User update = new User();
        update.setId(user.getId());
        update.setStatus(dto.getStatus());
        userMapper.updateById(update);

        log.info("[用户] {} userId={}, username={}, 原因={}",
                dto.getStatus() == 1 ? "恢复" : "注销", user.getId(), user.getUsername(), dto.getReason());
    }

    @Override
    public void updateSelf(UserUpdateSelfDTO dto) {
        Long currentUserId = securityUtils.getUserId();
        User user = requireUser(currentUserId);

        User update = new User();
        update.setId(user.getId());
        update.setPhone(dto.getPhone());
        update.setEmail(dto.getEmail());
        update.setNotifyEmail(dto.getNotifyEmail());
        userMapper.updateById(update);

        log.info("[用户] 修改本人资料 userId={}", user.getId());
    }

    // ------------------------------------------------------------
    // 私有方法：扩展表写入
    // ------------------------------------------------------------

    private void insertStudent(Long userId, UserCreateDTO dto) {
        if (dto.getStudentInfo() == null) {
            throw BizException.badRequest("学生必须填写学号与班级");
        }
        requireClass(dto.getStudentInfo().getClassId());
        Long same = studentInfoMapper.selectCount(Wrappers.<StudentInfo>lambdaQuery()
                .eq(StudentInfo::getStudentNo, dto.getStudentInfo().getStudentNo()));
        if (same != null && same > 0) {
            throw BizException.badRequest("学号已存在：" + dto.getStudentInfo().getStudentNo());
        }
        StudentInfo info = new StudentInfo();
        info.setUserId(userId);
        info.setStudentNo(dto.getStudentInfo().getStudentNo().trim());
        info.setClassId(dto.getStudentInfo().getClassId());
        studentInfoMapper.insert(info);
    }

    private void insertTeacher(Long userId, UserCreateDTO dto) {
        if (dto.getTeacherInfo() == null) {
            throw BizException.badRequest("教师必须填写工号与所属学院");
        }
        Long same = teacherInfoMapper.selectCount(Wrappers.<TeacherInfo>lambdaQuery()
                .eq(TeacherInfo::getTeacherNo, dto.getTeacherInfo().getTeacherNo()));
        if (same != null && same > 0) {
            throw BizException.badRequest("工号已存在：" + dto.getTeacherInfo().getTeacherNo());
        }
        TeacherInfo info = new TeacherInfo();
        info.setUserId(userId);
        info.setTeacherNo(dto.getTeacherInfo().getTeacherNo().trim());
        info.setCollege(dto.getTeacherInfo().getCollege());
        info.setTitle(dto.getTeacherInfo().getTitle());
        teacherInfoMapper.insert(info);
    }

    private void insertStaff(Long userId, UserCreateDTO dto, String roleCode) {
        if (dto.getStaffInfo() == null) {
            throw BizException.badRequest("管理类人员必须填写工号");
        }
        Long same = staffInfoMapper.selectCount(Wrappers.<StaffInfo>lambdaQuery()
                .eq(StaffInfo::getStaffNo, dto.getStaffInfo().getStaffNo()));
        if (same != null && same > 0) {
            throw BizException.badRequest("工号已存在：" + dto.getStaffInfo().getStaffNo());
        }
        StaffInfo info = new StaffInfo();
        info.setUserId(userId);
        info.setStaffNo(dto.getStaffInfo().getStaffNo().trim());
        info.setDepartment(dto.getStaffInfo().getDepartment());
        // 只有维修人员有「校内/校外」；其他管理类即使是前端传了也不落库
        if (CommonConst.ROLE_MAINTAINER.equals(roleCode)) {
            info.setMaintainerType(dto.getStaffInfo().getMaintainerType() == null
                    ? 1 : dto.getStaffInfo().getMaintainerType());
        }
        staffInfoMapper.insert(info);
    }

    // ------------------------------------------------------------
    // 私有方法：组装与校验
    // ------------------------------------------------------------

    private User requireUser(Long id) {
        User user = id == null ? null : userMapper.selectById(id);
        return BizException.requireFound(user, "用户不存在");
    }

    private ClassInfo requireClass(Long classId) {
        ClassInfo clazz = classId == null ? null : classInfoMapper.selectById(classId);
        return BizException.requireFound(clazz, "班级不存在，请重新选择");
    }

    private String roleCodeOf(Long roleId) {
        Role role = roleId == null ? null : roleMapper.selectById(roleId);
        return role == null ? null : role.getCode();
    }

    private UserDetailVO toDetailVO(User user, Role role) {
        UserDetailVO vo = new UserDetailVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setRoleCode(role == null ? null : role.getCode());
        vo.setRoleName(role == null ? null : role.getName());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setNotifyEmail(user.getNotifyEmail());
        vo.setStatus(user.getStatus());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setCreatedAt(user.getCreatedAt());

        String code = role == null ? null : role.getCode();
        if (CommonConst.ROLE_STUDENT.equals(code)) {
            vo.setStudentInfo(buildStudentInfo(user.getId()));
        } else if (CommonConst.ROLE_TEACHER.equals(code)) {
            vo.setTeacherInfo(buildTeacherInfo(user.getId()));
        } else {
            vo.setStaffInfo(buildStaffInfo(user.getId()));
        }
        return vo;
    }

    private StudentInfoVO buildStudentInfo(Long userId) {
        StudentInfo info = studentInfoMapper.selectOne(
                Wrappers.<StudentInfo>lambdaQuery().eq(StudentInfo::getUserId, userId));
        if (info == null) {
            return null;
        }
        StudentInfoVO vo = new StudentInfoVO();
        vo.setStudentNo(info.getStudentNo());
        vo.setClassId(info.getClassId());
        ClassInfo clazz = info.getClassId() == null ? null : classInfoMapper.selectById(info.getClassId());
        if (clazz != null) {
            vo.setClassName(clazz.getClassName());
            vo.setCollege(clazz.getCollege());
            vo.setMajor(clazz.getMajor());
            vo.setGrade(clazz.getGrade());
        }
        return vo;
    }

    private TeacherInfoVO buildTeacherInfo(Long userId) {
        TeacherInfo info = teacherInfoMapper.selectOne(
                Wrappers.<TeacherInfo>lambdaQuery().eq(TeacherInfo::getUserId, userId));
        if (info == null) {
            return null;
        }
        TeacherInfoVO vo = new TeacherInfoVO();
        vo.setTeacherNo(info.getTeacherNo());
        vo.setCollege(info.getCollege());
        vo.setTitle(info.getTitle());
        return vo;
    }

    private StaffInfoVO buildStaffInfo(Long userId) {
        StaffInfo info = staffInfoMapper.selectOne(
                Wrappers.<StaffInfo>lambdaQuery().eq(StaffInfo::getUserId, userId));
        if (info == null) {
            return null;
        }
        StaffInfoVO vo = new StaffInfoVO();
        vo.setStaffNo(info.getStaffNo());
        vo.setDepartment(info.getDepartment());
        vo.setMaintainerType(info.getMaintainerType());
        vo.setMaintainerTypeName(maintainerTypeName(info.getMaintainerType()));
        return vo;
    }

    private String maskPhone(String phone) {
        return sensitiveUtils.mask(phone, SensitiveType.PHONE);
    }

    private static String maintainerTypeName(Integer type) {
        if (type == null) {
            return null;
        }
        return type == 1 ? "校内" : (type == 2 ? "校外" : "未知");
    }

    /**
     * 写一条 {@code QUERY_PHONE} 操作日志。
     *
     * <p>脱敏例外的强制留痕：谁、什么时候、从哪个 IP 看了完整手机号。
     * 这里直接调用 {@link OperationLogService#record}（异步落库），
     * 因为「只在特定条件下记录」没法用 {@code @OpLog} 注解表达。
     */
    private void recordQueryPhone(String content) {
        try {
            OperationLog entity = new OperationLog();
            entity.setOperatorId(securityUtils.getUserId());
            entity.setModule(OperationModule.USER.getCode());
            entity.setOperationType(OperationType.QUERY_PHONE.name());
            entity.setContent(content);
            entity.setResult(1);

            HttpServletRequest request = currentRequest();
            if (request != null) {
                entity.setRequestMethod(request.getMethod());
                entity.setRequestUrl(request.getRequestURI());
                entity.setIp(ipUtils.getClientIp(request));
                entity.setUserAgent(ipUtils.truncateUserAgent(request.getHeader("User-Agent")));
            }
            operationLogService.record(entity);
        } catch (Exception e) {
            // 审计日志失败不能阻断业务查询
            log.error("[用户] 记录 QUERY_PHONE 日志失败，content={}", content, e);
        }
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servletAttributes
                ? servletRequest(servletAttributes)
                : null;
    }

    private static HttpServletRequest servletRequest(ServletRequestAttributes attributes) {
        return attributes.getRequest();
    }

    /** 供将来扩展：当前用户拥有的权限码（调试用） */
    @SuppressWarnings("unused")
    private Set<String> currentPermissions() {
        return permissionService.listCurrentUserCodes();
    }
}
