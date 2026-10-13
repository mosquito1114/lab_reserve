package com.labreserve.common.constant;

/**
 * 全局通用常量。
 */
public final class CommonConst {

    private CommonConst() {
    }

    // ------------------------------------------------------------
    // 分页
    // ------------------------------------------------------------

    public static final int DEFAULT_PAGE_NUM = 1;
    public static final int DEFAULT_PAGE_SIZE = 10;
    /** 单页最大条数，超过则被截断 */
    public static final int PAGE_SIZE_MAX = 100;
    /** 深分页阈值，超过该偏移量的统计类查询应改用游标（id > lastId） */
    public static final long DEEP_PAGE_THRESHOLD = 10_000L;

    // ------------------------------------------------------------
    // 时间格式
    // ------------------------------------------------------------

    public static final String DATE_PATTERN = "yyyy-MM-dd";
    public static final String TIME_PATTERN = "HH:mm";
    public static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_COMPACT_PATTERN = "yyyyMMdd";
    public static final String ZONE_ID = "Asia/Shanghai";

    // ------------------------------------------------------------
    // 认证
    // ------------------------------------------------------------

    public static final String TOKEN_HEADER = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";
    /** 登录失败次数上限 */
    public static final int LOGIN_FAIL_MAX = 5;
    /** 登录失败锁定分钟数 */
    public static final long LOGIN_LOCK_MINUTES = 15;

    // ------------------------------------------------------------
    // 角色编码（与 tb_role.code 一致）
    // ------------------------------------------------------------

    public static final String ROLE_STUDENT = "STUDENT";
    public static final String ROLE_TEACHER = "TEACHER";
    public static final String ROLE_SYS_ADMIN = "SYS_ADMIN";
    public static final String ROLE_LAB_ADMIN = "LAB_ADMIN";
    public static final String ROLE_MAINTAINER = "MAINTAINER";

    // ------------------------------------------------------------
    // 业务规则默认值
    // ------------------------------------------------------------

    /** 候补通知的确认时限（分钟）——需求 Q18：默认 30 分钟，可配置 */
    public static final int CANDIDATE_NOTICE_TIMEOUT_MINUTES = 30;
    /** 候补放弃 / 超时后的排队禁入天数——需求 Q17 */
    public static final int QUEUE_BAN_DAYS = 7;
    /** 发起调整申请需要提前的小时数——需求 Q13 */
    public static final int CHANGE_REQUEST_AHEAD_HOURS = 24;
    /** 邮件投递最大重试次数，超过进死信队列 */
    public static final int EMAIL_MAX_RETRY = 3;
    /** 已删除消息的物理清理保留天数 */
    public static final int MESSAGE_CLEANUP_RETAIN_DAYS = 30;

    /** 资源类型：实验室 */
    public static final int RESOURCE_TYPE_LAB = 1;
    /** 资源类型：设备 */
    public static final int RESOURCE_TYPE_EQUIPMENT = 2;

    // ------------------------------------------------------------
    // 编号格式
    // ------------------------------------------------------------

    /** 预约号：R-YYYYMMDD-XXXX */
    public static final String RESERVE_NO_PREFIX = "R-";
    /** 工单号：WX-YYYYMMDD-XXXX */
    public static final String REPAIR_ORDER_NO_PREFIX = "WX-";
    /** 排队号：P-{资源编号}-YYYYMMDD-XXX */
    public static final String QUEUE_NO_PREFIX = "P-";

    /** 设备编号正则：ZY-XX-YYY */
    public static final String EQUIPMENT_NO_REGEX = "^ZY-\\d{2}-\\d{3}$";
    /** 实验室编号正则：LAB-XX */
    public static final String LAB_NO_REGEX = "^LAB-\\d{2}$";

    public static final int SEQ_LENGTH_RESERVE_NO = 4;
    public static final int SEQ_LENGTH_ORDER_NO = 4;
    public static final int SEQ_LENGTH_QUEUE_NO = 3;
}
