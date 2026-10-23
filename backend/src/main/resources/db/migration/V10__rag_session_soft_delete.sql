-- ============================================================================
-- V10 · RAG 会话软删除
--
-- 背景：R5 前端联调发现"删除会话"只是把标题改成 [已删除] xxx，
--       会话仍然出现在列表里、还能点进去、还能重复删除 —— 与用户预期完全不符。
--
-- 按仓库约定（AGENTS.md §3）：业务主体表不物理删除，用 status 表达状态。
-- 所以给 tb_rag_session 补 status 列，删除 = status 置 0，
-- 所有列表/详情查询都过滤 status = 1。
-- 消息（tb_rag_message）保留不删，用于审计与统计。
--
-- 兼容处理：历史上被旧逻辑"改名"过的会话（标题以 [已删除] 开头）一并置为已删除。
-- ============================================================================

SET NAMES utf8mb4;

ALTER TABLE tb_rag_session
    ADD COLUMN status TINYINT NOT NULL DEFAULT 1
        COMMENT '1 正常 / 0 已删除（软删除，消息保留用于审计）',
    ADD INDEX idx_user_status_lastmsg (user_id, status, last_message_at);

-- 历史脏数据：旧逻辑用 "[已删除] " 前缀做假删除，这里统一转成 status=0
UPDATE tb_rag_session
SET status = 0
WHERE title LIKE '[已删除]%';
