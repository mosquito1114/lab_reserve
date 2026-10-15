-- ============================================================================
-- V6 · RAG：R2 ingest 链路落库字段
--
-- 背景：R2 新增 EMBED → UPSERT → FINALIZE 三步。
--       EMBED 调 LLM 拿到向量，UPSERT 推到 Qdrant。两步间如果不持久化向量，
--       进程崩溃会导致整段重跑，浪费 Embedding API 配额。
--       Finalize 写 ready_at 标志文档"已可被 QUERY 检索"。
--
-- 新增列：
--   tb_rag_chunk.vector_blob     LONGBLOB NULL  — EMBED 写入 (raw float[1024] little-endian)
--                                                UPSERT 重跑时不浪费 Embedding 调用
--   tb_rag_document.ready_at     DATETIME       — FINALIZE 写入
--
-- 列语义:
--   ready_at IS NULL            → 文档尚未走完全链 (CREATED/EXTRACTED/.../UPSERTED)
--   ready_at IS NOT NULL        → 文档已 Finalize 进入 READY
--   vector_blob IS NOT NULL     → 该 chunk 已嵌入，Qdrant 待写
--   vector_blob 落库后, UPSERT 重跑 不会重调 LLM (幂等)
--
-- 不动 V2 的 chunks 表 vector_ref 字段 (已就位; UPSERT 时填入 Qdrant Point ID)
-- 也不动 V2/V3/V4/V5 的已有数据
-- ============================================================================

SET NAMES utf8mb4;

ALTER TABLE tb_rag_document
    ADD COLUMN ready_at DATETIME DEFAULT NULL
        COMMENT 'Finalize Step 写入，表示文档正式可被 QUERY 检索的时刻';

ALTER TABLE tb_rag_chunk
    ADD COLUMN vector_blob LONGBLOB DEFAULT NULL
        COMMENT 'EMBED Step 写入 (raw float[vector-size] little-endian)，UPSERT 重跑可省 LLM 调用';
