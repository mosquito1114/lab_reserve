-- ============================================================================
-- lab_reserve 演示种子数据
--
-- 执行者：DataSeeder（应用启动时判断表为空才执行，见 infra/seed/DataSeeder.java）
-- 用途：让系统一启动就有可演示的角色、字典、资源与统计数据
--
-- ⚠️ 账号密码说明
--   演示账号的密码是 BCrypt 哈希，明文如下（仅用于课设演示）：
--     admin                    → admin123
--     其余全部演示账号           → 123456
--   生产环境必须删除本文件或修改全部密码。
--
-- ⚠️ 数据自洽性
--   tb_reservation 上的 idx uk_reservation_lab_occupy 要求
--   「同一实验室 + 同一日期 + 同一时段」在占用状态下只能有一条，
--   因此下面用混合进制的方式生成 (lab, date, slot) 组合，保证两两不重复：
--     lab  = (x MOD 12) + 1
--     slot = ((x DIV 12) MOD 5) + 1
--     date = 2025-09-01 + (x DIV 60) 天
--   x 取 0..239 时这三元组恰好一一对应，不会撞唯一索引。
-- ============================================================================

SET NAMES utf8mb4;

-- ============================================================================
-- 1. 角色
-- ============================================================================

INSERT INTO tb_role (id, code, name, description) VALUES
(1, 'STUDENT',    '学生',       '预约申请人，可提交预约、排队、报修'),
(2, 'TEACHER',    '教师',       '预约申请人，提交预约时必须填写授课课程'),
(3, 'SYS_ADMIN',  '系统管理员', '管理用户、角色权限与操作日志，不能审批预约'),
(4, 'LAB_ADMIN',  '实验室管理员','维护资源、审批预约与调整申请、派单与验收'),
(5, 'MAINTAINER', '维修人员',   '接收派单并更新维修进度与说明');

-- ============================================================================
-- 2. 权限点
-- ============================================================================

INSERT INTO tb_permission (code, name, module, description) VALUES
-- 用户管理
('user:list',           '查询用户列表',   'user',        NULL),
('user:view',           '查看用户详情',   'user',        NULL),
('user:create',         '新增用户',       'user',        '仅系统管理员'),
('user:update',         '修改用户',       'user',        '仅系统管理员'),
('user:disable',        '注销用户',       'user',        '仅系统管理员'),
('user:view-phone',     '查看完整手机号', 'user',        '脱敏例外，必须写操作日志'),
-- 资源管理
('resource:lab:list',   '查询实验室',     'resource',    NULL),
('resource:lab:create', '新增实验室',     'resource',    NULL),
('resource:lab:update', '修改实验室',     'resource',    NULL),
('resource:lab:disable','停用实验室',     'resource',    NULL),
('resource:equip:list',           '查询设备',   'resource', NULL),
('resource:equip:create',         '新增设备',   'resource', NULL),
('resource:equip:update',         '修改设备',   'resource', NULL),
('resource:equip:disable',        '停用设备',   'resource', NULL),
('resource:log:list',             '查询资源更新日志', 'resource', NULL),
-- 字典
('dict:list',           '查询字典',       'dict',        NULL),
('dict:manage',         '维护字典',       'dict',        '实验类型 / 时段'),
-- 预约
('reservation:create',  '提交预约',       'reservation', NULL),
('reservation:list',    '查询本人预约',   'reservation', NULL),
('reservation:list-all','查询全部预约',   'reservation', NULL),
('reservation:view',    '查看预约详情',   'reservation', NULL),
('reservation:audit',   '审核预约',       'reservation', NULL),
('reservation:cancel',  '撤销预约',       'reservation', NULL),
('reservation:release', '释放已占时段',   'reservation', '触发候补顺延'),
-- 调整申请
('change:create',       '发起调整申请',   'change',      NULL),
('change:list',         '查询调整申请',   'change',      NULL),
('change:audit',        '审批调整申请',   'change',      NULL),
-- 排队
('queue:create',        '加入排队',       'queue',       NULL),
('queue:list-all',      '查看资源队列',   'queue',       NULL),
('queue:cancel',        '取消排队',       'queue',       NULL),
('queue:confirm',       '候补确认/放弃',  'queue',       NULL),
-- 运维
('repair:create',       '提交报修',       'repair',      NULL),
('repair:list',         '查询全部工单',   'repair',      NULL),
('repair:list-assigned','查询我的维修任务','repair',     NULL),
('repair:review',       '复核工单',       'repair',      NULL),
('repair:assign',       '派单',           'repair',      NULL),
('repair:accept',       '维修验收',       'repair',      NULL),
('repair:progress',     '更新维修进度',   'repair',      NULL),
-- 消息
('message:list',        '查看消息',       'message',     NULL),
('message:delivery-log','查看邮件投递记录','message',    NULL),
-- 统计
('stat:view',           '查看统计',       'statistics',  NULL),
('stat:export',         '导出报表',       'statistics',  NULL),
-- 系统
('system:role:list',    '查询角色权限',   'system',      NULL),
('system:role:update',  '修改角色权限',   'system',      NULL),
('system:permission:list','查询权限点',   'system',      NULL),
('system:log:list',     '查询操作日志',   'system',      NULL),
('system:log:export',   '导出操作日志',   'system',      NULL);

-- ============================================================================
-- 3. 角色 → 权限矩阵（用 code 关联，避免硬编码 permission_id）
-- ============================================================================

-- 学生 / 教师：预约 + 排队 + 报修 + 消息
INSERT INTO tb_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM tb_role r
JOIN tb_permission p ON p.code IN (
    'resource:lab:list', 'resource:equip:list',
    'dict:list',
    'reservation:create', 'reservation:list', 'reservation:view', 'reservation:cancel',
    'change:create',
    'queue:create', 'queue:cancel', 'queue:confirm',
    'repair:create',
    'message:list'
)
WHERE r.code IN ('STUDENT', 'TEACHER');

-- 系统管理员：用户管理 + 全部日志与统计
INSERT INTO tb_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM tb_role r
JOIN tb_permission p ON p.code IN (
    'user:list', 'user:view', 'user:create', 'user:update', 'user:disable', 'user:view-phone',
    'resource:lab:list', 'resource:equip:list', 'resource:log:list',
    'dict:list',
    'message:list', 'message:delivery-log',
    'stat:view', 'stat:export',
    'system:role:list', 'system:role:update', 'system:permission:list',
    'system:log:list', 'system:log:export'
)
WHERE r.code = 'SYS_ADMIN';

-- 实验室管理员：资源维护 + 预约审批 + 派单验收（注意：没有 user:create，落实需求 Q32）
INSERT INTO tb_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM tb_role r
JOIN tb_permission p ON p.code IN (
    'user:list', 'user:view', 'user:view-phone',
    'resource:lab:list', 'resource:lab:create', 'resource:lab:update', 'resource:lab:disable',
    'resource:equip:list', 'resource:equip:create', 'resource:equip:update', 'resource:equip:disable',
    'resource:log:list',
    'dict:list', 'dict:manage',
    'reservation:list', 'reservation:list-all', 'reservation:view',
    'reservation:audit', 'reservation:release',
    'change:list', 'change:audit',
    'queue:list-all',
    'repair:create', 'repair:list', 'repair:review', 'repair:assign', 'repair:accept',
    'message:list', 'message:delivery-log',
    'stat:view', 'stat:export',
    'system:log:list'
)
WHERE r.code = 'LAB_ADMIN';

-- 维修人员：只看分配给自己的工单
INSERT INTO tb_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM tb_role r
JOIN tb_permission p ON p.code IN (
    'resource:lab:list', 'resource:equip:list',
    'dict:list',
    'repair:list-assigned', 'repair:progress',
    'message:list'
)
WHERE r.code = 'MAINTAINER';

-- ============================================================================
-- 4. 字典：实验类型 / 时段
-- ============================================================================

INSERT INTO tb_experiment_type (id, code, name, sort, status) VALUES
(1, 'DEMO',        '演示性', 1, 1),
(2, 'VALIDATE',    '验证性', 2, 1),
(3, 'COMPREHENSIVE','综合性', 3, 1),
(4, 'DESIGN',      '设计性', 4, 1),
(5, 'INNOVATE',    '创新性', 5, 1);

