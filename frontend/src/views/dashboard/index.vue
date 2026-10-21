<template>
  <div class="page-container dashboard">
    <el-alert
      class="mb-16"
      type="success"
      :closable="false"
      :title="`欢迎回来，${userStore.realName}（${userStore.roleName}）`"
      :description="`当前角色拥有 ${userStore.permissions.length} 项权限，左侧菜单按权限展示。`"
      show-icon
    />

    <el-row :gutter="16">
      <!-- ---------- 系统运行状态：只有系统管理员可见 ---------- -->
      <el-col v-if="canMonitor" :xs="24" :md="16">
        <el-card v-loading="loading" shadow="never" class="mb-16">
          <template #header>
            <div class="card-header">
              <span>系统运行状态</span>
              <el-button text :icon="Refresh" :loading="loading" @click="loadMonitor">刷新</el-button>
            </div>
          </template>

          <el-row :gutter="16">
            <el-col :xs="24" :sm="12" :md="6">
              <div class="stat">
                <div class="stat-label">后端服务</div>
                <div class="stat-value">
                  <el-tag :type="overallType" size="large" effect="dark">{{ monitor?.status ?? '未知' }}</el-tag>
                </div>
              </div>
            </el-col>
            <el-col :xs="24" :sm="12" :md="6">
              <div class="stat">
                <div class="stat-label">MySQL</div>
                <div class="stat-value">
                  <el-tag :type="monitor?.database === 'UP' ? 'success' : 'danger'" size="large" effect="dark">
                    {{ monitor?.database ?? '未知' }}
                  </el-tag>
                </div>
              </div>
            </el-col>
            <el-col :xs="24" :sm="12" :md="6">
              <div class="stat">
                <div class="stat-label">Redis</div>
                <div class="stat-value">
                  <el-tag :type="monitor?.redis === 'UP' ? 'success' : 'danger'" size="large" effect="dark">
                    {{ monitor?.redis ?? '未知' }}
                  </el-tag>
                </div>
              </div>
            </el-col>
            <el-col :xs="24" :sm="12" :md="6">
              <div class="stat">
                <div class="stat-label">运行时长</div>
                <div class="stat-value stat-value--text">{{ uptimeText }}</div>
              </div>
            </el-col>
          </el-row>

          <el-descriptions v-if="monitor" class="mt-16" :column="2" border size="small">
            <el-descriptions-item label="应用名">{{ monitor.application }}</el-descriptions-item>
            <el-descriptions-item label="版本">{{ monitor.version }}</el-descriptions-item>
            <el-descriptions-item label="激活配置">{{ monitor.profiles || '（默认）' }}</el-descriptions-item>
            <el-descriptions-item label="服务器时间">{{ monitor.serverTime }}</el-descriptions-item>
          </el-descriptions>

          <el-alert
            v-if="monitorError"
            class="mt-16"
            type="error"
            :closable="false"
            :title="monitorError"
            show-icon
          />
        </el-card>
      </el-col>

      <!-- ---------- 我的账号 ---------- -->
      <el-col :xs="24" :md="canMonitor ? 8 : 24">
        <el-card shadow="never" class="mb-16">
          <template #header>
            <div class="card-header">
              <span>我的账号</span>
              <el-button text type="primary" @click="router.push('/profile')">个人中心</el-button>
            </div>
          </template>

          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="账号">{{ userStore.userInfo?.username }}</el-descriptions-item>
            <el-descriptions-item label="姓名">{{ userStore.realName }}</el-descriptions-item>
            <el-descriptions-item label="角色">{{ userStore.roleName }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ userStore.userInfo?.phone || '—' }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ userStore.userInfo?.email || '—' }}</el-descriptions-item>
            <el-descriptions-item label="邮件通知">
              {{ userStore.userInfo?.notifyEmail === 1 ? '已开启' : '未开启' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <!-- ---------- 快捷入口：按当前角色可见的菜单生成 ---------- -->
    <el-card shadow="never">
      <template #header>
        <span>快捷入口</span>
      </template>
      <el-space wrap>
        <el-button
          v-for="item in shortcuts"
          :key="item.path"
          plain
          @click="router.push(item.path)"
        >
          {{ item.title }}
        </el-button>
      </el-space>
      <el-empty v-if="shortcuts.length === 0" description="当前角色暂无其他可用页面" :image-size="60" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { getMonitor, type SystemMonitor } from '@/api/system'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

/** 运行状态属于运维信息，只有具备该权限的角色（默认系统管理员）才展示 */
const canMonitor = computed(() => userStore.hasPermission('system:monitor:view'))

const loading = ref(false)
const monitor = ref<SystemMonitor | null>(null)
const monitorError = ref('')

const overallType = computed(() => {
  if (!monitor.value) return 'info'
  return monitor.value.status === 'UP' ? 'success' : 'warning'
})

const uptimeText = computed(() => {
  const seconds = monitor.value?.uptimeSeconds
  if (!seconds && seconds !== 0) return '—'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  return h > 0 ? `${h} 小时 ${m} 分` : m > 0 ? `${m} 分 ${s} 秒` : `${s} 秒`
})

/** 由后端下发的菜单生成快捷入口（首页本身不列） */
const shortcuts = computed(() => {
  const list: Array<{ path: string; title: string }> = []
  for (const menu of userStore.menus) {
    if (menu.children?.length) {
      for (const child of menu.children) {
        list.push({ path: child.path, title: child.title })
      }
    } else if (menu.path !== '/dashboard') {
      list.push({ path: menu.path, title: menu.title })
    }
  }
  return list
})

async function loadMonitor(): Promise<void> {
  if (!canMonitor.value) {
    return
  }
  loading.value = true
  monitorError.value = ''
  try {
    monitor.value = await getMonitor()
  } catch (error) {
    monitor.value = null
    monitorError.value = error instanceof Error ? error.message : '无法获取系统运行状态'
  } finally {
    loading.value = false
  }
}

onMounted(loadMonitor)
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 16px;
  background: #fafbfc;
  border-radius: var(--lr-radius);
}

.stat-label {
  font-size: 13px;
  color: var(--lr-text-secondary);
}

.stat-value {
  display: flex;
  align-items: center;
  min-height: 32px;
}

.stat-value--text {
  font-size: 16px;
  font-weight: 600;
  color: var(--lr-text-primary);
}

.mt-16 {
  margin-top: 16px;
}
</style>
