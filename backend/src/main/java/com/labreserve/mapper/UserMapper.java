package com.labreserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.labreserve.model.dto.UserPageDTO;
import com.labreserve.model.entity.User;
import com.labreserve.model.vo.MaintainerVO;
import com.labreserve.model.vo.UserOptionVO;
import com.labreserve.model.vo.UserVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 用户 Mapper（{@code tb_user}）。
 *
 * <p>用户列表要展示学生/教师/员工三类扩展字段，单表查不出来，
 * 因此这里用一条带 LEFT JOIN 的 SQL 直接投影成 {@link UserVO}
 * （反冗余原则：字段都在查询时 JOIN 得到，不落库）。
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

    /**
     * 用户分页查询（多条件 + 三类角色扩展字段）。
     *
     * <p>支持按关键字（账号/姓名）、角色、学院（学生看班级、教师看 teacher_info）、
     * 班级、学号/工号、状态筛选。
     */
    @Select("""
            <script>
            SELECT u.id, u.username, u.real_name, u.phone, u.email, u.notify_email,
                   u.status, u.last_login_at, u.created_at,
                   r.code AS role_code, r.name AS role_name,
                   si.student_no, si.class_id, c.class_name, c.grade,
                   ti.teacher_no,
                   st.staff_no, st.department, st.maintainer_type,
                   COALESCE(c.college, ti.college) AS college
            FROM tb_user u
            LEFT JOIN tb_role r          ON r.id = u.role_id
            LEFT JOIN tb_student_info si ON si.user_id = u.id
            LEFT JOIN tb_class c         ON c.id = si.class_id
            LEFT JOIN tb_teacher_info ti ON ti.user_id = u.id
            LEFT JOIN tb_staff_info st   ON st.user_id = u.id
            WHERE 1 = 1
            <if test="q.keyword != null and q.keyword != ''">
                AND (u.username LIKE CONCAT('%', #{q.keyword}, '%')
                     OR u.real_name LIKE CONCAT('%', #{q.keyword}, '%'))
            </if>
            <if test="q.roleCode != null and q.roleCode != ''">
                AND r.code = #{q.roleCode}
            </if>
            <if test="q.college != null and q.college != ''">
                AND (c.college = #{q.college} OR ti.college = #{q.college})
            </if>
            <if test="q.classId != null">
                AND si.class_id = #{q.classId}
            </if>
            <if test="q.studentNo != null and q.studentNo != ''">
                AND si.student_no = #{q.studentNo}
            </if>
            <if test="q.teacherNo != null and q.teacherNo != ''">
                AND ti.teacher_no = #{q.teacherNo}
            </if>
            <if test="q.staffNo != null and q.staffNo != ''">
                AND st.staff_no = #{q.staffNo}
            </if>
            <if test="q.status != null">
                AND u.status = #{q.status}
            </if>
            ORDER BY u.id DESC
            </script>
            """)
    IPage<UserVO> selectUserPage(IPage<UserVO> page, @Param("q") UserPageDTO query);

    /**
     * 查询某角色下状态正常的用户（下拉 / 派单用）。
     */
    @Select("""
            SELECT u.id, u.real_name, u.username
            FROM tb_user u
            JOIN tb_role r ON r.id = u.role_id
            WHERE u.status = 1 AND r.code = #{roleCode}
              AND (#{keyword} IS NULL OR u.real_name LIKE CONCAT('%', #{keyword}, '%')
                   OR u.username LIKE CONCAT('%', #{keyword}, '%'))
            ORDER BY u.id
            """)
    List<UserOptionVO> selectOptionsByRole(@Param("roleCode") String roleCode,
                                                                   @Param("keyword") String keyword);

    /**
     * 全角色用户下拉（不按角色过滤时使用）。
     */
    @Select("""
            SELECT u.id, u.real_name, u.username
            FROM tb_user u
            WHERE u.status = 1
              AND (#{keyword} IS NULL OR u.real_name LIKE CONCAT('%', #{keyword}, '%')
                   OR u.username LIKE CONCAT('%', #{keyword}, '%'))
            ORDER BY u.id
            """)
    List<UserOptionVO> selectAllOptions(@Param("keyword") String keyword);

    /**
     * 维修人员下拉（带 staff_info 的工号与校内/校外标签）。
     */
    @Select("""
            SELECT u.id, u.real_name, st.staff_no, st.maintainer_type
            FROM tb_user u
            JOIN tb_role r       ON r.id = u.role_id
            LEFT JOIN tb_staff_info st ON st.user_id = u.id
            WHERE u.status = 1 AND r.code = 'MAINTAINER'
              AND (#{maintainerType} IS NULL OR st.maintainer_type = #{maintainerType})
            ORDER BY u.id
            """)
    List<MaintainerVO> selectMaintainers(@Param("maintainerType") Integer maintainerType);
}