INSERT INTO tb_time_slot (id, code, name, start_time, end_time, sort, status) VALUES
(1, 'S1', '1-2节',  '08:00:00', '09:40:00', 1, 1),
(2, 'S2', '3-4节',  '10:00:00', '11:40:00', 2, 1),
(3, 'S3', '5-6节',  '14:00:00', '15:40:00', 3, 1),
(4, 'S4', '7-8节',  '16:00:00', '17:40:00', 4, 1),
(5, 'S5', '9-10节', '19:00:00', '20:40:00', 5, 1);

-- ============================================================================
-- 5. 班级
--    一个班属于一个学院 / 一个专业；学生的学院与专业由班级带出
--    学生端「我的实验课表」靠 tb_reservation_class 反查班级
-- ============================================================================

INSERT INTO tb_class (id, class_name, college, major, grade, student_count, status) VALUES
(1,  '软工2101',   '计算机学院',   '软件工程',             2021, 45, 1),
(2,  '电信2101',   '电子信息学院', '电子信息工程',         2021, 42, 1),
(3,  '机械2101',   '机械工程学院', '机械设计制造及其自动化', 2021, 40, 1),
(4,  '化学2101',   '化学化工学院', '应用化学',             2021, 38, 1),
(5,  '计科2101',   '计算机学院',   '计算机科学与技术',      2021, 46, 1),
(6,  '通信2101',   '电子信息学院', '通信工程',             2021, 41, 1),
(7,  '自动化2101', '机械工程学院', '自动化',               2021, 39, 1),
(8,  '化工2101',   '化学化工学院', '化学工程与工艺',        2021, 37, 1),
(9,  '软工2102',   '计算机学院',   '软件工程',             2021, 44, 1),
(10, '计科2102',   '计算机学院',   '计算机科学与技术',      2021, 45, 1);

-- ============================================================================
-- 6. 用户
--     id 1      系统管理员
--     id 2      实验室管理员
--     id 3-4    维修人员（校内 / 校外）
--     id 5-12   学生（8 个）
--     id 13-16  教师（4 个）
-- ============================================================================

INSERT INTO tb_user (id, username, password, real_name, role_id, phone, email, notify_email, status) VALUES
(1,  'admin',     '$2y$10$xDHNnjoa9FDKDjY4VAwt9uVJzGg1twiwTJcZ4jGX1cjDKOmoHiBFu', '系统管理员', 3, '13800000001', 'admin@lab-reserve.local',    0, 1),
(2,  'labadmin',  '$2y$10$8smwhhyvMIEOD9A/YpzXu.2a1w/Ar8Nat.DlVeDWJPhr7n1nfkIU2', '李文博',     4, '13800000002', 'labadmin@lab-reserve.local', 1, 1),
(3,  'repair01',  '$2y$10$8smwhhyvMIEOD9A/YpzXu.2a1w/Ar8Nat.DlVeDWJPhr7n1nfkIU2', '王建国',     5, '13800000003', 'repair01@lab-reserve.local', 1, 1),
(4,  'repair02',  '$2y$10$8smwhhyvMIEOD9A/YpzXu.2a1w/Ar8Nat.DlVeDWJPhr7n1nfkIU2', '赵师傅',     5, '13800000004', 'repair02@lab-reserve.local', 0, 1),
(5,  'stu2021001','$2y$10$8smwhhyvMIEOD9A/YpzXu.2a1w/Ar8Nat.DlVeDWJPhr7n1nfkIU2', '张一鸣',     1, '13900001001', 'stu2021001@lab-reserve.local', 0, 1),
(6,  'stu2021002','$2y$10$8smwhhyvMIEOD9A/YpzXu.2a1w/Ar8Nat.DlVeDWJPhr7n1nfkIU2', '陈思远',     1, '13900001002', 'stu2021002@lab-reserve.local', 0, 1),
(7,  'stu2021003','$2y$10$8smwhhyvMIEOD9A/YpzXu.2a1w/Ar8Nat.DlVeDWJPhr7n1nfkIU2', '刘雨桐',     1, '13900001003', 'stu2021003@lab-reserve.local', 0, 1),
(8,  'stu2021004','$2y$10$8smwhhyvMIEOD9A/YpzXu.2a1w/Ar8Nat.DlVeDWJPhr7n1nfkIU2', '黄浩然',     1, '13900001004', 'stu2021004@lab-reserve.local', 0, 1),
(9,  'stu2021005','$2y$10$8smwhhyvMIEOD9A/YpzXu.2a1w/Ar8Nat.DlVeDWJPhr7n1nfkIU2', '周欣怡',     1, '13900001005', 'stu2021005@lab-reserve.local', 0, 1),
(10, 'stu2021006','$2y$10$8smwhhyvMIEOD9A/YpzXu.2a1w/Ar8Nat.DlVeDWJPhr7n1nfkIU2', '吴俊杰',     1, '13900001006', 'stu2021006@lab-reserve.local', 0, 1),
(11, 'stu2021007','$2y$10$8smwhhyvMIEOD9A/YpzXu.2a1w/Ar8Nat.DlVeDWJPhr7n1nfkIU2', '郑雅文',     1, '13900001007', 'stu2021007@lab-reserve.local', 0, 1),
(12, 'stu2021008','$2y$10$8smwhhyvMIEOD9A/YpzXu.2a1w/Ar8Nat.DlVeDWJPhr7n1nfkIU2', '孙博文',     1, '13900001008', 'stu2021008@lab-reserve.local', 0, 1),
(13, 'tea1001',   '$2y$10$8smwhhyvMIEOD9A/YpzXu.2a1w/Ar8Nat.DlVeDWJPhr7n1nfkIU2', '王lab教授', 2, '13700002001', 'tea1001@lab-reserve.local',   1, 1),
(14, 'tea1002',   '$2y$10$8smwhhyvMIEOD9A/YpzXu.2a1w/Ar8Nat.DlVeDWJPhr7n1nfkIU2', '李静宜',     2, '13700002002', 'tea1002@lab-reserve.local',   1, 1),
(15, 'tea1003',   '$2y$10$8smwhhyvMIEOD9A/YpzXu.2a1w/Ar8Nat.DlVeDWJPhr7n1nfkIU2', '陈宏达',     2, '13700002003', 'tea1003@lab-reserve.local',   0, 1),
(16, 'tea1004',   '$2y$10$8smwhhyvMIEOD9A/YpzXu.2a1w/Ar8Nat.DlVeDWJPhr7n1nfkIU2', '周美玲',     2, '13700002004', 'tea1004@lab-reserve.local',   0, 1);

