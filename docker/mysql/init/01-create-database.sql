-- ============================================================
-- lab_reserve 建库脚本
--
-- 执行时机：MySQL 容器「首次初始化」时由官方 entrypoint 自动执行
--           （即 docker-entrypoint-initdb.d 目录），先于应用启动的 Flyway。
-- 注意：数据卷已存在时不会重复执行。要重来请 `docker compose down -v`。
--
-- 应用账号由官方镜像根据环境变量创建：
--   MYSQL_DATABASE / MYSQL_USER / MYSQL_PASSWORD
-- 本脚本只负责把库的字符集与排序规则显式固定下来（不依赖服务器默认值）。
-- ============================================================

CREATE DATABASE IF NOT EXISTS `lab_reserve`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

-- 表结构由后端启动时的 Flyway 负责（backend/src/main/resources/db/migration/V1__init_schema.sql）
-- 演示数据由后端 DataSeeder 负责（backend/src/main/resources/data/seed_data.sql）
