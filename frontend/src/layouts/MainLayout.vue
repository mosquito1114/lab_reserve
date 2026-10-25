<template>
  <el-container class="layout" direction="vertical">
    <!-- ==================== 通栏顶栏（悬浮在内容之上，磨砂） ==================== -->
    <el-header class="layout-header">
      <div class="layout-header-left">
        <div class="layout-brand" @click="router.push('/dashboard')">
          <span class="layout-brand-badge">
            <el-icon :size="17" color="#fff"><School /></el-icon>
          </span>
          <span class="layout-brand-text">实验室预约</span>
        </div>
        <el-divider direction="vertical" class="layout-brand-divider" />
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
          <el-breadcrumb-item v-if="currentTitle && route.path !== '/dashboard'">
            {{ currentTitle }}
          </el-breadcrumb-item>
        </el-breadcrumb>
      </div>

      <div class="layout-header-right">
        <el-tooltip
          :content="messageStore.unread > 0 ? `有 ${messageStore.unread} 条未读消息` : '消息中心'"
          placement="bottom"
        >
          <el-badge
            :value="messageStore.unread"
            :max="99"
            :hidden="messageStore.unread === 0"
            class="layout-badge"
            @click="gotoInbox"
          >
            <el-icon :size="18"><Bell /></el-icon>
          </el-badge>
        </el-tooltip>

        <el-icon class="layout-collapse" @click="collapsed = !collapsed">
          <component :is="collapsed ? 'Expand' : 'Fold'" />
        </el-icon>
      </div>
    </el-header>

    <el-container class="layout-body">
      <!-- ==================== 浮动侧栏（半透明磨砂面板） ==================== -->
      <el-aside
        class="layout-aside"
        :width="collapsed ? 'var(--lr-sidebar-width-collapsed)' : 'var(--lr-sidebar-width)'"
      >
        <el-scrollbar class="layout-menu-scroll">
          <el-menu
            :default-active="activeMenu"
            :collapse="collapsed"
            :collapse-transition="false"
            background-color="transparent"
            text-color="#5c564f"
            active-text-color="#ffffff"
            router
          >
            <!-- 菜单由后端按权限下发（见 MenuService），这里只负责渲染 -->
            <template v-for="menu in userStore.menus" :key="menu.path">
              <el-sub-menu v-if="menu.children?.length" :index="menu.path">
                <template #title>
                  <el-icon><component :is="menu.icon || 'Menu'" /></el-icon>
                  <span>{{ menu.title }}</span>
                </template>
                <el-menu-item v-for="child in menu.children" :key="child.path" :index="child.path">
                  <el-icon><component :is="child.icon || 'Menu'" /></el-icon>
                  <template #title>{{ child.title }}</template>
                </el-menu-item>
              </el-sub-menu>
              <el-menu-item v-else :index="menu.path">
                <el-icon><component :is="menu.icon || 'Menu'" /></el-icon>
                <template #title>{{ menu.title }}</template>
              </el-menu-item>
            </template>
          </el-menu>
        </el-scrollbar>

        <!-- ---------- 底部用户块（向上弹出） ---------- -->
        <div class="layout-user-card">
          <el-dropdown trigger="click" placement="top-start" @command="handleCommand">
            <div class="layout-user" :class="{ 'is-collapsed': collapsed }">
              <el-avatar :size="32" class="layout-avatar">{{ avatarText }}</el-avatar>
              <div v-show="!collapsed" class="layout-user-meta">
                <span class="layout-user-name">{{ displayName }}</span>
                <span class="layout-user-role">{{ roleLabel }}</span>
              </div>
              <el-icon v-show="!collapsed" class="layout-user-caret"><ArrowUp /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>{{ roleLabel }}</el-dropdown-item>
                <el-dropdown-item command="password">
                  <el-icon><Key /></el-icon>
                  修改密码
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-aside>

      <!-- ==================== 内容区 ==================== -->
      <el-main class="layout-main" :class="{ 'is-full-height': isFullHeight }">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>

    <!-- ==================== 修改密码 ==================== -->
    <el-dialog v-model="passwordVisible" title="修改密码" width="420px">
      <el-alert
        class="mb-16"
        type="warning"
        :closable="false"
        title="修改成功后当前登录会立即失效，需要用新密码重新登录"
        show-icon
      />
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="88px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="6~64 位" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordSaving" @click="handlePasswordSubmit">确定</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { updatePassword } from '@/api/auth'
import { useMessageStore } from '@/stores/message'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const messageStore = useMessageStore()

/** 未读角标轮询（30s 一次，页面隐藏时不打扰后端） */
let unreadTimer: number | undefined

function gotoInbox(): void {
  void router.push('/message/inbox')
}