INSERT INTO tb_student_info (user_id, student_no, class_id) VALUES
(5,  '2021001', 1),
(6,  '2021002', 2),
(7,  '2021003', 3),
(8,  '2021004', 4),
(9,  '2021005', 5),
(10, '2021006', 6),
(11, '2021007', 7),
(12, '2021008', 8);

INSERT INTO tb_teacher_info (user_id, teacher_no, college, title) VALUES
(13, 'T1001', '计算机学院',   '教授'),
(14, 'T1002', '电子信息学院', '副教授'),
(15, 'T1003', '机械工程学院', '讲师'),
(16, 'T1004', '化学化工学院', '副教授');

INSERT INTO tb_staff_info (user_id, staff_no, department, maintainer_type) VALUES
(1, 'S0001', '信息中心',   NULL),
(2, 'S0002', '实验室管理中心', NULL),
(3, 'M0001', '后勤维修科', 1),
(4, 'M0002', '校外维保单位', 2);

-- ============================================================================
-- 7. 实验室（12 间）
-- ============================================================================

INSERT INTO tb_laboratory (id, lab_no, name, location, open_time_start, open_time_end, open_weekdays, capacity, experiment_type_id, status, description) VALUES
(1,  'LAB-01', '计算机基础实验室',   '实验楼 A301', '08:00:00', '21:00:00', '1,2,3,4,5',   60, 2, 1, '配备 60 台微型计算机'),
(2,  'LAB-02', '程序设计实验室',     '实验楼 A302', '08:00:00', '21:00:00', '1,2,3,4,5',   50, 4, 1, '软件开发与调试'),
(3,  'LAB-03', '网络工程实验室',     '实验楼 A305', '08:00:00', '18:00:00', '1,2,3,4,5',   40, 3, 1, '交换机与路由器实训'),
(4,  'LAB-04', '电子电路实验室',     '实验楼 B201', '08:00:00', '18:00:00', '1,2,3,4,5',   45, 2, 1, '模拟与数字电路'),
(5,  'LAB-05', '嵌入式系统实验室',   '实验楼 B203', '08:00:00', '21:00:00', '1,2,3,4,5',   35, 4, 1, '单片机与嵌入式开发'),
(6,  'LAB-06', '机械设计实验室',     '实验楼 C101', '08:00:00', '18:00:00', '1,2,3,4,5',   40, 4, 1, '机械制图与设计'),
(7,  'LAB-07', '数控加工实验室',     '实验楼 C102', '08:00:00', '18:00:00', '1,2,3,4,5',   25, 1, 1, '数控机床操作实训'),
(8,  'LAB-08', '化工原理实验室',     '实验楼 D201', '08:00:00', '18:00:00', '1,2,3,4,5',   30, 2, 1, '化工单元操作'),
(9,  'LAB-09', '分析化学实验室',     '实验楼 D203', '08:00:00', '18:00:00', '1,2,3,4,5',   30, 3, 1, '仪器分析实训'),
(10, 'LAB-10', '人工智能实验室',     '实验楼 E401', '08:00:00', '21:00:00', '1,2,3,4,5,6', 45, 5, 1, 'GPU 服务器与模型训练'),
(11, 'LAB-11', '大数据实验室',       '实验楼 E403', '08:00:00', '21:00:00', '1,2,3,4,5,6', 45, 3, 1, '分布式计算与数据分析'),
(12, 'LAB-12', '创新创业实验室',     '实验楼 E501', '08:00:00', '21:00:00', '1,2,3,4,5,6,7',30, 5, 1, '开放共享空间');

