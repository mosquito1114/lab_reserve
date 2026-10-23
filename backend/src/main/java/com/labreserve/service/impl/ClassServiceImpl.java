package com.labreserve.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labreserve.common.exception.BizException;
import com.labreserve.common.result.PageResult;
import com.labreserve.mapper.ClassInfoMapper;
import com.labreserve.model.dto.ClassListDTO;
import com.labreserve.model.dto.ClassPageDTO;
import com.labreserve.model.dto.ClassSaveDTO;
import com.labreserve.model.entity.ClassInfo;
import com.labreserve.model.vo.ClassVO;
import com.labreserve.service.ClassService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 班级服务实现。
 */
@Slf4j
@Service
public class ClassServiceImpl implements ClassService {

    @Autowired
    private ClassInfoMapper classInfoMapper;

    @Override
    public List<ClassVO> list(ClassListDTO dto) {
        LambdaQueryWrapper<ClassInfo> wrapper = Wrappers.<ClassInfo>lambdaQuery()
                .eq(ClassInfo::getStatus, 1)
                .eq(StringUtils.hasText(dto.getCollege()), ClassInfo::getCollege, dto.getCollege())
                .eq(dto.getGrade() != null, ClassInfo::getGrade, dto.getGrade())
                .and(StringUtils.hasText(dto.getKeyword()), condition -> condition
                        .like(ClassInfo::getClassName, dto.getKeyword())
                        .or()
                        .like(ClassInfo::getMajor, dto.getKeyword()))
                .orderByAsc(ClassInfo::getGrade)
                .orderByAsc(ClassInfo::getClassName);
        return classInfoMapper.selectList(wrapper).stream().map(ClassServiceImpl::toVO).toList();
    }

    @Override
    public PageResult<ClassVO> page(ClassPageDTO dto) {
        LambdaQueryWrapper<ClassInfo> wrapper = Wrappers.<ClassInfo>lambdaQuery()
                .eq(StringUtils.hasText(dto.getCollege()), ClassInfo::getCollege, dto.getCollege())
                .eq(dto.getGrade() != null, ClassInfo::getGrade, dto.getGrade())
                .eq(dto.getStatus() != null, ClassInfo::getStatus, dto.getStatus())
                .and(StringUtils.hasText(dto.getKeyword()), condition -> condition
                        .like(ClassInfo::getClassName, dto.getKeyword())
                        .or()
                        .like(ClassInfo::getMajor, dto.getKeyword()))
                .orderByAsc(ClassInfo::getGrade)
                .orderByAsc(ClassInfo::getClassName);

        Page<ClassInfo> page = classInfoMapper.selectPage(
                new Page<>(dto.safePageNum(), dto.safePageSize()), wrapper);
        return PageResult.of(page, ClassServiceImpl::toVO);
    }

    @Override
    public Long create(ClassSaveDTO dto) {
        requireUniqueClassName(dto.getClassName(), null);

        ClassInfo entity = new ClassInfo();
        entity.setClassName(dto.getClassName().trim());
        entity.setCollege(dto.getCollege());
        entity.setMajor(dto.getMajor());
        entity.setGrade(dto.getGrade());
        entity.setStudentCount(dto.getStudentCount() == null ? 0 : dto.getStudentCount());
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        classInfoMapper.insert(entity);

        log.info("[班级] 新增 id={}, className={}", entity.getId(), entity.getClassName());
        return entity.getId();
    }

    @Override
    public void update(ClassSaveDTO dto) {
        ClassInfo entity = dto.getId() == null ? null : classInfoMapper.selectById(dto.getId());
        BizException.requireFound(entity, "班级不存在");
        requireUniqueClassName(dto.getClassName(), dto.getId());

        ClassInfo patch = new ClassInfo();
        patch.setId(entity.getId());
        patch.setClassName(dto.getClassName().trim());
        patch.setCollege(dto.getCollege());
        patch.setMajor(dto.getMajor());
        patch.setGrade(dto.getGrade());
        patch.setStudentCount(dto.getStudentCount());
        patch.setStatus(dto.getStatus());
        classInfoMapper.updateById(patch);

        log.info("[班级] 修改 id={}, className={}", entity.getId(), patch.getClassName());
    }

    // ------------------------------------------------------------

    /** 班级名全局唯一；{@code excludeId} 用于修改时排除自身 */
    private void requireUniqueClassName(String className, Long excludeId) {
        Long count = classInfoMapper.selectCount(Wrappers.<ClassInfo>lambdaQuery()
                .eq(ClassInfo::getClassName, className)
                .ne(excludeId != null, ClassInfo::getId, excludeId));
        if (count != null && count > 0) {
            throw BizException.badRequest("班级名称已存在：" + className);
        }
    }

    private static ClassVO toVO(ClassInfo entity) {
        ClassVO vo = new ClassVO();
        vo.setId(entity.getId());
        vo.setClassName(entity.getClassName());
        vo.setCollege(entity.getCollege());
        vo.setMajor(entity.getMajor());
        vo.setGrade(entity.getGrade());
        vo.setStudentCount(entity.getStudentCount());
        vo.setStatus(entity.getStatus());
        return vo;
    }
}
