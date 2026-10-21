package com.labreserve.common.constant;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 权限码与权限模块常量。
 *
 * <p>取值必须与 {@code tb_permission.code} 完全一致（种子脚本 {@code seed_data.sql} 灌入）。
 * 所有 {@code @RequirePerm} 注解都引用这里的常量，避免手写字符串写错。
 *
 * <p>命名规范：{@code 模块:资源:动作}，例如 {@code reservation:lab:create}。
 */
public final class PermissionConst {

    private PermissionConst() {
    }

    // ------------------------------------------------------------
    // 用户
    // ------------------------------------------------------------
    public static final String USER_LIST = "user:list";
    public static final String USER_VIEW = "user:view";
    public static final String USER_CREATE = "user:create";
    public static final String USER_UPDATE = "user:update";
    public static final String USER_DISABLE = "user:disable";
    public static final String USER_VIEW_PHONE = "user:view-phone";

    // ------------------------------------------------------------
    // 资源
    // ------------------------------------------------------------
    public static final String RESOURCE_LAB_LIST = "resource:lab:list";
    public static final String RESOURCE_LAB_CREATE = "resource:lab:create";
    public static final String RESOURCE_LAB_UPDATE = "resource:lab:update";
    public static final String RESOURCE_LAB_DISABLE = "resource:lab:disable";
    public static final String RESOURCE_EQUIP_LIST = "resource:equip:list";
    public static final String RESOURCE_EQUIP_CREATE = "resource:equip:create";
    public static final String RESOURCE_EQUIP_UPDATE = "resource:equip:update";
    public static final String RESOURCE_EQUIP_DISABLE = "resource:equip:disable";
    public static final String RESOURCE_LOG_LIST = "resource:log:list";

    // ------------------------------------------------------------
    // 字典 / 班级
    // ------------------------------------------------------------
    public static final String DICT_LIST = "dict:list";
    public static final String DICT_MANAGE = "dict:manage";
    public static final String CLASS_LIST = "class:list";
    public static final String CLASS_MANAGE = "class:manage";

    // ------------------------------------------------------------
    // 预约（实验室与设备分开授权 —— req.md §10 变更 1）
    // ------------------------------------------------------------
    /** 提交实验室预约（仅教师） */
    public static final String RESERVATION_LAB_CREATE = "reservation:lab:create";
    /** 提交设备预约（师生都可以） */
    public static final String RESERVATION_EQUIPMENT_CREATE = "reservation:equipment:create";
    /** 查看「我的实验课表」（学生） */
    public static final String RESERVATION_TIMETABLE = "reservation:timetable";
    public static final String RESERVATION_LIST = "reservation:list";
    public static final String RESERVATION_LIST_ALL = "reservation:list-all";
    public static final String RESERVATION_VIEW = "reservation:view";
    public static final String RESERVATION_AUDIT = "reservation:audit";
    public static final String RESERVATION_CANCEL = "reservation:cancel";
    public static final String RESERVATION_RELEASE = "reservation:release";

    // ------------------------------------------------------------
    // 调整申请
    // ------------------------------------------------------------
    public static final String CHANGE_CREATE = "change:create";
    public static final String CHANGE_LIST = "change:list";
    public static final String CHANGE_AUDIT = "change:audit";

    // ------------------------------------------------------------
    // 排队
    // ------------------------------------------------------------
    public static final String QUEUE_CREATE = "queue:create";
    public static final String QUEUE_LIST_ALL = "queue:list-all";
    public static final String QUEUE_CANCEL = "queue:cancel";
    public static final String QUEUE_CONFIRM = "queue:confirm";

    // ------------------------------------------------------------
    // 运维
    // ------------------------------------------------------------
    public static final String REPAIR_CREATE = "repair:create";
    public static final String REPAIR_LIST = "repair:list";
    public static final String REPAIR_LIST_ASSIGNED = "repair:list-assigned";
    public static final String REPAIR_REVIEW = "repair:review";
    public static final String REPAIR_ASSIGN = "repair:assign";
    public static final String REPAIR_ACCEPT = "repair:accept";
    public static final String REPAIR_PROGRESS = "repair:progress";

    // ------------------------------------------------------------
    // 消息 / 统计
    // ------------------------------------------------------------
    public static final String MESSAGE_LIST = "message:list";
    public static final String MESSAGE_DELIVERY_LOG = "message:delivery-log";
    public static final String STAT_VIEW = "stat:view";
    public static final String STAT_EXPORT = "stat:export";

    // ------------------------------------------------------------
    // 系统
    // ------------------------------------------------------------
    public static final String SYSTEM_ROLE_LIST = "system:role:list";
    public static final String SYSTEM_ROLE_UPDATE = "system:role:update";
    public static final String SYSTEM_PERMISSION_LIST = "system:permission:list";
    public static final String SYSTEM_LOG_LIST = "system:log:list";
    public static final String SYSTEM_LOG_EXPORT = "system:log:export";

    /** 查看系统运行状态（`/api/system/monitor`，DB / Redis / 运行时长；仅系统管理员） */
    public static final String SYSTEM_MONITOR = "system:monitor:view";

    // ------------------------------------------------------------
    // RAG（智能问答）—— R0 骨架阶段上线
    // ------------------------------------------------------------
    /** 发起 RAG 提问（POST /api/rag/chat/send）—— 5 角色全开 */
    public static final String RAG_CHAT_QUERY = "rag:chat:query";
    /** 查看自己的会话与消息历史 */
    public static final String RAG_CHAT_HISTORY = "rag:chat:history";
    /** 上传知识库文档 */
    public static final String RAG_DOC_UPLOAD = "rag:doc:upload";
    /** 文档列表 / 详情 / 元数据 */
    public static final String RAG_DOC_LIST = "rag:doc:list";
    /** 重建文档摄取链路 / 修改切片策略 */
    public static final String RAG_DOC_REINDEX = "rag:doc:reindex";
    /** 停用 / 启用 / 删除文档 */
    public static final String RAG_DOC_DELETE = "rag:doc:delete";

    /** 防锁死：任何情况下都不允许从 {@code SYS_ADMIN} 移除这个权限 */
    public static final String CRITICAL_PERMISSION = SYSTEM_ROLE_UPDATE;

    /**
     * 权限模块编码 → 中文名，用于权限勾选树的分组标题。
     *
     * <p>顺序即树里的展示顺序。
     */
    public static final Map<String, String> MODULE_NAMES = new LinkedHashMap<>();

    static {
        MODULE_NAMES.put("user", "用户管理");
        MODULE_NAMES.put("resource", "资源管理");
        MODULE_NAMES.put("dict", "字典管理");
        MODULE_NAMES.put("class", "班级管理");
        MODULE_NAMES.put("reservation", "预约管理");
        MODULE_NAMES.put("change", "预约调整");
        MODULE_NAMES.put("queue", "排队候补");
        MODULE_NAMES.put("repair", "设备运维");
        MODULE_NAMES.put("message", "消息通知");
        MODULE_NAMES.put("statistics", "数据统计");
        MODULE_NAMES.put("system", "系统管理");
    }

    /** 取模块中文名，未登记时回退为编码本身 */
    public static String moduleName(String moduleCode) {
        return MODULE_NAMES.getOrDefault(moduleCode, moduleCode);
    }
}