-- 说明：实验室的可开展实验类型已内联在 tb_laboratory.experiment_type_id（一个实验室只开展一种固定类型），因此没有关联表。

-- ============================================================================
-- 8. 设备（12 间实验室 × 7 种 = 84 台）
-- ============================================================================

INSERT INTO tb_equipment (resource_no, name, model, stock, status, lab_id, next_calibration_date, remark)
WITH RECURSIVE
  labs(li) AS (SELECT 1 UNION ALL SELECT li + 1 FROM labs WHERE li < 12),
  kinds(ei) AS (SELECT 1 UNION ALL SELECT ei + 1 FROM kinds WHERE ei < 7)
SELECT
  CONCAT('ZY-', LPAD(labs.li, 2, '0'), '-', LPAD(kinds.ei, 3, '0')),
  CASE kinds.ei
    WHEN 1 THEN '微型计算机'
    WHEN 2 THEN '数字示波器'
    WHEN 3 THEN '数字万用表'
    WHEN 4 THEN '信号发生器'
    WHEN 5 THEN '直流稳压电源'
    WHEN 6 THEN '焊接工作台'
    ELSE        '网络交换机'
  END,
  CASE kinds.ei
    WHEN 1 THEN '联想 M720q'
    WHEN 2 THEN 'RIGOL DS1102Z-E'
    WHEN 3 THEN 'Fluke 15B+'
    WHEN 4 THEN 'RIGOL DG1022Z'
    WHEN 5 THEN '固纬 GPS-3030D'
    WHEN 6 THEN '白光 FX-888D'
    ELSE        '华为 S5720-28X'
  END,
  CASE kinds.ei
    WHEN 1 THEN 10
    WHEN 2 THEN 4
    WHEN 3 THEN 8
    WHEN 4 THEN 3
    WHEN 5 THEN 6
    WHEN 6 THEN 2
    ELSE        5
  END,
  -- 每 13 台放一台"维修中"，用于演示"维修中设备不可预约"
  IF(MOD(labs.li * 7 + kinds.ei, 13) = 0, 2, 1),
  labs.li,
  DATE_ADD('2026-01-01', INTERVAL kinds.ei MONTH),
  NULL
FROM labs CROSS JOIN kinds;

-- ============================================================================
-- 9. 历史预约（240 条实验室 + 120 条设备），用于统计模块演示
-- ============================================================================

INSERT INTO tb_reservation (
    reserve_no, applicant_id,
    resource_type, lab_id, equipment_id, equipment_count,
    reserve_date, slot_id, use_people,
    experiment_type_id, course_name, purpose, status,
    audit_user_id, audit_time, reject_reason, from_queue, source_queue_id,
    created_at, updated_at)
