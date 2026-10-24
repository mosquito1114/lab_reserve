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
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/ProfileView.vue'),
        meta: { title: '个人中心', icon: 'User' }
      },
      {
        path: 'system/user',
        name: 'SystemUser',
        component: () => import('@/views/system/UserView.vue'),
        meta: { title: '用户管理', icon: 'UserFilled', permission: 'user:list' }
      },
      {
        path: 'system/class',
        name: 'SystemClass',
        component: () => import('@/views/system/ClassView.vue'),
        meta: { title: '班级管理', icon: 'School', permission: 'class:manage' }
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
      },
      {
        path: 'reservation/apply',
        name: 'ReservationApply',
        component: () => import('@/views/reservation/ApplyView.vue'),
        meta: { title: '预约申请', icon: 'EditPen', permission: 'reservation:equipment:create' }
      },
      {
        path: 'reservation/my',
        name: 'ReservationMy',
        component: () => import('@/views/reservation/MyReservationView.vue'),
        meta: { title: '我的预约', icon: 'List', permission: 'reservation:list' }
      },
      {
        path: 'reservation/timetable',
        name: 'ReservationTimetable',
        component: () => import('@/views/reservation/TimetableView.vue'),
        meta: { title: '我的实验课表', icon: 'Notebook', permission: 'reservation:timetable' }
      },
      {
        path: 'reservation/audit',
        name: 'ReservationAudit',
        component: () => import('@/views/reservation/AuditView.vue'),
        meta: { title: '预约审核', icon: 'Checked', permission: 'reservation:audit' }
      },
      {
        path: 'reservation/change-audit',
        name: 'ReservationChangeAudit',
        component: () => import('@/views/reservation/ChangeAuditView.vue'),
        meta: { title: '调整审批', icon: 'RefreshRight', permission: 'change:audit' }
      },
      {
        path: 'queue/my',
        name: 'QueueMy',
        component: () => import('@/views/queue/MyQueueView.vue'),
        meta: { title: '我的排队', icon: 'Timer', permission: 'queue:create' }
      },
      {
        path: 'queue/manage',
        name: 'QueueManage',
        component: () => import('@/views/queue/QueueManageView.vue'),
        meta: { title: '排队管理', icon: 'Sort', permission: 'queue:list-all' }
      },
      {
        path: 'queue/ban',
        name: 'QueueBan',
        component: () => import('@/views/queue/QueueBanView.vue'),
        meta: { title: '我的禁入记录', icon: 'Warning', permission: 'queue:create' }
      },
      {
        path: 'resource/laboratory',
        name: 'ResourceLaboratory',
        component: () => import('@/views/resource/LaboratoryView.vue'),
        meta: { title: '实验室管理', icon: 'House', permission: 'resource:lab:list' }
      },
      {
        path: 'resource/equipment',
        name: 'ResourceEquipment',
        component: () => import('@/views/resource/EquipmentView.vue'),
        meta: { title: '设备管理', icon: 'Cpu', permission: 'resource:equip:list' }
      },
      {
        path: 'resource/dict',
        name: 'ResourceDict',
        component: () => import('@/views/resource/DictView.vue'),
        meta: { title: '字典维护', icon: 'Collection', permission: 'dict:manage' }
      },
      {
        path: 'resource/log',
        name: 'ResourceLog',
        component: () => import('@/views/resource/ResourceLogView.vue'),
        meta: { title: '资源更新日志', icon: 'Tickets', permission: 'resource:log:list' }
      },
      {
        path: 'resource/availability',
        name: 'ResourceAvailability',
        component: () => import('@/views/resource/AvailabilityView.vue'),
        meta: { title: '资源可用性', icon: 'Calendar', permission: 'resource:lab:list' }
      },
      {
        path: 'message/inbox',
        name: 'MessageInbox',
        component: () => import('@/views/message/InboxView.vue'),
        meta: { title: '消息中心', icon: 'ChatDotRound', permission: 'message:list' }
      },
      {
        path: 'message/delivery-log',
        name: 'MessageDeliveryLog',
        component: () => import('@/views/message/DeliveryLogView.vue'),
        meta: { title: '邮件投递记录', icon: 'Promotion', permission: 'message:delivery-log' }
      },
      // RAG 智能问答（R5）
      {
        path: 'rag/chat',
        name: 'RagChat',
        component: () => import('@/views/rag/ChatView.vue'),
        // 全屏式布局：去掉页面内边距，聊天区自己管滚动
        meta: { title: '知识库问答', icon: 'ChatLineSquare', permission: 'rag:chat:query', fullHeight: true }
      },
      {
        path: 'rag/documents',
        name: 'RagDocuments',
        component: () => import('@/views/rag/DocumentView.vue'),
        meta: { title: '知识库管理', icon: 'Files', permission: 'rag:doc:list' }
      }
      // ↓↓↓ 后续迭代在此按模块追加子路由 ↓↓↓
      // 设备运维：/repair/apply  /repair/my  /repair/order  /repair/task
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
  // 菜单也是 get_info 下发的：menus 为空时同样要补拉（否则侧边栏空着）
  if (!userStore.userInfo || userStore.menus.length === 0) {
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
