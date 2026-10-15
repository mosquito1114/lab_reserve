package com.labreserve.model.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 操作日志导出模型（EasyExcel）。
 *
 * <p>字段顺序即 Excel 列顺序；列表页与导出用的是同一套筛选条件，
 * 差别只是导出不分页（但仍受 {@code system:log:export} 权限约束）。
 */
@Data
public class OperationLogExportVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty("日志ID")
    @ColumnWidth(10)
    private Long id;

    @ExcelProperty("操作人")
    @ColumnWidth(14)
    private String operatorName;

    @ExcelProperty("角色编码")
    @ColumnWidth(16)
    private String roleName;

    @ExcelProperty("模块")
    @ColumnWidth(14)
    private String moduleName;

    @ExcelProperty("操作类型")
    @ColumnWidth(12)
    private String operationTypeName;

    @ExcelProperty("操作内容")
    @ColumnWidth(38)
    private String content;

    @ExcelProperty("请求方法")
    @ColumnWidth(10)
    private String requestMethod;

    @ExcelProperty("请求地址")
    @ColumnWidth(30)
    private String requestUrl;

    @ExcelProperty("客户端IP")
    @ColumnWidth(16)
    private String ip;

    @ExcelProperty("结果")
    @ColumnWidth(8)
    private String resultText;

    @ExcelProperty("耗时(ms)")
    @ColumnWidth(10)
    private Integer costMs;

    @ExcelProperty("失败原因")
    @ColumnWidth(30)
    private String errorMsg;

    @ExcelProperty("操作时间")
    @ColumnWidth(20)
    private String createdAt;
}