WITH RECURSIVE x(n) AS (SELECT 0 UNION ALL SELECT n + 1 FROM x WHERE n < 239)
SELECT
  CONCAT('R-', DATE_FORMAT(DATE_ADD('2025-09-01', INTERVAL (x.n DIV 60) DAY), '%Y%m%d'),
         '-', LPAD(x.n + 1, 4, '0')),
  t.user_id,
  1,
  (x.n MOD 12) + 1,
  NULL,
  1,
  DATE_ADD('2025-09-01', INTERVAL (x.n DIV 60) DAY),
  ((x.n DIV 12) MOD 5) + 1,
  -- 使用人数 = 上课班级人数，且不超过实验室容量
  LEAST(c.student_count, l.capacity),
  -- 实验类型必须与所选实验室的可开展类型一致，所以由 lab_id 反查 ELT
  ELT((x.n MOD 12) + 1, 2, 4, 3, 2, 4, 4, 1, 2, 3, 5, 3, 5),
  CONCAT('课程-', ELT((x.n MOD 5) + 1, '程序设计', '电路分析', '机械设计', '化工原理', '人工智能')),
  CONCAT('实验内容：', ELT((x.n MOD 5) + 1, '基础验证实验', '综合设计实验', '数据分析实验', '硬件调试实验', '创新实践实验')),
  CASE
    WHEN x.n MOD 20 = 0 THEN 'REJECTED'
    WHEN x.n MOD 20 = 1 THEN 'CANCELED'
    WHEN x.n MOD 20 IN (2, 3) THEN 'PENDING'
    ELSE 'APPROVED'
  END,
  IF(x.n MOD 20 IN (2, 3), NULL, 2),
  IF(x.n MOD 20 IN (2, 3), NULL,
     DATE_ADD(DATE_ADD('2025-09-01', INTERVAL (x.n DIV 60) DAY), INTERVAL -1 DAY)),
  IF(x.n MOD 20 = 0, '该时段实验室已安排其他课程', NULL),
  0,
  NULL,
  DATE_ADD(DATE_ADD('2025-09-01', INTERVAL (x.n DIV 60) DAY), INTERVAL -(3 + (x.n MOD 3)) DAY),
  DATE_ADD(DATE_ADD('2025-09-01', INTERVAL (x.n DIV 60) DAY), INTERVAL -(3 + (x.n MOD 3)) DAY)
FROM x
JOIN tb_laboratory l ON l.id = (x.n MOD 12) + 1
-- 上课班级由 lab_id 反查，与下面 tb_reservation_class 的写入逻辑保持一致
JOIN tb_class c ON c.id = ((l.id - 1) MOD 10) + 1
-- 实验室只能由教师预约，按班级所属学院取该学院的教师（同一学院只取一个，避免行数翻倍）
JOIN (SELECT college, MIN(user_id) AS user_id FROM tb_teacher_info GROUP BY college) t
  ON t.college = c.college;


-- 实验室预约必须指定上课班级（教师选班级），这里按 lab_id 反查，与学生课表口径一致
INSERT INTO tb_reservation_class (reservation_id, class_id)
SELECT r.id, ((r.lab_id - 1) MOD 10) + 1
FROM tb_reservation r
WHERE r.resource_type = 1;
-- 设备预约（120 条）：占用键为空，不受实验室唯一索引约束
INSERT INTO tb_reservation (
    reserve_no, applicant_id,
    resource_type, lab_id, equipment_id, equipment_count,
    reserve_date, slot_id, use_people,
    experiment_type_id, course_name, purpose, status,
    audit_user_id, audit_time, reject_reason, from_queue, source_queue_id,
    created_at, updated_at)
