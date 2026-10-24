package com.labreserve.model.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 资源预约占比导出 VO（EasyExcel）。
 */
@Data
@ColumnWidth(18)
public class ResourceRatioExportVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty("资源编号")
    private String resourceNo;

    @ExcelProperty("资源名称")
    private String resourceName;

    @ExcelProperty("预约数")
    private Long count;

    @ExcelProperty("占比")
    private String ratio;
}
