import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { getToken } from '@/utils/request'
import { useUserStore } from '@/stores/user'

const APP_TITLE = import.meta.env.VITE_APP_TITLE || '实验室预约管理系统'

/**
 * 路由表。
 *
 * 约定：
 *   - meta.public = true 表示免登录
 *   - meta.title 会写入 document.title 与页面标题栏
 *   - meta.permission 表示进入该页面需要的权限码（迭代 1 接入权限校验）
 *
 * 各业务模块的页面会随迭代逐步补上（预约 / 资源 / 排队 / 运维 / 消息 / 统计 / 系统）。
 */
export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页', icon: 'HomeFilled' }
      },
      {
        path: 'system/role',
        name: 'SystemRole',
        component: () => import('@/views/system/RoleView.vue'),
        meta: { title: '角色权限', icon: 'Lock', permission: 'system:role:list' }
      },
      {
        path: 'system/operation-log',
        name: 'SystemOperationLog',
        component: () => import('@/views/system/OperationLogView.vue'),
        meta: { title: '操作日志', icon: 'Document', permission: 'system:log:list' }
      }
      // ↓↓↓ 后续迭代在此按模块追加子路由 ↓↓↓
      // 预约管理：/reservation/apply  /reservation/my  /reservation/audit  /reservation/change-audit
      // 资源管理：/resource/laboratory  /resource/equipment  /resource/update-log
      // 排队候补：/queue/my  /queue/ban
      // 设备运维：/repair/apply  /repair/my  /repair/order  /repair/task
      // 消息通知：/message
      // 数据统计：/statistics
      // 系统管理：/system/user  /system/role  /system/operation-log
    ]
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/Forbidden.vue'),
    meta: { title: '无权限', public: true }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/NotFound.vue'),
    meta: { title: '页面不存在', public: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach(async (to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · ${APP_TITLE}` : APP_TITLE

  // 免登录页面直接放行
  if (to.meta.public) {
    // 已登录用户访问登录页 → 回首页
    if (to.path === '/login' && getToken()) {
      return { path: '/dashboard' }
    }
    return true
  }

  // 未登录 → 跳登录页，并记住来源
  if (!getToken()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 已登录但用户信息还没加载（刷新页面）→ 先拉一次，拿到权限码才能判断路由权限
  const userStore = useUserStore()
  if (!userStore.userInfo) {
    try {
      await userStore.fetchInfo()
    } catch {
      // token 失效 / 账号被注销：清本地状态回登录页
      userStore.reset()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }

  // 页面级权限：meta.permission 没通过 → 403
  const permission = to.meta.permission as string | undefined
  if (permission && !userStore.hasPermission(permission)) {
    return { path: '/403' }
  }

  return true
})

export default router