WITH RECURSIVE y(n) AS (SELECT 0 UNION ALL SELECT n + 1 FROM y WHERE n < 119)
SELECT
  CONCAT('R-D', DATE_FORMAT(DATE_ADD('2025-10-01', INTERVAL (y.n DIV 40) DAY), '%Y%m%d'),
         '-', LPAD(y.n + 1, 4, '0')),
  u.id,
  2,
  NULL,
  (y.n MOD 84) + 1,
  1 + (y.n MOD 3),
  DATE_ADD('2025-10-01', INTERVAL (y.n DIV 40) DAY),
  ((y.n DIV 8) MOD 5) + 1,
  1 + (y.n MOD 20),
  (y.n MOD 5) + 1,
  IF(u.role_id = 2, CONCAT('课程-', ELT((y.n MOD 5) + 1, '程序设计', '电路分析', '机械设计', '化工原理', '人工智能')), NULL),
  CONCAT('设备借用：', ELT((y.n MOD 5) + 1, '基础验证实验', '综合设计实验', '数据分析实验', '硬件调试实验', '创新实践实验')),
  CASE
    WHEN y.n MOD 25 = 0 THEN 'REJECTED'
    WHEN y.n MOD 25 = 1 THEN 'CANCELED'
    ELSE 'APPROVED'
  END,
  2,
  DATE_ADD(DATE_ADD('2025-10-01', INTERVAL (y.n DIV 40) DAY), INTERVAL -1 DAY),
  IF(y.n MOD 25 = 0, '该设备库存不足，请减少数量', NULL),
  0,
  NULL,
  DATE_ADD(DATE_ADD('2025-10-01', INTERVAL (y.n DIV 40) DAY), INTERVAL -(2 + (y.n MOD 3)) DAY),
  DATE_ADD(DATE_ADD('2025-10-01', INTERVAL (y.n DIV 40) DAY), INTERVAL -(2 + (y.n MOD 3)) DAY)
FROM y
JOIN tb_user u ON u.id = 5 + (y.n MOD 12);

-- ============================================================================
-- 10. 历史消息（给每个学生/教师制造一些站内信，用于未读角标演示）
-- ============================================================================

INSERT INTO tb_message (receiver_id, type, title, content, biz_type, biz_id,
                        email_required, email_state, is_read, is_deleted, created_at, updated_at)
SELECT
  r.applicant_id,
  'RESERVATION_AUDIT',
  CASE r.status
    WHEN 'APPROVED' THEN '预约审核通过'
    WHEN 'REJECTED' THEN '预约已被驳回'
    WHEN 'CANCELED' THEN '预约已取消'
    ELSE '预约状态更新'
  END,
  CONCAT('你的预约单 ', r.reserve_no, ' 当前状态：', r.status, '。'),
  'RESERVATION',
  r.id,
  0,
  NULL,
  IF(r.id MOD 3 = 0, 0, 1),
  0,
  DATE_ADD(r.created_at, INTERVAL 1 HOUR),
  DATE_ADD(r.created_at, INTERVAL 1 HOUR)
FROM tb_reservation r
WHERE r.id <= 120;

-- ============================================================================
-- 11. 演示排队与候补（让"我的排队"页面一进来看得到东西）
-- ============================================================================

INSERT INTO tb_resource_queue (queue_no, resource_type, lab_id, equipment_id,
                               reserve_date, slot_id, applicant_id, status, join_time, created_at, updated_at)
VALUES
('P-LAB-01-20251010-001', 1, 1,  NULL, '2025-10-10', 2, 5,  'QUEUING',  '2025-10-08 09:10:00', '2025-10-08 09:10:00', '2025-10-08 09:10:00'),
('P-LAB-01-20251010-002', 1, 1,  NULL, '2025-10-10', 2, 6,  'QUEUING',  '2025-10-08 09:25:00', '2025-10-08 09:25:00', '2025-10-08 09:25:00'),
('P-LAB-01-20251010-003', 1, 1,  NULL, '2025-10-10', 2, 7,  'QUEUING',  '2025-10-08 10:02:00', '2025-10-08 10:02:00', '2025-10-08 10:02:00'),
('P-LAB-03-20251011-001', 1, 3,  NULL, '2025-10-11', 3, 9,  'NOTIFIED', '2025-10-08 14:00:00', '2025-10-08 14:00:00', '2025-10-09 09:00:00'),
('P-LAB-05-20251012-001', 1, 5,  NULL, '2025-10-12', 1, 10, 'QUEUING',  '2025-10-09 08:30:00', '2025-10-09 08:30:00', '2025-10-09 08:30:00'),
('P-ZY-01-001-20251012-001', 2, NULL, 1, '2025-10-12', 4, 11, 'QUEUING', '2025-10-09 11:00:00', '2025-10-09 11:00:00', '2025-10-09 11:00:00');

INSERT INTO tb_candidate_notice_log (queue_id, notice_time, deadline, channel, action, action_time, seq, created_at)
SELECT q.id, '2025-10-09 09:00:00', '2025-10-09 09:30:00', 1, 0, NULL, 1, '2025-10-09 09:00:00'
FROM tb_resource_queue q
WHERE q.queue_no = 'P-LAB-03-20251011-001';

