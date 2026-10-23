package com.labreserve.config;

import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 演示种子数据加载器。
 *
 * <p>需求：Spring Boot 启动时需要检查表是否建好，没有就自动建表并灌入演示数据。
 * 建表交给 Flyway，本类只负责灌数据。
 *
 * <h3>执行时机</h3>
 * <ul>
 *   <li>Flyway 建完表之后（{@code ApplicationRunner} 在容器刷新完成后执行）</li>
 *   <li>只在 {@code tb_role} 为空时执行，因此重复启动不会重复灌入</li>
 * </ul>
 *
 * <h3>数据来源</h3>
 * {@code resources/data/seed_data.sql} —— 角色、权限矩阵、字典、12 间实验室、
 * 84 台设备、360 条历史预约、120 条消息、排队与工单样例。
 *
 * <h3>关闭方式</h3>
 * {@code lab-reserve.seed.enabled=false}（或环境变量 {@code LAB_RESERVE_SEED_ENABLED=false}）。
 * 生产环境务必关闭。
 */
@Slf4j
@Component
public class DataSeeder implements ApplicationRunner {

    private static final String SEED_SCRIPT = "data/seed_data.sql";

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${lab-reserve.seed.enabled:true}")
    private boolean enabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("[种子数据] 已通过配置禁用，跳过");
            return;
        }
        if (!isDatabaseEmpty()) {
            log.info("[种子数据] 检测到已有数据，跳过灌入");
            return;
        }

        long start = System.currentTimeMillis();
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.setSqlScriptEncoding("UTF-8");
            populator.setSeparator(";");
            populator.setContinueOnError(false);
            populator.setIgnoreFailedDrops(true);
            populator.addScript(new ClassPathResource(SEED_SCRIPT));
            DatabasePopulatorUtils.execute(populator, dataSource);
            log.info("[种子数据] 灌入完成，耗时 {} ms", System.currentTimeMillis() - start);
            logSummary();
        } catch (Exception e) {
            // 种子数据失败说明环境有问题，直接让应用启动失败，避免后面出现"页面全空"的迷惑现象
            log.error("[种子数据] 灌入失败，脚本={}", SEED_SCRIPT, e);
            throw new IllegalStateException("演示种子数据灌入失败，请检查 data/seed_data.sql 与数据库版本", e);
        }
    }

    // ------------------------------------------------------------

    /** 用第一个被插入的表作为"是否已初始化"的标记表 */
    private boolean isDatabaseEmpty() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_role", Integer.class);
        return count == null || count == 0;
    }

    private void logSummary() {
        try {
            log.info("[种子数据] 角色 {} 个 / 权限 {} 个 / 用户 {} 个 / 实验室 {} 间 / 设备 {} 台 / 预约 {} 条 / 消息 {} 条",
                    count("tb_role"), count("tb_permission"), count("tb_user"),
                    count("tb_laboratory"), count("tb_equipment"),
                    count("tb_reservation"), count("tb_message"));
            log.info("[种子数据] 演示账号：全部账号密码均为 123456（含 admin）");
        } catch (Exception e) {
            log.warn("[种子数据] 统计行数失败", e);
        }
    }

    private int count(String table) {
        Integer value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return value == null ? 0 : value;
    }
}
