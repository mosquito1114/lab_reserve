package com.labreserve.common.constant;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Redis Key 命名规范。
 *
 * <p>对应 {@code agents/design.md} §5，所有 Key 都必须从这里拼接，<b>禁止硬编码</b>。
 *
 * <p>关于 TTL：排队 ZSet、收件箱 ZSet、候补 deadline ZSet、
 * 消息未读数都是业务数据而非缓存，<b>不设过期</b>；
 * 因此 Redis 的 {@code maxmemory-policy} 必须是 {@code noeviction}（见 docker/redis/redis.conf）。
 */
public final class RedisKeyConst {

    private RedisKeyConst() {
    }

    /** 全局前缀，避免与同实例上的其他应用冲突 */
    public static final String PREFIX = "lab:reserve:";

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");

    // ============================================================
    // 认证与权限
    // ============================================================

    /** Refresh Token 存储：{@code lab:reserve:auth:token:{userId}} */
    public static final String AUTH_TOKEN = PREFIX + "auth:token:";

    /** Access Token 登出黑名单：{@code lab:reserve:auth:blacklist:{jti}} */
    public static final String AUTH_BLACKLIST = PREFIX + "auth:blacklist:";

    /** 登录失败计数：{@code lab:reserve:login:fail:{username}} */
    public static final String LOGIN_FAIL = PREFIX + "login:fail:";

    /** 角色权限码集合缓存：{@code lab:reserve:perm:role:{roleId}} */
    public static final String PERM_ROLE = PREFIX + "perm:role:";

    // ============================================================
    // 分布式锁
    // ============================================================

    /** 预约判冲锁：{@code lab:reserve:lock:reserve:{type}:{resId}:{date}:{slotId}} */
    public static final String LOCK_RESERVE = PREFIX + "lock:reserve:";

    /** 排队队列操作锁：{@code lab:reserve:lock:queue:{type}:{resId}:{date}:{slotId}} */
    public static final String LOCK_QUEUE = PREFIX + "lock:queue:";

    /** 定时任务互斥锁：{@code lab:reserve:lock:job:{jobName}} */
    public static final String LOCK_JOB = PREFIX + "lock:job:";

    // ============================================================
    // 排队与候补
    // ============================================================

    /**
     * 排队队列（ZSet）：{@code lab:reserve:queue:{type}:{resId}:{date}:{slotId}}
     * <p>score = 入队时间戳，member = userId。顺位 = ZRANK + 1，不落库。
     */
    public static final String QUEUE = PREFIX + "queue:";

    /** 排队号当日序号自增：{@code lab:reserve:queue:seq:{resourceNo}:{yyyyMMdd}} */
    public static final String QUEUE_SEQ = PREFIX + "queue:seq:";

    /**
     * 候补确认截止时间（ZSet）：{@code lab:reserve:queue:notice:deadline}
     * <p>score = deadline 时间戳，member = queueId。定时任务扫它来触发超时顺延。
     */
    public static final String QUEUE_NOTICE_DEADLINE = PREFIX + "queue:notice:deadline";

    // ============================================================
    // 资源占用与库存
    // ============================================================

    /** 设备时段剩余库存预占：{@code lab:reserve:stock:{equipmentId}:{date}:{slotId}} */
    public static final String STOCK = PREFIX + "stock:";

    /** 防重复提交：{@code lab:reserve:submit:{userId}:{md5}} */
    public static final String SUBMIT = PREFIX + "submit:";

    // ============================================================
    // 消息与收件箱
    // ============================================================

    /**
     * 收件箱索引（ZSet）：{@code lab:reserve:inbox:{userId}}
     * <p>score = 消息时间戳，member = messageId。<b>不设过期</b>（需求 K3）。
     */
    public static final String INBOX = PREFIX + "inbox:";

    /** 未读消息数：{@code lab:reserve:inbox:unread:{userId}} */
    public static final String INBOX_UNREAD = PREFIX + "inbox:unread:";

    // ============================================================
    // Redis Stream（邮件投递）
    // ============================================================

    /** 邮件投递队列 */
    public static final String STREAM_EMAIL = PREFIX + "stream:msg:email";

    /** 邮件死信队列 */
    public static final String STREAM_EMAIL_DEAD = PREFIX + "stream:msg:email:dead";

    /** 邮件消费组名 */
    public static final String STREAM_EMAIL_GROUP = "mailer";

    /** Stream 最大长度（近似），防止无限增长 */
    public static final long STREAM_EMAIL_MAX_LEN = 10_000L;