-- ============================================================================
-- 12. 演示报修工单
-- ============================================================================

INSERT INTO tb_repair_order (order_no, submitter_id, equipment_id,
                             fault_desc, fault_scene, fault_images, priority, status,
                             reviewer_id, review_time, review_opinion,
                             assignee_id, assign_time,
                             accept_user_id, accept_time, accept_result, accept_opinion, archive_time,
                             created_at, updated_at)
VALUES
('WX-20250920-0001', 5, 1,
 '开机后屏幕无显示，主机风扇不转', '2025-09-20 上午第三节课，做操作系统实验时',
 NULL, 3, 'ACCEPTED', 2, '2025-09-20 14:00:00', '确认设备故障，已转维修',
 3, '2025-09-20 15:00:00', 2, '2025-09-22 10:00:00', 1, '更换电源后测试正常', '2025-09-22 10:05:00',
 '2025-09-20 11:30:00', '2025-09-22 10:05:00'),
('WX-20250925-0002', 6, 9,
 '示波器屏幕闪烁，波形不稳定', '2025-09-25 下午第一节课',
 NULL, 2, 'REPAIRING', 2, '2025-09-25 16:00:00', '初步判断为探头接触不良',
 3, '2025-09-25 17:00:00', NULL, NULL, NULL, NULL, NULL,
 '2025-09-25 15:20:00', '2025-09-26 09:00:00'),
('WX-20250928-0003', 7, 17,
 '万用表读数跳变，无法归零', '2025-09-28 下午实验课',
 NULL, 1, 'ASSIGNED', 2, '2025-09-28 15:00:00', '确认设备故障',
 4, '2025-09-28 16:00:00', NULL, NULL, NULL, NULL, NULL,
 '2025-09-28 14:10:00', '2025-09-28 16:00:00'),
('WX-20251001-0004', 8, 25,
 '信号发生器输出无波形', '2025-10-01 上午实验',
 NULL, 4, 'SUBMITTED', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 '2025-10-01 10:30:00', '2025-10-01 10:30:00'),
('WX-20251002-0005', 9, 33,
 '电源输出不稳定，电压漂移', '2025-10-02 下午实验',
 NULL, 2, 'SUBMITTED', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 '2025-10-02 15:00:00', '2025-10-02 15:00:00');

INSERT INTO tb_repair_record (order_id, maintainer_id, status, repair_desc, parts_replaced, created_at)
SELECT o.id, 3, 1, '已到达现场，正在排查电源模块', NULL, '2025-09-25 17:30:00'
FROM tb_repair_order o WHERE o.order_no = 'WX-20250925-0002';

INSERT INTO tb_repair_record (order_id, maintainer_id, status, repair_desc, parts_replaced, created_at)
SELECT o.id, 3, 1, '定位为电源模块损坏，等待配件', NULL, '2025-09-20 15:30:00'
FROM tb_repair_order o WHERE o.order_no = 'WX-20250920-0001';

INSERT INTO tb_repair_record (order_id, maintainer_id, status, repair_desc, parts_replaced, created_at)
SELECT o.id, 3, 2, '更换电源模块 1 个，通电测试正常', '电源模块 ATX-500W × 1', '2025-09-22 09:40:00'
FROM tb_repair_order o WHERE o.order_no = 'WX-20250920-0001';

-- ============================================================================
-- 13. 资源更新日志样例
-- ============================================================================

INSERT INTO tb_resource_update_log (resource_type, resource_id, operation,
                                    before_json, after_json, update_reason, operator_id, created_at)
VALUES
(1, 1, 2,
 '{"capacity":50,"location":"实验楼 A301"}',
 '{"capacity":60,"location":"实验楼 A301"}',
 '扩容：新增 10 个机位', 2, '2025-09-05 10:00:00'),
(2, 1, 2,
 '{"stock":8,"status":1}',
 '{"stock":10,"status":1}',
 '新采购 2 台', 2, '2025-09-06 11:20:00'),
(1, 12, 1,
 NULL,
 '{"labNo":"LAB-12","capacity":30}',
 '新增开放共享空间', 2, '2025-09-01 09:00:00');
