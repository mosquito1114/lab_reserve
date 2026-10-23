package com.labreserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labreserve.model.entity.StudentInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学生扩展信息 Mapper（{@code tb_student_info}）。
 */
@Mapper
public interface StudentInfoMapper extends BaseMapper<StudentInfo> {
}
