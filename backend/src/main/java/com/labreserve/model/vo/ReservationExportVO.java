package com.labreserve.model.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 预约明细导出 VO（EasyExcel）。
 */
@Data
@ColumnWidth(18)
public class ReservationExportVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty("预约号")
    private String reserveNo;

    @ExcelProperty("申请人")
    private String applicantName;

    @ExcelProperty("学院")
    private String college;

    @ExcelProperty("资源类型")
    private String resourceType;

    @ExcelProperty("资源")
    private String resourceName;

    @ExcelProperty("预约日期")
    private String reserveDate;

    @ExcelProperty("时段")
    private String slotName;

    @ExcelProperty("使用人数")
    private Integer usePeople;

    @ExcelProperty("实验类型")
    private String experimentTypeName;

    @ExcelProperty("授课课程")
    private String courseName;

    @ExcelProperty("实验内容")
    @ColumnWidth(40)
    private String purpose;

    @ExcelProperty("状态")
    private String statusName;

    @ExcelProperty("是否排队转正")
    private String fromQueueText;

    @ExcelProperty("提交时间")
    private String createdAt;
}
