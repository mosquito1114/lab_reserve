package com.labreserve.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labreserve.common.exception.BizException;
import com.labreserve.mapper.ExperimentTypeMapper;
import com.labreserve.mapper.LaboratoryMapper;
import com.labreserve.mapper.ReservationMapper;
import com.labreserve.model.dto.DictQueryDTO;
import com.labreserve.model.dto.ExperimentTypeSaveDTO;
import com.labreserve.model.entity.ExperimentType;
import com.labreserve.model.entity.Laboratory;
import com.labreserve.model.entity.Reservation;
import com.labreserve.model.vo.ExperimentTypeVO;
import com.labreserve.service.ExperimentTypeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 实验类型字典实现。
 */
@Slf4j
@Service
public class ExperimentTypeServiceImpl implements ExperimentTypeService {

    @Autowired
    private ExperimentTypeMapper experimentTypeMapper;

    @Autowired
    private LaboratoryMapper laboratoryMapper;

    @Autowired
    private ReservationMapper reservationMapper;

    @Override
    public List<ExperimentTypeVO> list(DictQueryDTO dto) {
        List<ExperimentType> list = experimentTypeMapper.selectList(Wrappers.<ExperimentType>lambdaQuery()
                .eq(dto != null && dto.getStatus() != null, ExperimentType::getStatus, dto == null ? null : dto.getStatus())
                .orderByAsc(ExperimentType::getSort)
                .orderByAsc(ExperimentType::getId));
        return list.stream().map(ExperimentTypeServiceImpl::toVO).toList();
    }

    @Override
    public Long create(ExperimentTypeSaveDTO dto) {
        requireUniqueCode(dto.getCode(), null);
        ExperimentType entity = new ExperimentType();
        entity.setCode(dto.getCode().trim().toUpperCase());
        entity.setName(dto.getName().trim());
        entity.setSort(dto.getSort() == null ? 0 : dto.getSort());
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        experimentTypeMapper.insert(entity);
        log.info("[字典] 新增实验类型 id={}, code={}", entity.getId(), entity.getCode());
        return entity.getId();
    }

    @Override
    public void update(ExperimentTypeSaveDTO dto) {
        if (dto.getId() == null) {
            throw BizException.badRequest("实验类型 ID 不能为空");
        }
        ExperimentType exists = experimentTypeMapper.selectById(dto.getId());
        if (exists == null) {
            throw BizException.notFound("实验类型不存在");
        }
        requireUniqueCode(dto.getCode(), dto.getId());

        ExperimentType entity = new ExperimentType();
        entity.setId(dto.getId());
        entity.setCode(dto.getCode().trim().toUpperCase());
        entity.setName(dto.getName().trim());
        entity.setSort(dto.getSort());
        entity.setStatus(dto.getStatus());
        experimentTypeMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        if (id == null) {
            throw BizException.badRequest("实验类型 ID 不能为空");
        }
        if (experimentTypeMapper.selectById(id) == null) {
            throw BizException.notFound("实验类型不存在");
        }
        long labCount = laboratoryMapper.selectCount(Wrappers.<Laboratory>lambdaQuery()
                .eq(Laboratory::getExperimentTypeId, id));
        if (labCount > 0) {
            throw BizException.conflict("该实验类型已被 " + labCount + " 个实验室使用，不能删除，可改为停用");
        }
        long reserveCount = reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getExperimentTypeId, id));
        if (reserveCount > 0) {
            throw BizException.conflict("该实验类型已被 " + reserveCount + " 条预约使用，不能删除，可改为停用");
        }
        experimentTypeMapper.deleteById(id);
        log.info("[字典] 删除实验类型 id={}", id);
    }

    private void requireUniqueCode(String code, Long excludeId) {
        if (!StringUtils.hasText(code)) {
            throw BizException.badRequest("编码不能为空");
        }
        long count = experimentTypeMapper.selectCount(Wrappers.<ExperimentType>lambdaQuery()
                .eq(ExperimentType::getCode, code.trim().toUpperCase())
                .ne(excludeId != null, ExperimentType::getId, excludeId));
        if (count > 0) {
            throw BizException.conflict("实验类型编码已存在：" + code.trim().toUpperCase());
        }
    }

    private static ExperimentTypeVO toVO(ExperimentType entity) {
        ExperimentTypeVO vo = new ExperimentTypeVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setSort(entity.getSort());
        vo.setStatus(entity.getStatus());
        return vo;
    }
}
