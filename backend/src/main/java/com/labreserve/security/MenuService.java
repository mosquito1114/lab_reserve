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

    /** 需要权限的分组菜单（迭代 2 起：用户管理 / 班级管理 / 角色权限 / 操作日志） */
    private static final List<GroupDef> GROUPS = List.of(
            new GroupDef("/system", "系统管理", "Setting", List.of(
                    new MenuDef("/system/user", "用户管理", "UserFilled", PermissionConst.USER_LIST),
                    new MenuDef("/system/class", "班级管理", "School", PermissionConst.CLASS_MANAGE),
                    new MenuDef("/system/role", "角色权限", "Lock", PermissionConst.SYSTEM_ROLE_LIST),
                    new MenuDef("/system/operation-log", "操作日志", "Document", PermissionConst.SYSTEM_LOG_LIST)
            )),
            new GroupDef("/message", "消息通知", "Bell", List.of(
                    new MenuDef("/message/inbox", "消息中心", "ChatDotRound", PermissionConst.MESSAGE_LIST),
                    new MenuDef("/message/delivery-log", "邮件投递记录", "Promotion", PermissionConst.MESSAGE_DELIVERY_LOG)
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
