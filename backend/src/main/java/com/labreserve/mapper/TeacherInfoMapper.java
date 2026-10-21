package com.labreserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labreserve.model.entity.TeacherInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 教师扩展信息 Mapper（{@code tb_teacher_info}）。
 */
@Mapper
public interface TeacherInfoMapper extends BaseMapper<TeacherInfo> {
}
