package com.labreserve.model.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 实验类型占比导出 VO（EasyExcel）。
 */
@Data
@ColumnWidth(18)
public class ExperimentTypeExportVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty("实验类型编码")
    private String code;

    @ExcelProperty("实验类型")
    private String name;

    @ExcelProperty("预约数")
    private Long count;

    @ExcelProperty("占比")
    private String ratio;
}
