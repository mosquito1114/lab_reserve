-- ============================================================
-- V4：新增「查看系统运行状态」权限（仅系统管理员）
--
-- 背景：首页原来对所有角色都展示「后端 / MySQL / Redis / 运行时长」，
-- 这属于运维信息，只应给系统管理员看。
--
-- 做法：
--   1) 新增权限点 system:monitor:view
--   2) 只授予 SYS_ADMIN
--   3) /api/system/monitor 用 @RequirePerm 保护；公开的 /api/system/health 只返回 status
--      （docker-compose 的健康检查仍然可用）
--
-- 与 V2 的差异：这里不用 INSERT IGNORE，而是 NOT EXISTS 显式判重，
-- 避免「静默吞掉错误」的问题（V3 修的就是 V2 的这个坑）。
--
-- ⚠️ 升级到本版本后请清一次权限缓存，否则角色权限还是旧的：
--     redis-cli DEL lab:reserve:perm:role:1 lab:reserve:perm:role:2 \
--       lab:reserve:perm:role:3 lab:reserve:perm:role:4 lab:reserve:perm:role:5
--    （下次请求会自动从 tb_role_permission 回源）
-- ============================================================

INSERT INTO tb_permission (code, name, module, description)
SELECT 'system:monitor:view', '查看系统运行状态', 'system', '仅系统管理员；含 DB / Redis / 运行时长'
WHERE NOT EXISTS (
    SELECT 1 FROM tb_permission WHERE code = 'system:monitor:view'
);

INSERT INTO tb_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM tb_role r
JOIN tb_permission p ON p.code = 'system:monitor:view'
WHERE r.code = 'SYS_ADMIN'
  AND NOT EXISTS (
      SELECT 1
      FROM tb_role_permission rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );
