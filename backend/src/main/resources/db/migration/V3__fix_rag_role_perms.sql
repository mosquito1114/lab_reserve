-- ============================================================================
-- V3 · 修补:RAG 角色权限分配
--
-- 背景：V2 SQL 跑过 Flyway 后 success=1 但 INSERT IGNORE 这两段没真插入数据
-- （MySQL 8 + Flyway 事务边界 + multi-row INSERT...SELECT 某种路径下静默 NOOP；
--  人工直接执行同样的 SQL 是 OK 的，因此判定为 Flyway 跑迁移时的小概率吞掉）。
--
-- 本迁移：
--   - 用最朴素的 INSERT IGNORE 再补一次（已经分配的角色不会重复）
--   - 凡 RAG 第一次跑的部署会先把数据写对
--   - 已经走过 V2 但 RAG 分配缺失的部署，补上数据
-- ============================================================================

INSERT IGNORE INTO tb_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM tb_role r
JOIN tb_permission p ON p.code IN ('rag:chat:query', 'rag:chat:history')
WHERE r.code IN ('STUDENT', 'TEACHER', 'MAINTAINER');

INSERT IGNORE INTO tb_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM tb_role r
JOIN tb_permission p ON p.code IN (
    'rag:chat:query', 'rag:chat:history',
    'rag:doc:upload', 'rag:doc:list', 'rag:doc:reindex', 'rag:doc:delete')
WHERE r.code IN ('LAB_ADMIN', 'SYS_ADMIN');