const collapsed = ref(false)

const activeMenu = computed(() => route.path)

/**
 * 全屏式页面（如知识库问答）：
 * 路由 meta.fullHeight = true 时，主区域禁用自己的外层滚动条，
 * 由页面内部（聊天区）自己管理滚动，避免出现双层滚动条。
 */
const isFullHeight = computed(() => route.meta.fullHeight === true)
const currentTitle = computed(() => (route.meta.title as string | undefined) ?? '')

const displayName = computed(() => userStore.realName || '未登录')
const roleLabel = computed(() => userStore.roleName || '游客')
const avatarText = computed(() => (userStore.realName ? userStore.realName.charAt(0) : '游'))

// ---------------- 修改密码 ----------------

interface PasswordForm {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

const passwordVisible = ref(false)
const passwordSaving = ref(false)
const passwordFormRef = ref<FormInstance>()
const passwordForm = reactive<PasswordForm>({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const passwordRules: FormRules<PasswordForm> = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度需在 6~64 位之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

function openPasswordDialog(): void {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordVisible.value = true
}

async function handlePasswordSubmit(): Promise<void> {
  if (!passwordFormRef.value) {
    return
  }
  const valid = await passwordFormRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  passwordSaving.value = true
  try {
    await updatePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    ElMessage.success('密码修改成功，请用新密码重新登录')
    passwordVisible.value = false
    userStore.reset()
    await router.replace('/login')
  } finally {
    passwordSaving.value = false
  }
}

// ---------------- 用户块下拉 ----------------

async function handleCommand(command: string): Promise<void> {
  if (command === 'password') {
    openPasswordDialog()
    return
  }
  if (command !== 'logout') {
    return
  }
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await userStore.logout()
  messageStore.reset()
  await router.replace('/login')
}

// 刷新页面后 token 还在但用户信息为空，这里补一次请求（菜单与权限依赖它）
onMounted(async () => {
  if (userStore.isLogin && (!userStore.userInfo || userStore.menus.length === 0)) {
    try {
      await userStore.fetchInfo()
    } catch {
      await router.replace('/login')
      return
    }
  }
  // 未读角标：进页面拉一次，之后每 30s 轮询（没有消息权限的角色接口会 403，静默忽略）
  if (userStore.hasPermission('message:list')) {
    void messageStore.fetchUnread()
    unreadTimer = window.setInterval(() => {
      if (!document.hidden) {
        void messageStore.fetchUnread()
      }
    }, 30_000)
  }
})

onBeforeUnmount(() => {
  if (unreadTimer) {
    window.clearInterval(unreadTimer)
    unreadTimer = undefined
  }
})
</script>

<style scoped lang="scss">
/* 顶栏悬浮高度 + 内容上边距，两处必须一致 */
$header-overlap: calc(var(--lr-header-height) + var(--lr-sidebar-gap));

.layout {
  position: relative;
  height: 100%;
  background-color: transparent;
}

/* ==================== 通栏顶栏 ==================== */
/* 绝对定位 + 磨砂：内容滚动时从顶栏下方模糊穿过 */
.layout-header {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--lr-header-height);
  padding: 0 20px;
  background-color: var(--lr-material-thin);
  box-shadow: var(--lr-shadow-inset-hairline);
  -webkit-backdrop-filter: saturate(var(--lr-material-thin-saturate)) blur(var(--lr-material-thin-blur));
  backdrop-filter: saturate(var(--lr-material-thin-saturate)) blur(var(--lr-material-thin-blur));
}

.layout-header-left {
  display: flex;
  gap: 14px;
  align-items: center;
  min-width: 0;
}

.layout-brand {
  display: flex;
  flex-shrink: 0;
  gap: 10px;
  align-items: center;
  cursor: pointer;
  user-select: none;
}

.layout-brand-badge {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  background: var(--lr-gradient-primary);
  border-radius: var(--lr-radius-control);
  box-shadow: var(--lr-shadow-primary);
}

.layout-brand-text {
  font-size: var(--lr-font-lg);
  font-weight: 600;
  color: var(--lr-text-primary);
  letter-spacing: -0.02em;
  white-space: nowrap;
}

.layout-brand-divider {
  height: 18px;
  margin: 0;
  border-left-color: var(--lr-border);
}

.layout-header-right {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
  align-items: center;
}

.layout-badge,
.layout-collapse {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  color: var(--lr-text-regular);
  cursor: pointer;
  border-radius: var(--lr-radius-control);
  transition: background-color var(--lr-duration) var(--lr-ease),
    color var(--lr-duration) var(--lr-ease);

  &:hover {
    color: var(--lr-primary);
    background-color: var(--lr-bg-hover);
  }
}

.layout-collapse {
  font-size: 18px;
}

/* ==================== 主体 ==================== */
/* 左上是给悬浮顶栏让出的空间；内容区从视口顶部开始滚动，才能穿过顶栏 */
.layout-body {
  min-height: 0;
  overflow: hidden;
}

/* ---------- 浮动侧栏 ---------- */
.layout-aside {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  margin: $header-overlap var(--lr-space-4) var(--lr-sidebar-gap) var(--lr-page-gutter);
  overflow: hidden;
  background-color: var(--lr-material-regular);
  border: 1px solid var(--lr-hairline);
  border-radius: var(--lr-radius-lg);
  box-shadow: var(--lr-shadow-sm);
  -webkit-backdrop-filter: saturate(var(--lr-material-regular-saturate))
    blur(var(--lr-material-regular-blur));
  backdrop-filter: saturate(var(--lr-material-regular-saturate))
    blur(var(--lr-material-regular-blur));
  transition: width var(--lr-duration-slow) var(--lr-ease);
}

.layout-menu-scroll {
  flex: 1;
  min-height: 0;
}

/* ---------- 菜单 ---------- */
:deep(.el-menu) {
  padding: 10px;
  background: transparent;
  border-right: none;
}

:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  height: 48px;
  margin-bottom: var(--lr-space-1);
  font-size: var(--lr-font);
  color: var(--lr-text-secondary);
  border-radius: var(--lr-radius-md);
  transition: background-color var(--lr-duration) var(--lr-ease),
    color var(--lr-duration) var(--lr-ease),
    box-shadow var(--lr-duration) var(--lr-ease);

  &:hover {
    color: var(--lr-primary);
    background-color: var(--lr-primary-soft);
  }
}

/* 分组标题做成小号标签 */
:deep(.el-sub-menu__title) {
  font-size: var(--lr-font-sm);
  font-weight: 500;
  letter-spacing: 0.02em;
}

:deep(.el-sub-menu .el-menu-item) {
  height: 44px;
  padding-left: 44px !important;
  font-size: var(--lr-font);
}

/* 选中项：整块填充赭色 + 白字 + 暖阴影 */
:deep(.el-menu-item.is-active) {
  font-weight: 500;
  color: #fff !important;
  background-color: var(--lr-primary);
  box-shadow: var(--lr-shadow-primary);
}

:deep(.el-menu--inline) {
  background: transparent;
}

/* 折叠态 */
:deep(.el-menu--collapse) {
  width: 100%;
  padding: 10px 8px;
}

:deep(.el-menu--collapse .el-menu-item),
:deep(.el-menu--collapse .el-sub-menu__title) {
  justify-content: center;
  height: 44px;
  margin-bottom: var(--lr-space-1);
  padding: 0 !important;
}

/* ---------- 底部用户块 ---------- */
.layout-user-card {
  flex-shrink: 0;
  padding: 10px;
  border-top: 1px solid var(--lr-hairline);
}

.layout-user {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 8px 10px;
  cursor: pointer;
  border-radius: var(--lr-radius-md);
  outline: none;
  transition: background-color var(--lr-duration) var(--lr-ease);

  &:hover {
    background-color: var(--lr-bg-hover);
  }

  &.is-collapsed {
    justify-content: center;
    padding: 6px;
  }
}

.layout-avatar {
  flex-shrink: 0;
  font-size: var(--lr-font-sm);
  font-weight: 500;
  background: var(--lr-gradient-primary);
}

.layout-user-meta {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  line-height: 1.35;
}

.layout-user-name {
  overflow: hidden;
  font-size: var(--lr-font-sm);
  font-weight: 600;
  color: var(--lr-text-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.layout-user-role {
  overflow: hidden;
  font-size: var(--lr-font-xs);
  color: var(--lr-text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.layout-user-caret {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--lr-text-placeholder);
}

/* ==================== 内容区 ==================== */
/* padding-top 让出悬浮顶栏；背景透明，露出 body 上的页面光晕。
   注意：滚动容器的 padding 属于滚动可视区，所以内容向上滚动时
   会进入这段 padding 区域、从磨砂顶栏下方穿过。 */
.layout-main {
  padding: var(--lr-header-height) 0 0;
  overflow-y: auto;
  background-color: transparent;

  /* 全屏式页面（聊天）：外层不滚动，交给页面自己管 */
  &.is-full-height {
    padding-top: var(--lr-header-height);
    overflow: hidden;
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* ==================== 磨砂降级 ==================== */
@supports not (backdrop-filter: blur(1px)) {
  .layout-header,
  .layout-aside {
    background-color: var(--lr-material-fallback);
    -webkit-backdrop-filter: none;
    backdrop-filter: none;
  }
}
</style>
