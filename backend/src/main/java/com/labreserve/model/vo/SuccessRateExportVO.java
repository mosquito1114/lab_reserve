package com.labreserve.model.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 预约成功率导出 VO（EasyExcel）。
 */
@Data
@ColumnWidth(18)
public class SuccessRateExportVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty("指标")
    private String metric;

    @ExcelProperty("数值")
    private String value;
}
