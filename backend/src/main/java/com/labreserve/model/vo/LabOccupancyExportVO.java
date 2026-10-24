package com.labreserve.model.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 实验室占用率导出 VO（EasyExcel）。
 */
@Data
@ColumnWidth(18)
public class LabOccupancyExportVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty("实验室编号")
    private String labNo;

    @ExcelProperty("实验室名称")
    private String labName;

    @ExcelProperty("开放时段数")
    private Long openSlots;

    @ExcelProperty("已占用时段数")
    private Long usedSlots;

    @ExcelProperty("占用率")
    private String occupancyRate;
}
