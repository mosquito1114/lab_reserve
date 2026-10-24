package com.labreserve.security;

import com.labreserve.common.constant.PermissionConst;
import com.labreserve.model.vo.MenuItemVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 侧边栏菜单构建。
 *
 * <p>菜单是「按权限过滤的路由表」：每个叶子菜单声明它需要的权限码，
 * 服务端根据当前角色拥有的权限码过滤后下发，前端只渲染不判断。
 *
 * <p>这样做的好处：新模块上线时前端文档不用改权限表，
 * 菜单可见性与接口权限天然一致（都用 {@code tb_role_permission} 这一份数据）。
 *
 * <p>⚠️ 迭代 4/5/6/7/8 的业务页面（资源、预约、排队、运维、消息、统计、用户管理）
 * 在各自迭代里往 {@link #GROUPS} 追加即可。
 */
@Service
public class MenuService {

    /** 叶子菜单：路径 + 标题 + 图标 + 需要的权限码 */
    private record MenuDef(String path, String title, String icon, String permission) {
    }

    /** 分组菜单：标题 + 图标 + 子菜单 */
    private record GroupDef(String path, String title, String icon, List<MenuDef> children) {
    }

    /** 总是可见的菜单（不依赖权限）：首页 + 个人中心（所有角色都能改自己的联系方式） */
    private static final List<MenuDef> ALWAYS = List.of(
            new MenuDef("/dashboard", "首页", "HomeFilled", null),
            new MenuDef("/profile", "个人中心", "User", null)
    );

    /** 需要权限的分组菜单（迭代 2 系统管理 → 4 资源管理 → 3 消息通知 → 5 预约管理 → 6 排队候补 → 7 设备运维 → R5 智能问答） */
    private static final List<GroupDef> GROUPS = List.of(
            new GroupDef("/system", "系统管理", "Setting", List.of(
                    new MenuDef("/system/user", "用户管理", "UserFilled", PermissionConst.USER_LIST),
                    new MenuDef("/system/class", "班级管理", "School", PermissionConst.CLASS_MANAGE),
                    new MenuDef("/system/role", "角色权限", "Lock", PermissionConst.SYSTEM_ROLE_LIST),
                    new MenuDef("/system/operation-log", "操作日志", "Document", PermissionConst.SYSTEM_LOG_LIST)
            )),
            new GroupDef("/resource", "资源管理", "OfficeBuilding", List.of(
                    new MenuDef("/resource/laboratory", "实验室管理", "House", PermissionConst.RESOURCE_LAB_LIST),
                    new MenuDef("/resource/equipment", "设备管理", "Cpu", PermissionConst.RESOURCE_EQUIP_LIST),
                    new MenuDef("/resource/dict", "字典维护", "Collection", PermissionConst.DICT_MANAGE),
                    new MenuDef("/resource/log", "资源更新日志", "Tickets", PermissionConst.RESOURCE_LOG_LIST),
                    new MenuDef("/resource/availability", "资源可用性", "Calendar", PermissionConst.RESOURCE_LAB_LIST)
            )),
            new GroupDef("/reservation", "预约管理", "Calendar", List.of(
                    new MenuDef("/reservation/apply", "预约申请", "EditPen", PermissionConst.RESERVATION_EQUIPMENT_CREATE),
                    new MenuDef("/reservation/my", "我的预约", "List", PermissionConst.RESERVATION_LIST),
                    new MenuDef("/reservation/timetable", "我的实验课表", "Notebook", PermissionConst.RESERVATION_TIMETABLE),
                    new MenuDef("/reservation/audit", "预约审核", "Checked", PermissionConst.RESERVATION_AUDIT),
                    new MenuDef("/reservation/change-audit", "调整审批", "RefreshRight", PermissionConst.CHANGE_AUDIT)
            )),
            new GroupDef("/repair", "设备运维", "Tools", List.of(
                    new MenuDef("/repair/apply", "我要报修", "Tools", PermissionConst.REPAIR_CREATE),
                    new MenuDef("/repair/my", "我的报修", "Tickets", null),
                    new MenuDef("/repair/order", "工单管理", "DocumentChecked", PermissionConst.REPAIR_LIST),
                    new MenuDef("/repair/task", "我的维修任务", "SetUp", PermissionConst.REPAIR_LIST_ASSIGNED)
            )),
            new GroupDef("/queue", "排队候补", "Timer", List.of(
                    new MenuDef("/queue/my", "我的排队", "Timer", PermissionConst.QUEUE_CREATE),
                    new MenuDef("/queue/manage", "排队管理", "Sort", PermissionConst.QUEUE_LIST_ALL),
                    new MenuDef("/queue/ban", "我的禁入记录", "Warning", PermissionConst.QUEUE_CREATE)
            )),
            new GroupDef("/message", "消息通知", "Bell", List.of(
                    new MenuDef("/message/inbox", "消息中心", "ChatDotRound", PermissionConst.MESSAGE_LIST),
                    new MenuDef("/message/delivery-log", "邮件投递记录", "Promotion", PermissionConst.MESSAGE_DELIVERY_LOG)
            )),
            // RAG 智能问答（R5）：问答全员可见（rag:chat:query），
            // 知识库管理仅 LAB_ADMIN / SYS_ADMIN（rag:doc:list）
            new GroupDef("/rag", "智能问答", "MagicStick", List.of(
                    new MenuDef("/rag/chat", "知识库问答", "ChatLineSquare", PermissionConst.RAG_CHAT_QUERY),
                    new MenuDef("/rag/documents", "知识库管理", "Files", PermissionConst.RAG_DOC_LIST)
            ))
    );

    /**
     * 构建菜单。
     *
     * @param codes 当前用户拥有的权限码
     * @return 过滤后的菜单树（空分组会被丢弃）
     */
    public List<MenuItemVO> build(Set<String> codes) {
        List<MenuItemVO> menus = new ArrayList<>();

        for (MenuDef def : ALWAYS) {
            menus.add(new MenuItemVO(def.path(), def.title(), def.icon(), null));
        }

        for (GroupDef group : GROUPS) {
            List<MenuItemVO> children = new ArrayList<>();
            for (MenuDef child : group.children()) {
                if (child.permission() == null || codes.contains(child.permission())) {
                    children.add(new MenuItemVO(child.path(), child.title(), child.icon(), null));
                }
            }
            if (!children.isEmpty()) {
                menus.add(new MenuItemVO(group.path(), group.title(), group.icon(), children));
            }
        }

        return menus;
    }
}
