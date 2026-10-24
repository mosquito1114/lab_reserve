package com.labreserve.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 我的实验课表条目（学生端，按班级反查）。
 */
@Data
@Schema(description = "我的实验课表条目")
public class TimetableVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long reservationId;

    private String reserveNo;

    private Long labId;

    private String labNo;

    private String labName;

    private String labLocation;

    private LocalDate reserveDate;

    private String slotName;

    private LocalTime startTime;

    private LocalTime endTime;

    private String courseName;

    @Schema(description = "任课教师")
    private String teacherName;

    private String status;
}