    // ============================================================
    // 业务单号自增
    // ============================================================

    /** 预约号自增：{@code lab:reserve:seq:reservation:{yyyyMMdd}} */
    public static final String SEQ_RESERVATION = PREFIX + "seq:reservation:";

    /** 报修工单号自增：{@code lab:reserve:seq:repair:{yyyyMMdd}} */
    public static final String SEQ_REPAIR_ORDER = PREFIX + "seq:repair:";

    // ============================================================
    // 统计缓存
    // ============================================================

    /** 占用率缓存：{@code lab:reserve:stat:occupancy:{yyyyMM}} */
    public static final String STAT_OCCUPANCY = PREFIX + "stat:occupancy:";

    // ============================================================
    // RAG（智能问答）
    // ============================================================

    /** 每用户限速：{@code lab:reserve:rag:rl:user:{userId}} */
    public static final String RAG_RL_USER = PREFIX + "rag:rl:user:";

    /** 全局并发信号量：{@code lab:reserve:rag:sem:global} */
    public static final String RAG_SEM_GLOBAL = PREFIX + "rag:sem:global";

    /** 等待队列（ZSet）{@code lab:reserve:rag:queue:wait}，score = role_priority*1e9 - ts */
    public static final String RAG_QUEUE_WAIT = PREFIX + "rag:queue:wait";

    /** 摄取步骤锁：{@code lab:reserve:rag:step:{docId}:{ver}:{code}} */
    public static final String RAG_STEP_LOCK = PREFIX + "rag:step:";

    // ============================================================
    // Key 构造方法
    // ============================================================

    /** {@code lab:reserve:queue:{type}:{resId}:{date}:{slotId}} */
    public static String queueKey(Integer resourceType, Long resourceId, LocalDate date, Long slotId) {
        return QUEUE + resourceType + ":" + resourceId + ":" + date + ":" + slotId;
    }

    /** {@code lab:reserve:lock:reserve:{type}:{resId}:{date}:{slotId}} */
    public static String lockReserveKey(Integer resourceType, Long resourceId, LocalDate date, Long slotId) {
        return LOCK_RESERVE + resourceType + ":" + resourceId + ":" + date + ":" + slotId;
    }

    /** {@code lab:reserve:lock:queue:{type}:{resId}:{date}:{slotId}} */
    public static String lockQueueKey(Integer resourceType, Long resourceId, LocalDate date, Long slotId) {
        return LOCK_QUEUE + resourceType + ":" + resourceId + ":" + date + ":" + slotId;
    }

    /** {@code lab:reserve:queue:seq:{resourceNo}:{yyyyMMdd}} */
    public static String queueSeqKey(String resourceNo, LocalDate date) {
        return QUEUE_SEQ + resourceNo + ":" + YYYYMMDD.format(date);
    }

    /** {@code lab:reserve:seq:reservation:{yyyyMMdd}} */
    public static String reservationSeqKey(LocalDate date) {
        return SEQ_RESERVATION + YYYYMMDD.format(date);
    }

    /** {@code lab:reserve:seq:repair:{yyyyMMdd}} */
    public static String repairOrderSeqKey(LocalDate date) {
        return SEQ_REPAIR_ORDER + YYYYMMDD.format(date);
    }

    /** {@code lab:reserve:stat:occupancy:{yyyyMM}} */
    public static String statOccupancyKey(LocalDate date) {
        return STAT_OCCUPANCY + YYYYMM.format(date);
    }

    /** {@code lab:reserve:inbox:{userId}} */
    public static String inboxKey(Long userId) {
        return INBOX + userId;
    }

    /** {@code lab:reserve:inbox:unread:{userId}} */
    public static String inboxUnreadKey(Long userId) {
        return INBOX_UNREAD + userId;
    }

    /** {@code lab:reserve:perm:role:{roleId}} */
    public static String permRoleKey(Long roleId) {
        return PERM_ROLE + roleId;
    }

    // ------------------------------------------------------------
    // RAG（迭代 R0 起）
    // ------------------------------------------------------------

    /** {@code lab:reserve:rag:rl:user:{userId}} */
    public static String ragRateLimitKey(Long userId) {
        return RAG_RL_USER + userId;
    }

    /** {@code lab:reserve:rag:step:{docId}:{ver}:{stepCode}} */
    public static String ragStepLockKey(Long documentId, Integer version, String stepCode) {
        return RAG_STEP_LOCK + documentId + ":" + version + ":" + stepCode;
    }
}
