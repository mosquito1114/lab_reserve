-- ============================================================================
-- V4 · RAG：摄取链路中间态落库列
--
-- 背景：v2 设计图 §3.4 抽象基类里 Step 与 Step 之间需要传递整段文本，
--       EXTRACT 解析后 → SANITIZE 清洗；SANITIZE 清洗后 → CHUNK 切片。
--       选项：
--         (a) Redis 存中间态：快但 ./dev.sh down 后丢失，开发体验差
--         (b) MySQL MEDIUMTEXT 列 UPDATE：稳定，UPDATE 成本可接受（R1 自测文件 ≤50MB）
--         (c) 引入新表 rag_step_output：多表事务 + JOIN 多一次查询
--       选 (b)：单表 + 简单；R5 性能调优时可以无痛迁到 (a)/(c)。
--
-- 风格：
--   - 不动 V1/V2/V3；新列全部 NULL-able，老数据不受影响
--   - 字段命名与设计图一致（raw_text / clean_text）
-- ============================================================================

SET NAMES utf8mb4;

ALTER TABLE tb_rag_document
    ADD COLUMN raw_text   MEDIUMTEXT  DEFAULT NULL
        COMMENT 'EXTRACT Step 解析后的原文（Tika 输出，由 EXTRACT 落库）',
    ADD COLUMN clean_text MEDIUMTEXT  DEFAULT NULL
        COMMENT 'SANITIZE Step 清洗后的正文（身份证/手机/邮箱已替换为占位符，由 SANITIZE 落库）';
