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
          <el-menu-item index="/dashboard">
            <el-icon><HomeFilled /></el-icon>
            <template #title>首页</template>
          </el-menu-item>
          <!-- 后续迭代按模块追加菜单项，并按权限过滤 -->
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
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const collapsed = ref(false)

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => (route.meta.title as string | undefined) ?? '')

const displayName = computed(() => userStore.realName || '未登录')
const roleLabel = computed(() => userStore.roleName || '游客')
const avatarText = computed(() => (userStore.realName ? userStore.realName.charAt(0) : '游'))

async function handleCommand(command: string): Promise<void> {
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
