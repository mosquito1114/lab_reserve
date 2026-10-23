-- ============================================================================
-- V6：新增「发送系统通知」权限（迭代 3）
--
-- 迭代 3 落地站内信 + 邮件投递后，需要一个能让管理员主动发通知的入口，
-- 同时它也是验收脚本触发「站内信 + 邮件」全链路的真实业务动作。
-- 该权限只给系统管理员，实验室管理员不参与消息运营。
-- ============================================================================

INSERT IGNORE INTO tb_permission (code, name, module, description) VALUES
('message:send', '发送系统通知', 'message', '管理员向指定用户发送系统通知（站内信 + 邮件）');

INSERT IGNORE INTO tb_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM tb_role r
         JOIN tb_permission p ON p.code = 'message:send'
WHERE r.code = 'SYS_ADMIN';
