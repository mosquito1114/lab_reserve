<template>
  <div class="page-container dashboard">
    <PageHeader :title="greeting" :subtitle="`${userStore.roleName} · 共 ${userStore.permissions.length} 项权限`">
      <template #extra>
        <el-button class="btn-pill" type="primary" :icon="Refresh" :loading="loading" @click="reloadAll">
          刷新
        </el-button>
      </template>
    </PageHeader>

    <!-- ---------- 业务概览：有 stat:view 的角色可见 ---------- -->
    <el-row v-if="canStat" :gutter="16" class="overview-row">
      <el-col :xs="24" :sm="12" :md="6" v-for="card in overviewCards" :key="card.label">
        <el-card
          shadow="never"
          class="overview-card"
          :class="{ 'is-highlight': card.highlight }"
          v-loading="overviewLoading"
        >
          <div class="overview-head">
            <span class="overview-icon">
              <el-icon :size="16"><component :is="card.icon" /></el-icon>
            </span>
            <span class="overview-label">{{ card.label }}</span>
            <span v-if="card.badge" class="overview-badge">{{ card.badge }}</span>
          </div>

          <div class="overview-value tabular">{{ card.value }}</div>

          <div class="overview-foot">
            <span class="overview-dot" />
            <span class="overview-hint">{{ card.hint }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <!-- ---------- 左栏：系统运行状态 + 快捷入口 ---------- -->
      <el-col :xs="24" :lg="canMonitor ? 16 : 24">
        <div class="col-stack">
          <el-card v-if="canMonitor" v-loading="loading" shadow="never">
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

          <!-- ---------- 快捷入口：按当前角色可见的菜单生成 ---------- -->
          <el-card shadow="never">
            <template #header>
              <span>快捷入口</span>
            </template>

            <div v-if="shortcuts.length" class="quick-links">
              <div
                v-for="item in shortcuts"
                :key="item.path"
                class="quick-link"
                @click="router.push(item.path)"
              >
                <span class="quick-link-icon">
                  <el-icon :size="15"><component :is="item.icon || 'Menu'" /></el-icon>
                </span>
                <span class="quick-link-title">{{ item.title }}</span>
                <el-icon class="quick-link-arrow"><ArrowRight /></el-icon>
              </div>
            </div>
            <el-empty v-else description="当前角色暂无其他可用页面" :image-size="60" />
          </el-card>
        </div>
      </el-col>

      <!-- ---------- 右栏：我的账号 ---------- -->
      <el-col :xs="24" :lg="canMonitor ? 8 : 24">
        <div class="col-stack">
          <el-card shadow="never">
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
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Refresh } from '@element-plus/icons-vue'
import { getMonitor, type SystemMonitor } from '@/api/system'
import { getOverview, type StatisticsOverview } from '@/api/statistics'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

/** 运行状态属于运维信息，只有具备该权限的角色（默认系统管理员）才展示 */
const canMonitor = computed(() => userStore.hasPermission('system:monitor:view'))
const canStat = computed(() => userStore.hasPermission('stat:view'))

/** 按小时返回问候语（纯展示） */
const greeting = computed(() => {
  const hour = new Date().getHours()
  const name = userStore.realName || '你好'
  if (hour < 6) return `夜深了，${name}`
  if (hour < 12) return `上午好，${name}`
  if (hour < 14) return `中午好，${name}`
  if (hour < 18) return `下午好，${name}`
  return `晚上好，${name}`
})

const overview = ref<StatisticsOverview | null>(null)
const overviewLoading = ref(false)

/**
 * 四张形态各异的概览卡。
 *
 * hint 只是说明性文字；后端 getOverview 不返回环比数据，
 * 因此这里不展示趋势百分比，避免出现与真实业务不符的数字。
 */
const overviewCards = computed(() => [
  {
    label: '预约总数',
    value: overview.value?.reservationTotal ?? 0,
    icon: 'Calendar',
    hint: '含历史全部预约',
    badge: '',
    highlight: false
  },
  {
    label: '待审核',
    value: overview.value?.pendingAudit ?? 0,
    icon: 'Clock',
    hint: 'PENDING 状态',
    badge: '',
    highlight: false
  },
  {
    label: '今日预约',
    value: overview.value?.todayReservation ?? 0,
    icon: 'Sunrise',
    hint: '当日 00:00 起',
    badge: '',
    highlight: false
  },
  {
    label: '待处理工单',
    value: overview.value?.repairPending ?? 0,
    icon: 'Tools',
    hint: '待审核与维修中',
    badge: '本周',
    highlight: true
  }
])

