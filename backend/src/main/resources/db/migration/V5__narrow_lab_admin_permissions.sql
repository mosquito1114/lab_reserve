-- ============================================================
-- V5：收窄「实验室管理员」的权限到本职（需求变更 2）
--
-- 背景：LAB_ADMIN 只负责「实验室资源维护 + 预约/调整审批 + 排队调度 + 报修验收」，
--       不该碰用户管理、班级维护、操作日志、邮件投递记录。
--
-- 移除的权限点：
--   user:list / user:view / user:view-phone   （用户管理）
--   class:list / class:manage                  （班级维护）
--   system:log:list                            （操作日志）
--   message:delivery-log                       （邮件投递记录，属运维台账）
--
-- 保留：resource:* / dict:* / reservation:list|list-all|view|audit|release /
--       change:list|audit / queue:list-all / repair:create|list|review|assign|accept /
--       message:list / stat:view|export
--
-- 注意：
--   1) 用户 / 班级 / 操作日志仍然由 SYS_ADMIN 负责（班级由系统管理员维护）
--   2) 本迁移只删授权关系，不删权限点本身
--   3) 升级后清一次权限缓存：redis-cli DEL lab:reserve:perm:role:4
--      （LAB_ADMIN 的 role_id 通常是 4；不确定就删全部 lab:reserve:perm:role:*）
-- ============================================================

DELETE rp
FROM tb_role_permission rp
JOIN tb_role r ON r.id = rp.role_id
JOIN tb_permission p ON p.id = rp.permission_id
WHERE r.code = 'LAB_ADMIN'
  AND p.code IN (
      'user:list',
      'user:view',
      'user:view-phone',
      'class:list',
      'class:manage',
      'system:log:list',
      'message:delivery-log'
  );
