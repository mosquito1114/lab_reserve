-- ============================================================================
-- V2 · RAG（智能问答）模块初始化
--
-- 涵盖：
--   1) 6 张新表（文档 / 步骤日志 / 切片 / 会话 / 消息 / 使用统计）
--   2) 6 个新权限码 + 角色分配
--   3) 切片策略字段就位（RECURSIVE / QA / FIXED_OVERLAP）
--
-- 风格：
--   - INSERT 用 ON DUPLICATE KEY UPDATE 让重跑幂等（开发期反复跑也不会报错）
--   - 字段命名一律 tb_rag_* 与约定一致（不要多余的物理外键）
-- ============================================================================

SET NAMES utf8mb4;

-- ============================================================================
-- 1. 文档主表（链路 A 状态机）
-- ============================================================================
CREATE TABLE IF NOT EXISTS tb_rag_document (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    title                VARCHAR(255) NOT NULL,
    category             VARCHAR(32)  NOT NULL                          COMMENT 'REGULATION / MANUAL / FAQ / SOP',
    source_type          VARCHAR(16)  NOT NULL                          COMMENT 'PDF / DOCX / MD / TXT / PPT / XLSX',
    file_url             VARCHAR(500)                                  COMMENT 'OSS URL',
    char_count           INT          NOT NULL DEFAULT 0,
    chunk_count          INT          NOT NULL DEFAULT 0,

    -- 生命周期状态机
    pipeline_state       VARCHAR(32)  NOT NULL DEFAULT 'CREATED'        COMMENT 'CREATED/EXTRACTING/EXTRACTED/SANITIZING/SANITIZED/CHUNKING/CHUNKED/EMBEDDING/EMBEDDED/UPSERTING/UPSERTED/READY/DISABLED/FAILED',
    ingest_version       INT          NOT NULL DEFAULT 1,
    content_sha256       CHAR(64)     NOT NULL,
    last_error_step      VARCHAR(32),
    last_error_msg       VARCHAR(2000),
    last_error_at        DATETIME,

    -- 【切片策略可插拔】R1 起生效
    chunking_strategy    VARCHAR(32)  DEFAULT NULL                       COMMENT 'RECURSIVE/QA/FIXED_OVERLAP; NULL=走全局默认',
    chunk_max_chars      INT          DEFAULT NULL                       COMMENT '策略切长上限; NULL=走全局默认',
    chunk_overlap        INT          DEFAULT NULL                       COMMENT '窗口重叠; NULL=走全局默认',

    uploaded_by          BIGINT       NOT NULL,
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_state       (pipeline_state),
    INDEX idx_sha         (content_sha256),
    INDEX idx_uploader    (uploaded_by),
    INDEX idx_chunking    (chunking_strategy)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- 2. 步骤日志（每跑一步落一行；完整审计，可还原 DAG）
-- ============================================================================
CREATE TABLE IF NOT EXISTS tb_rag_pipeline_step (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    document_id          BIGINT       NOT NULL,
    ingest_version       INT          NOT NULL,
    step_code            VARCHAR(32)  NOT NULL                          COMMENT 'EXTRACT/SANITIZE/CHUNK/EMBED/UPSERT/FINALIZE',
    status               TINYINT      NOT NULL                          COMMENT '1 RUNNING / 2 SUCCESS / 3 FAILED / 4 SKIPPED',
    input_sha256         CHAR(64),
    output_sha256        CHAR(64),
    output_meta          JSON                                           COMMENT '输出统计 JSON: {strategy, maxChars, overlap, chunkCount, tokens, points}',
    trace_id             VARCHAR(64),
    cost_ms              INT,
    error_msg            VARCHAR(2000),
    started_at           DATETIME,
    finished_at          DATETIME,
    parent_step_id       BIGINT,
    PRIMARY KEY (id),
    INDEX idx_doc_ver_step (document_id, ingest_version, step_code),
    INDEX idx_status       (status),
    INDEX idx_trace        (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- 3. 切片明细（用于引用追溯 / 人工排查）
-- ============================================================================
CREATE TABLE IF NOT EXISTS tb_rag_chunk (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    document_id          BIGINT       NOT NULL,
    ingest_version       INT          NOT NULL,
    chunk_index          INT          NOT NULL,
    content              TEXT         NOT NULL,
    char_start           INT          NOT NULL,
    char_end             INT          NOT NULL,
    strategy_code        VARCHAR(32)                                   COMMENT 'RECURSIVE/QA/FIXED_OVERLAP',
    vector_ref           VARCHAR(128)                                  COMMENT 'Qdrant Point ID: {docId}_{ver}_{idx}',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_ver_idx (document_id, ingest_version, chunk_index),
    INDEX idx_strategy (strategy_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- 4. 会话
-- ============================================================================
CREATE TABLE IF NOT EXISTS tb_rag_session (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    user_id              BIGINT       NOT NULL,
    title                VARCHAR(128) NOT NULL DEFAULT '新会话',
    message_count        INT          NOT NULL DEFAULT 0,
    summary              MEDIUMTEXT                                      COMMENT '历史摘要(SYSTEM 角色压缩结果)',
    summary_upto         BIGINT,
    last_token_window    INT          NOT NULL DEFAULT 0                  COMMENT '最近 N 轮累计 token, 超阈值触发压缩',
    last_message_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_user_lastmsg (user_id, last_message_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- 5. 消息
-- ============================================================================
CREATE TABLE IF NOT EXISTS tb_rag_message (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    session_id           BIGINT       NOT NULL,
    role                 VARCHAR(16)  NOT NULL                            COMMENT 'USER / ASSISTANT / TOOL / SYSTEM',
    content              MEDIUMTEXT   NOT NULL,
    rewritten            TEXT                                              COMMENT '问题重写结果(USER 角色的补充字段)',
    intent               JSON                                              COMMENT '意图识别结果(USER 角色的补充字段)',
    citations            JSON                                              COMMENT '[{source, docId, chunkId, toolName, snippet, score}]',
    tool_calls           JSON                                              COMMENT 'Tool 调用记录(ASSISTANT 角色)',
    input_tokens         INT,
    output_tokens        INT,
    cost_ms              INT,
    feedback             TINYINT                                           COMMENT '1 有用 / 0 没用(异步反馈)',
    trace_id             VARCHAR(64),
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_session_created (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- 6. 使用统计(运营报表 + 成本核算)
-- ============================================================================
CREATE TABLE IF NOT EXISTS tb_rag_usage_stats (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    user_id              BIGINT       NOT NULL,
    role_code            VARCHAR(32)  NOT NULL,
    intent               VARCHAR(32)  NOT NULL,
    used_tokens          INT          NOT NULL,
    doc_hits             INT          NOT NULL,
    tool_calls           INT          NOT NULL,
    cost_ms              INT          NOT NULL,
    degraded             TINYINT      NOT NULL DEFAULT 0                  COMMENT '是否走了降级路径',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_day   (created_at),
    INDEX idx_user  (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- 7. 权限码 + 角色分配
--    INSERT IGNORE 让重跑幂等
-- ============================================================================

-- 7.1 添加 6 个新权限码到 tb_permission
INSERT IGNORE INTO tb_permission (code, name, module, description) VALUES
('rag:chat:query',  '发起 RAG 提问',          'rag',  'POST /api/rag/chat/send'),
('rag:chat:history','查看会话与消息历史',     'rag',  '查自己 session/message'),
('rag:doc:upload',  '上传知识库文档',         'rag',  'POST /api/rag/admin/documents/upload'),
('rag:doc:list',    '文档列表与元数据',       'rag',  'GET .../documents 与 .../chunking-strategies'),
('rag:doc:reindex', '重建摄取/改切片策略',    'rag',  'POST .../reingest, PATCH .../chunking, POST .../steps/*/run'),
('rag:doc:delete',  '停用/启用文档',          'rag',  'POST .../disable, POST .../enable');

-- 7.2 学生 / 教师：rag:chat:query + rag:chat:history
INSERT IGNORE INTO tb_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM tb_role r JOIN tb_permission p ON p.code IN (
    'rag:chat:query', 'rag:chat:history'
)
WHERE r.code IN ('STUDENT', 'TEACHER', 'MAINTAINER');

-- 7.3 实验室管理员 / 系统管理员：以上 6 个全开
INSERT IGNORE INTO tb_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM tb_role r JOIN tb_permission p ON p.code IN (
    'rag:chat:query', 'rag:chat:history',
    'rag:doc:upload', 'rag:doc:list', 'rag:doc:reindex', 'rag:doc:delete'
)
WHERE r.code IN ('LAB_ADMIN', 'SYS_ADMIN');
