-- ============================================================================
-- V5 · 修补：tb_rag_document 加 status 列
--
-- 背景：R0 的 RagDocumentMapper.setBusinessStatus(id, status) 已经引用了
--       tb_rag_document.status 列，但 V2 建表时漏建。
--       跳过了 V4 主要因为 V4 主题是"中间态落库列"，再加 status 会让一个
--       迁移文件承担两个语义不连贯的变更，不利 review。
--
-- 列语义（1 启用 / 0 停用），与 pipeline_state 解耦：
--   - 文档还没走完摄取链路 → status 默认 0（停用）
--   - admin enable 后 → status=1（参与 QUERY 链路检索）
--   - 文档被人工停用 → status=0（不检索，但 chunks 不删）
--
-- 老数据兼容：现有行 status=NULL，被 Java 端按 0 处理。
-- ============================================================================

SET NAMES utf8mb4;

ALTER TABLE tb_rag_document
    ADD COLUMN status TINYINT NOT NULL DEFAULT 0
        COMMENT '业务启停 1启用 / 0停用；与 pipeline_state 解耦',
    ADD INDEX idx_status (status);