async function loadOverview(): Promise<void> {
  if (!canStat.value) {
    return
  }
  overviewLoading.value = true
  try {
    overview.value = await getOverview()
  } catch {
    // 首页非关键路径，失败静默
  } finally {
    overviewLoading.value = false
  }
}

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
  const list: Array<{ path: string; title: string; icon?: string }> = []
  for (const menu of userStore.menus) {
    if (menu.children?.length) {
      for (const child of menu.children) {
        list.push({ path: child.path, title: child.title, icon: child.icon })
      }
    } else if (menu.path !== '/dashboard') {
      list.push({ path: menu.path, title: menu.title, icon: menu.icon })
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

async function reloadAll(): Promise<void> {
  await Promise.all([loadMonitor(), loadOverview()])
}

onMounted(() => {
  void loadMonitor()
  void loadOverview()
})
</script>

<style scoped lang="scss">
/* ==================== 布局 ==================== */
.overview-row {
  margin-bottom: var(--lr-card-gap);
}

.col-stack {
  display: flex;
  flex-direction: column;
  gap: var(--lr-card-gap);
  margin-bottom: var(--lr-card-gap);
}

/* ==================== 概览卡 ==================== */
.overview-card {
  height: 100%;

  :deep(.el-card__body) {
    display: flex;
    flex-direction: column;
    gap: 14px;
    height: 100%;
    padding: 20px;
  }
}

.overview-head {
  display: flex;
  gap: 8px;
  align-items: center;
}

.overview-icon {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  color: var(--lr-primary);
  background: var(--lr-primary-soft);
  border-radius: var(--lr-radius-sm);
}

.overview-label {
  font-size: var(--lr-font-sm);
  color: var(--lr-text-secondary);
}

.overview-badge {
  padding: 1px 8px;
  margin-left: auto;
  font-size: var(--lr-font-xs);
  color: var(--lr-primary);
  background: var(--lr-primary-soft);
  border-radius: var(--lr-radius-pill);
}

/* 大数字：全页视觉重心 */
.overview-value {
  font-size: var(--lr-font-display);
  font-weight: 700;
  line-height: 1;
  color: var(--lr-text-primary);
  letter-spacing: -0.03em;
  white-space: nowrap;
}

.overview-foot {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-top: auto;
}

.overview-dot {
  flex-shrink: 0;
  width: 5px;
  height: 5px;
  background: var(--lr-primary);
  border-radius: 50%;
}

.overview-hint {
  font-size: var(--lr-font-xs);
  color: var(--lr-text-secondary);
}

/* ---------- 第 4 张：整块赭色反转，作为焦点 ---------- */
.overview-card.is-highlight {
  background: var(--lr-gradient-primary);
  border-color: transparent;
  box-shadow: var(--lr-shadow-primary);

  .overview-label,
  .overview-hint {
    color: rgb(255 255 255 / 82%);
  }

  .overview-value {
    color: #fff;
  }

  .overview-icon {
    color: #fff;
    background: rgb(255 255 255 / 18%);
  }

  .overview-badge {
    color: #fff;
    background: rgb(255 255 255 / 22%);
  }

  .overview-dot {
    background: rgb(255 255 255 / 75%);
  }
}

/* ==================== 运行状态 ==================== */
.stat {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
  background: var(--lr-primary-soft);
  border-radius: var(--lr-radius);
}

.stat-label {
  font-size: var(--lr-font-sm);
  color: var(--lr-text-secondary);
}

.stat-value {
  display: flex;
  align-items: center;
  min-height: 32px;
}

.stat-value--text {
  font-size: var(--lr-font-lg);
  font-weight: 600;
  color: var(--lr-text-primary);
}

/* ==================== 快捷入口 ==================== */
.quick-links {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 10px;
}

.quick-link {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 12px 14px;
  cursor: pointer;
  background: var(--lr-bg-page);
  border: 1px solid transparent;
  border-radius: var(--lr-radius);
  transition: background-color var(--lr-duration) var(--lr-ease),
    border-color var(--lr-duration) var(--lr-ease),
    box-shadow var(--lr-duration) var(--lr-ease);

  &:hover {
    background: var(--lr-bg-card);
    border-color: var(--lr-border-light);
    box-shadow: var(--lr-shadow-sm);

    .quick-link-arrow {
      color: var(--lr-primary);
      transform: translateX(2px);
    }
  }
}

.quick-link-icon {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: var(--lr-primary);
  background: var(--lr-primary-soft);
  border-radius: var(--lr-radius-sm);
}

.quick-link-title {
  flex: 1;
  overflow: hidden;
  font-size: var(--lr-font-sm);
  color: var(--lr-text-regular);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-link-arrow {
  flex-shrink: 0;
  font-size: 13px;
  color: var(--lr-text-placeholder);
  transition: color var(--lr-duration) var(--lr-ease),
    transform var(--lr-duration) var(--lr-ease);
}
</style>
