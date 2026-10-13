-- ============================================================================
-- lab_reserve 初始化表结构  V1
--
-- 对应文档：agents/design.md §3
-- 执行者：Flyway（应用启动时自动执行，只需在全新库上跑一次）
--
-- 说明：
--   1. 这里不写 DROP TABLE —— Flyway 的迁移脚本只应执行一次，
--      带 DROP 反而会在异常重跑时静默清空数据。要重建请 `docker compose down -v`。
--   2. 表名统一 tb_ 前缀；索引名沿用逻辑名，不重复带 tb_。
--   3. created_at / updated_at 全部交给数据库默认值，
--      因此 Java 侧不需要 MetaObjectHandler。
--   4. 业务主体表（tb_user / tb_laboratory / tb_equipment / tb_reservation）
--      不物理删除，用 status 表达"注销 / 停用 / 已取消"。
--   5. 反冗余原则：能通过 join 查到的字段一律不落库（详见 agents/design.md §1.6）。
--      因此本脚本里没有"姓名快照 / 编号快照 / 角色快照"这类字段，
--      查询时 join 对应的主表即可。
-- ============================================================================

SET NAMES utf8mb4;

-- ============================================================================
-- 一、用户域
-- ============================================================================

CREATE TABLE tb_user (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  username      VARCHAR(50)  NOT NULL COMMENT '登录账号',
  password      VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希',
  real_name     VARCHAR(50)  NOT NULL COMMENT '真实姓名',
  role_id       BIGINT       NOT NULL COMMENT '角色ID',
  phone         VARCHAR(20)           DEFAULT NULL COMMENT '手机号（明文存储，接口层脱敏展示）',
  email         VARCHAR(100)          DEFAULT NULL COMMENT '邮箱',
  notify_email  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否开启邮件通知 0否 1是',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1正常 0已注销',
  last_login_at DATETIME              DEFAULT NULL COMMENT '最后登录时间',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_username (username),
  KEY idx_user_role (role_id),
  KEY idx_user_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表（统一账号）';

CREATE TABLE tb_class (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  class_name    VARCHAR(50) NOT NULL COMMENT '班级名称，如 软工2101',
  college       VARCHAR(50) NOT NULL COMMENT '所属学院',
  major         VARCHAR(50) NOT NULL COMMENT '专业',
  grade         INT         NOT NULL COMMENT '年级，如 2021',
  student_count INT         NOT NULL DEFAULT 0 COMMENT '班级人数',
  status        TINYINT     NOT NULL DEFAULT 1 COMMENT '1正常 0停用',
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_class_name (class_name),
  KEY idx_class_college (college),
  KEY idx_class_major (major),
  KEY idx_class_grade (grade)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班级表';

CREATE TABLE tb_student_info (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  user_id    BIGINT      NOT NULL COMMENT '关联 tb_user.id',
  student_no VARCHAR(20) NOT NULL COMMENT '学号',
  -- 学院 / 专业 / 班级名都从 tb_class 带出，这里只存外键，避免双真相源
  class_id   BIGINT      NOT NULL COMMENT '所属班级ID',
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_student_user (user_id),
  UNIQUE KEY uk_student_no (student_no),
  KEY idx_student_class (class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生信息扩展表';

CREATE TABLE tb_teacher_info (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  user_id    BIGINT      NOT NULL COMMENT '关联 tb_user.id',
  teacher_no VARCHAR(20) NOT NULL COMMENT '工号',
  college    VARCHAR(50) NOT NULL COMMENT '所属学院',
  title      VARCHAR(30)          DEFAULT NULL COMMENT '职称',
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_teacher_user (user_id),
  UNIQUE KEY uk_teacher_no (teacher_no),
  KEY idx_teacher_college (college)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师信息扩展表';

CREATE TABLE tb_staff_info (
  id              BIGINT      NOT NULL AUTO_INCREMENT,
  user_id         BIGINT      NOT NULL COMMENT '关联 tb_user.id',
  staff_no        VARCHAR(20) NOT NULL COMMENT '工号',
  department      VARCHAR(50)          DEFAULT NULL COMMENT '部门',
  maintainer_type TINYINT              DEFAULT NULL COMMENT '维修人员类别 1校内 2校外（仅维修人员有值）',
  created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_staff_user (user_id),
  UNIQUE KEY uk_staff_no (staff_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理类人员扩展表（系统管理员/实验室管理员/维修人员）';

-- ============================================================================
-- 二、权限域（精简 RBAC：角色 - 权限 - 角色权限）
--    5 个角色互斥，因此 tb_user.role_id 直接外键，不需要 user_role 中间表
-- ============================================================================

CREATE TABLE tb_role (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  code        VARCHAR(30) NOT NULL COMMENT '角色编码 STUDENT/TEACHER/SYS_ADMIN/LAB_ADMIN/MAINTAINER',
  name        VARCHAR(50) NOT NULL COMMENT '角色名称',
  description VARCHAR(255)         DEFAULT NULL,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

CREATE TABLE tb_permission (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  code        VARCHAR(60) NOT NULL COMMENT '权限码 模块:资源:动作，如 reservation:audit',
  name        VARCHAR(50) NOT NULL COMMENT '权限名称',
  module      VARCHAR(30) NOT NULL COMMENT '所属模块',
  description VARCHAR(255)         DEFAULT NULL,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_permission_code (code),
  KEY idx_permission_module (module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限点表';

CREATE TABLE tb_role_permission (
  id            BIGINT   NOT NULL AUTO_INCREMENT,
  role_id       BIGINT   NOT NULL,
  permission_id BIGINT   NOT NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_permission (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- ============================================================================
-- 三、资源域
-- ============================================================================

CREATE TABLE tb_experiment_type (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  code       VARCHAR(30) NOT NULL COMMENT '编码 DEMO/VALIDATE/COMPREHENSIVE/DESIGN/INNOVATE',
  name       VARCHAR(50) NOT NULL COMMENT '名称 演示性/验证性/综合性/设计性/创新性',
  sort       INT         NOT NULL DEFAULT 0,
  status     TINYINT     NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_experiment_type_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实验类型字典表';

CREATE TABLE tb_time_slot (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  code       VARCHAR(20) NOT NULL COMMENT '编码 S1/S2/...',
  name       VARCHAR(30) NOT NULL COMMENT '名称 如 1-2节',
  start_time TIME        NOT NULL COMMENT '开始时间',
  end_time   TIME        NOT NULL COMMENT '结束时间',
  sort       INT         NOT NULL DEFAULT 0,
  status     TINYINT     NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_time_slot_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='固定时段字典表';

CREATE TABLE tb_laboratory (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  lab_no          VARCHAR(20)  NOT NULL COMMENT '实验室编号 LAB-XX',
  name            VARCHAR(50)  NOT NULL COMMENT '名称',
  location        VARCHAR(100) NOT NULL COMMENT '位置',
  open_time_start TIME         NOT NULL COMMENT '开放开始时间',
  open_time_end   TIME         NOT NULL COMMENT '开放结束时间',
  open_weekdays   VARCHAR(20)  NOT NULL DEFAULT '1,2,3,4,5' COMMENT '开放星期，1=周一',
  capacity        INT          NOT NULL COMMENT '容纳人数',
  -- 一个实验室只开展一种固定类型的实验，因此直接用外键，不需要关联表
  experiment_type_id BIGINT    NOT NULL COMMENT '可开展实验类型ID',
  status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1可用 0停用',
  description     VARCHAR(255)          DEFAULT NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_laboratory_no (lab_no),
  KEY idx_laboratory_status (status),
  KEY idx_laboratory_location (location),
  KEY idx_laboratory_experiment_type (experiment_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实验室表';

CREATE TABLE tb_equipment (
  id                    BIGINT       NOT NULL AUTO_INCREMENT,
  resource_no           VARCHAR(30)  NOT NULL COMMENT '资源编号 ZY-XX-YYY',
  name                  VARCHAR(50)  NOT NULL COMMENT '名称',
  model                 VARCHAR(50)           DEFAULT NULL COMMENT '型号',
  stock                 INT          NOT NULL DEFAULT 1 COMMENT '库存数量',
  status                TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 2维修中 3报废 0停用',
  lab_id                BIGINT       NOT NULL COMMENT '所属实验室ID',
  next_calibration_date DATE                  DEFAULT NULL COMMENT '下次校准日期（手工填写，系统不推算不提醒）',
  remark                VARCHAR(255)          DEFAULT NULL,
  created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_equipment_no (resource_no),
  KEY idx_equipment_lab (lab_id),
  KEY idx_equipment_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备表';

CREATE TABLE tb_resource_update_log (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  resource_type TINYINT      NOT NULL COMMENT '1实验室 2设备',
  resource_id   BIGINT       NOT NULL COMMENT '资源ID',
  operation     TINYINT      NOT NULL COMMENT '1新增 2修改 3删除/停用',
  before_json   TEXT                  DEFAULT NULL COMMENT '变更前快照',
  after_json    TEXT                  DEFAULT NULL COMMENT '变更后快照',
  update_reason VARCHAR(255) NOT NULL COMMENT '更新原因（强制填写）',
  operator_id   BIGINT       NOT NULL COMMENT '操作人',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_rul_resource (resource_type, resource_id),
  KEY idx_rul_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源更新日志表';

-- ============================================================================
-- 四、预约域
-- ============================================================================

CREATE TABLE tb_reservation (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  reserve_no         VARCHAR(30)  NOT NULL COMMENT '预约号 R-YYYYMMDD-XXXX',
  applicant_id       BIGINT       NOT NULL COMMENT '申请人 tb_user.id',
  resource_type      TINYINT      NOT NULL COMMENT '1实验室 2设备',
  lab_id             BIGINT                DEFAULT NULL COMMENT '实验室ID（type=1 必填）',
  equipment_id       BIGINT                DEFAULT NULL COMMENT '设备ID（type=2 必填，主资源）',
  equipment_count    INT          NOT NULL DEFAULT 1 COMMENT '设备数量（type=2 时有效）',
  reserve_date       DATE         NOT NULL COMMENT '预约日期',
  slot_id            BIGINT       NOT NULL COMMENT '时段ID',
  use_people         INT          NOT NULL DEFAULT 1 COMMENT '使用人数',
  experiment_type_id BIGINT       NOT NULL COMMENT '实验类型ID',
  course_name        VARCHAR(100)          DEFAULT NULL COMMENT '授课课程（教师必填）',
  purpose            VARCHAR(500) NOT NULL COMMENT '实验内容/用途',
  status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                     COMMENT 'PENDING/APPROVED/REJECTED/CANCELED/CHANGE_PENDING/EXPIRED',
  audit_user_id      BIGINT                DEFAULT NULL COMMENT '审核人',
  audit_time         DATETIME              DEFAULT NULL,
  reject_reason      VARCHAR(255)          DEFAULT NULL COMMENT '驳回原因',
  from_queue         TINYINT      NOT NULL DEFAULT 0 COMMENT '是否由排队转正 0否 1是',
  source_queue_id    BIGINT                DEFAULT NULL COMMENT '来源排队记录ID',
  created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  -- 并发兜底：只有"占用中"的状态才生成键值，其余为 NULL。
  -- MySQL 唯一索引允许存在多个 NULL，因此不影响已取消/已驳回的记录。
  occupy_key VARCHAR(80) GENERATED ALWAYS AS (
      IF(status IN ('PENDING','APPROVED','CHANGE_PENDING') AND resource_type = 1,
         CONCAT(lab_id, '-', reserve_date, '-', slot_id), NULL)
  ) STORED COMMENT '实验室时段占用唯一键（并发兜底）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_reservation_no (reserve_no),
  UNIQUE KEY uk_reservation_lab_occupy (occupy_key),
  KEY idx_reservation_lab_slot (lab_id, reserve_date, slot_id, status),
  KEY idx_reservation_equip_slot (equipment_id, reserve_date, slot_id, status),
  KEY idx_reservation_applicant (applicant_id),
  KEY idx_reservation_status (status),
  KEY idx_reservation_date (reserve_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约申请表';

CREATE TABLE tb_reservation_class (
  id             BIGINT   NOT NULL AUTO_INCREMENT,
  reservation_id BIGINT   NOT NULL,
  class_id       BIGINT   NOT NULL,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_res_class (reservation_id, class_id),
  KEY idx_res_class_class (class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约上课班级关联表（教师预约实验室时指定，学生端课表的数据来源）';

CREATE TABLE tb_reservation_equipment (
  id              BIGINT   NOT NULL AUTO_INCREMENT,
  reservation_id  BIGINT   NOT NULL,
  equipment_id    BIGINT   NOT NULL,
  equipment_count INT      NOT NULL DEFAULT 1 COMMENT '本次需要的数量',
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_res_equip (reservation_id, equipment_id),
  KEY idx_res_equip_equip (equipment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实验室预约附带的设备清单（“增加设备”调整的落点）';

CREATE TABLE tb_reservation_change_request (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  reservation_id BIGINT       NOT NULL,
  applicant_id   BIGINT       NOT NULL COMMENT '发起人',
  change_type    TINYINT      NOT NULL COMMENT '1修改 2取消',
  before_json    TEXT         NOT NULL COMMENT '变更前完整快照',
  after_json     TEXT         NOT NULL COMMENT '变更后完整快照（取消时与变更前一致）',
  reason         VARCHAR(255) NOT NULL COMMENT '申请原因',
  status         TINYINT      NOT NULL DEFAULT 0 COMMENT '0待审批 1通过 2驳回',
  audit_user_id  BIGINT                DEFAULT NULL,
  audit_time     DATETIME              DEFAULT NULL,
  audit_opinion  VARCHAR(255)          DEFAULT NULL,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_rcr_reservation (reservation_id),
  KEY idx_rcr_status (status),
  KEY idx_rcr_applicant (applicant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约调整申请记录表';

-- ============================================================================
-- 五、排队域
-- ============================================================================

CREATE TABLE tb_resource_queue (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  queue_no      VARCHAR(40) NOT NULL COMMENT '排队号 P-资源编号-YYYYMMDD-序号',
  resource_type TINYINT     NOT NULL COMMENT '1实验室 2设备',
  lab_id        BIGINT               DEFAULT NULL COMMENT '主资源：实验室ID',
  equipment_id  BIGINT               DEFAULT NULL COMMENT '主资源：设备ID',
  reserve_date  DATE        NOT NULL COMMENT '预约日期',
  slot_id       BIGINT      NOT NULL COMMENT '时段ID',
  applicant_id  BIGINT      NOT NULL,
  status        VARCHAR(20) NOT NULL DEFAULT 'QUEUING'
                COMMENT 'QUEUING/NOTIFIED/CONFIRMED/ABANDONED/TIMEOUT/CANCELED',
  join_time     DATETIME    NOT NULL COMMENT '入队时间（= Redis ZSet score）',
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_queue_no (queue_no),
  UNIQUE KEY uk_queue_user_slot (applicant_id, resource_type, lab_id, equipment_id, reserve_date, slot_id)
    COMMENT '同一用户对同一资源同一时段只能排一次',
  KEY idx_queue_lab_slot (lab_id, reserve_date, slot_id, status),
  KEY idx_queue_equip_slot (equipment_id, reserve_date, slot_id, status),
  KEY idx_queue_applicant (applicant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源排队记录表';

CREATE TABLE tb_candidate_notice_log (
  id           BIGINT   NOT NULL AUTO_INCREMENT,
  queue_id     BIGINT   NOT NULL COMMENT '排队记录ID',
  notice_time  DATETIME NOT NULL COMMENT '通知发出时间',
  deadline     DATETIME NOT NULL COMMENT '确认截止时间（默认 +30 分钟）',
  channel      TINYINT  NOT NULL DEFAULT 1 COMMENT '1站内信 2邮件',
  action       TINYINT  NOT NULL DEFAULT 0 COMMENT '0未响应 1确认 2放弃 3超时',
  action_time  DATETIME          DEFAULT NULL,
  seq          INT      NOT NULL DEFAULT 1 COMMENT '该排队记录第几次被通知（顺延后会再次通知）',
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_cnl_queue (queue_id),
  KEY idx_cnl_deadline (deadline, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='候补通知记录表';

CREATE TABLE tb_queue_ban_record (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  user_id       BIGINT       NOT NULL COMMENT '被禁入用户',
  resource_type TINYINT      NOT NULL COMMENT '1实验室 2设备',
  lab_id        BIGINT                DEFAULT NULL,
  equipment_id  BIGINT                DEFAULT NULL,
  reason        VARCHAR(255) NOT NULL COMMENT '禁入原因',
  start_time    DATETIME     NOT NULL,
  end_time      DATETIME     NOT NULL COMMENT '默认 start_time + 7 天',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_qbr_user_resource (user_id, resource_type, lab_id, equipment_id, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排队禁入记录表（7天）';

-- ============================================================================
-- 六、通知域
--    只有这一张表：站内信本体 + 邮件投递终态（S2 方案）
--    邮件重试状态全部在 Redis Stream 里，不落库
-- ============================================================================

CREATE TABLE tb_message (
  id               BIGINT        NOT NULL AUTO_INCREMENT,
  receiver_id      BIGINT        NOT NULL COMMENT '接收人 tb_user.id',
  type             VARCHAR(30)   NOT NULL
                   COMMENT 'RESERVATION_AUDIT/QUEUE_POSITION/CANDIDATE_NOTICE/REPAIR/CHANGE_AUDIT/SYSTEM',
  title            VARCHAR(100)  NOT NULL,
  content          VARCHAR(1000) NOT NULL COMMENT '正文（也是渲染邮件的唯一来源）',
  biz_type         VARCHAR(30)            DEFAULT NULL COMMENT '关联业务类型 RESERVATION/QUEUE/REPAIR_ORDER',
  biz_id           BIGINT                 DEFAULT NULL COMMENT '关联业务ID',
  email_required   TINYINT       NOT NULL DEFAULT 0 COMMENT '是否需要邮件投递 0否 1是',
  email_state      VARCHAR(10)            DEFAULT NULL COMMENT 'NULL未发/投递中 OK成功 DEAD死信',
  email_last_error VARCHAR(500)           DEFAULT NULL COMMENT '最后一次失败原因',
  is_read          TINYINT       NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  is_deleted       TINYINT       NOT NULL DEFAULT 0 COMMENT '0正常 1已删除（用户视角）',
  created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_message_receiver (receiver_id, is_deleted, created_at),
  KEY idx_message_unread (receiver_id, is_read, is_deleted),
  KEY idx_message_email_state (email_state, email_required)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表（站内信本体 + 邮件终态）';

-- ============================================================================
-- 七、运维域
--    验收只发生一次，字段内联在 tb_repair_order，不单独建表
-- ============================================================================

CREATE TABLE tb_repair_order (
  id             BIGINT        NOT NULL AUTO_INCREMENT,
  order_no       VARCHAR(30)   NOT NULL COMMENT '工单号 WX-YYYYMMDD-XXXX',
  submitter_id   BIGINT        NOT NULL COMMENT '报修人',
  equipment_id   BIGINT        NOT NULL COMMENT '报修设备',
  fault_desc     VARCHAR(1000) NOT NULL COMMENT '故障现象',
  fault_scene    VARCHAR(255)  NOT NULL COMMENT '发生场景',
  fault_images   VARCHAR(1000)          DEFAULT NULL COMMENT '故障截图 URL（OSS），逗号分隔',
  priority       TINYINT       NOT NULL DEFAULT 2 COMMENT '1低 2中 3高 4紧急',
  status         VARCHAR(20)   NOT NULL DEFAULT 'SUBMITTED'
                 COMMENT 'SUBMITTED/REJECTED/ASSIGNED/REPAIRING/REPAIRED/ACCEPTED/REOPENED',
  reviewer_id    BIGINT                 DEFAULT NULL COMMENT '复核人（实验室管理员）',
  review_time    DATETIME               DEFAULT NULL,
  review_opinion VARCHAR(255)           DEFAULT NULL COMMENT '复核意见',
  assignee_id    BIGINT                 DEFAULT NULL COMMENT '指派维修人',
  assign_time    DATETIME               DEFAULT NULL,
  accept_user_id BIGINT                 DEFAULT NULL COMMENT '验收人',
  accept_time    DATETIME               DEFAULT NULL,
  accept_result  TINYINT                DEFAULT NULL COMMENT '1通过 2不通过',
  accept_opinion VARCHAR(255)           DEFAULT NULL,
  archive_time   DATETIME               DEFAULT NULL COMMENT '归档时间',
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_repair_order_no (order_no),
  KEY idx_repair_status (status),
  KEY idx_repair_equipment (equipment_id),
  KEY idx_repair_submitter (submitter_id),
  KEY idx_repair_assignee (assignee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备报修工单表（含验收字段）';

CREATE TABLE tb_repair_record (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  order_id        BIGINT        NOT NULL,
  maintainer_id   BIGINT        NOT NULL COMMENT '维修人',
  status          TINYINT       NOT NULL COMMENT '1维修中 2已完成',
  repair_desc     VARCHAR(1000)          DEFAULT NULL COMMENT '维修说明（完成后必填）',
  parts_replaced  VARCHAR(500)           DEFAULT NULL COMMENT '配件更换记录',
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_repair_record_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维修进度记录表';

-- ============================================================================
-- 八、审计域
--    只增不改不删；操作人姓名与角色通过 operator_id join tb_user / tb_role 得到
-- ============================================================================

CREATE TABLE tb_operation_log (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  operator_id    BIGINT                DEFAULT NULL COMMENT '操作人ID（登录失败时为空）',
  module         VARCHAR(30)  NOT NULL COMMENT '模块 auth/user/resource/reservation/queue/repair/message/statistics/system',
  operation_type VARCHAR(30)  NOT NULL COMMENT 'LOGIN/LOGOUT/CREATE/UPDATE/DELETE/AUDIT/EXPORT/ASSIGN/ACCEPT/QUERY_PHONE',
  content        VARCHAR(500) NOT NULL COMMENT '操作内容描述',
  request_method VARCHAR(10)           DEFAULT NULL,
  request_url    VARCHAR(255)          DEFAULT NULL,
  request_param  TEXT                  DEFAULT NULL COMMENT '请求参数（已脱敏）',
  ip             VARCHAR(50)           DEFAULT NULL COMMENT '客户端 IP',
  user_agent     VARCHAR(255)          DEFAULT NULL,
  result         TINYINT      NOT NULL DEFAULT 1 COMMENT '1成功 0失败',
  error_msg      VARCHAR(500)          DEFAULT NULL,
  cost_ms        INT                   DEFAULT NULL COMMENT '耗时毫秒',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_oplog_operator (operator_id),
  KEY idx_oplog_type (operation_type),
  KEY idx_oplog_created (created_at),
  KEY idx_oplog_module (module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表（只增不改不删）';
