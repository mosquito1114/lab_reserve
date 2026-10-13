package com.labreserve;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 实验室预约管理系统启动类。
 *
 * <p>模块划分见 agents/design.md，共 7 个业务模块：
 * 预约管理 / 基础信息管理 / 排队候补 / 设备运维 / 通知 / 统计 / 权限与日志。
 */
// 排除 Spring Security 的默认内存用户：本项目用 JWT + 自定义 PermissionService，
// 不排除的话启动日志里会打印一个「generated security password」的告警，容易误导。
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@MapperScan("com.labreserve.mapper")
@EnableAsync(proxyTargetClass = true)
@EnableScheduling
public class LabReserveApplication {

    public static void main(String[] args) {
        SpringApplication.run(LabReserveApplication.class, args);
    }
}
