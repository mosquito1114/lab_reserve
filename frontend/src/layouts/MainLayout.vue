<template>
  <el-container class="layout">
    <!-- ---------- 侧边栏 ---------- -->
    <el-aside class="layout-aside" :width="collapsed ? 'var(--lr-sidebar-width-collapsed)' : 'var(--lr-sidebar-width)'">
      <div class="layout-logo">
        <el-icon :size="22" color="#fff"><School /></el-icon>
        <span v-show="!collapsed" class="layout-logo-text">实验室预约</span>
      </div>

      <el-scrollbar>
        <el-menu
          :default-active="activeMenu"
          :collapse="collapsed"
          :collapse-transition="false"
          background-color="#20293f"
          text-color="#c3cbdc"
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
    </el-aside>

    <el-container>
      <!-- ---------- 顶栏 ---------- -->
      <el-header class="layout-header">
        <div class="layout-header-left">
          <el-icon class="layout-collapse" @click="collapsed = !collapsed">
            <component :is="collapsed ? 'Expand' : 'Fold'" />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentTitle">{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="layout-header-right">
          <el-tooltip content="消息中心（迭代 3 开放）" placement="bottom">
            <el-badge :value="0" :hidden="true" class="layout-badge">
              <el-icon :size="18"><Bell /></el-icon>
            </el-badge>
          </el-tooltip>

          <el-dropdown trigger="click" @command="handleCommand">
            <span class="layout-user">
              <el-avatar :size="28" class="layout-avatar">{{ avatarText }}</el-avatar>
              <span class="layout-user-name">{{ displayName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
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
      </el-header>

      <!-- ---------- 内容区 ---------- -->
      <el-main class="layout-main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>

  <!-- ---------- 修改密码 ---------- -->
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
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { updatePassword } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const collapsed = ref(false)

const activeMenu = computed(() => route.path)
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

// ---------------- 顶部下拉 ----------------

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
  await router.replace('/login')
}

// 刷新页面后 token 还在但用户信息为空，这里补一次请求（菜单与权限依赖它）
onMounted(async () => {
  if (userStore.isLogin && !userStore.userInfo) {
    try {
      await userStore.fetchInfo()
    } catch {
      await router.replace('/login')
    }
  }
})
</script>

<style scoped lang="scss">
.layout {
  height: 100%;
}

/* ---------- 侧边栏 ---------- */
.layout-aside {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background-color: #20293f;
  transition: width 0.2s;
}

.layout-logo {
  display: flex;
  gap: 8px;
  align-items: center;
  height: var(--lr-header-height);
  padding-left: 20px;
  overflow: hidden;
  white-space: nowrap;
}

.layout-logo-text {
  font-size: 16px;
  font-weight: 600;
  color: #fff;
}

:deep(.el-menu) {
  border-right: none;
}

/* ---------- 顶栏 ---------- */
.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--lr-header-height);
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}

.layout-header-left {
  display: flex;
  gap: 16px;
  align-items: center;
}

.layout-collapse {
  font-size: 18px;
  color: var(--lr-text-regular);
  cursor: pointer;

  &:hover {
    color: var(--lr-primary);
  }
}

.layout-header-right {
  display: flex;
  gap: 20px;
  align-items: center;
}

.layout-badge {
  display: flex;
  align-items: center;
  color: var(--lr-text-regular);
  cursor: pointer;

  &:hover {
    color: var(--lr-primary);
  }
}

.layout-user {
  display: flex;
  gap: 8px;
  align-items: center;
  color: var(--lr-text-regular);
  cursor: pointer;
  outline: none;
}

.layout-avatar {
  background-color: var(--lr-primary);
  font-size: 13px;
}

.layout-user-name {
  font-size: 14px;
}

/* ---------- 内容区 ---------- */
.layout-main {
  padding: 0;
  overflow-y: auto;
  background-color: var(--lr-bg-page);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
