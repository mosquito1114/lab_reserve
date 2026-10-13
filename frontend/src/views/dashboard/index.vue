<template>
  <div class="page-container dashboard">
    <el-alert
      v-if="!userStore.isLogin"
      class="mb-16"
      type="info"
      :closable="false"
      title="当前未登录"
      description="骨架阶段（迭代 0）登录功能尚未接入，迭代 1 会实现 JWT 登录与精简 RBAC 权限控制。"
      show-icon
    />

    <!-- ---------- 系统健康状态 ---------- -->
    <el-card shadow="never" class="mb-16">
      <template #header>
        <div class="card-header">
          <span>系统状态</span>
          <el-button text :icon="Refresh" :loading="loading" @click="loadHealth">刷新</el-button>
        </div>
      </template>

      <el-row :gutter="16">
        <el-col :xs="24" :sm="12" :md="6">
          <div class="stat">
            <div class="stat-label">后端服务</div>
            <div class="stat-value">
              <el-tag :type="overallType" size="large" effect="dark">{{ health?.status ?? '未知' }}</el-tag>
            </div>
          </div>
        </el-col>
        <el-col :xs="24" :sm="12" :md="6">
          <div class="stat">
            <div class="stat-label">MySQL</div>
            <div class="stat-value">
              <el-tag :type="health?.database === 'UP' ? 'success' : 'danger'" size="large">
                {{ health?.database ?? '未知' }}
              </el-tag>
            </div>
          </div>
        </el-col>
        <el-col :xs="24" :sm="12" :md="6">
          <div class="stat">
            <div class="stat-label">Redis</div>
            <div class="stat-value">
              <el-tag :type="health?.redis === 'UP' ? 'success' : 'danger'" size="large">
                {{ health?.redis ?? '未知' }}
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

      <el-descriptions v-if="health" class="mt-16" :column="2" border size="small">
        <el-descriptions-item label="应用名">{{ health.application }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ health.version }}</el-descriptions-item>
        <el-descriptions-item label="激活配置">{{ health.profiles || '（默认）' }}</el-descriptions-item>
        <el-descriptions-item label="服务器时间">{{ health.serverTime }}</el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="healthError"
        class="mt-16"
        type="error"
        :closable="false"
        :title="healthError"
        show-icon
      />
    </el-card>

    <!-- ---------- 项目骨架进度 ---------- -->
    <el-card shadow="never">
      <template #header>
        <span>项目骨架（迭代 0）</span>
      </template>

      <el-descriptions :column="1" border size="small">
        <el-descriptions-item
          v-for="item in skeletonItems"
          :key="item.label"
          :label="item.label"
        >
          <span :class="item.done ? 'text-secondary' : 'text-danger'">
            {{ item.done ? '✅ ' : '⏳ ' }}{{ item.value }}
          </span>
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">后续迭代</el-divider>
      <el-space wrap>
        <el-tag v-for="m in upcoming" :key="m" type="info" effect="plain">{{ m }}</el-tag>
      </el-space>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getHealth, type SystemHealth } from '@/api/system'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const loading = ref(false)
const health = ref<SystemHealth | null>(null)
const healthError = ref('')

const overallType = computed(() => {
  if (!health.value) return 'info'
  return health.value.status === 'UP' ? 'success' : 'warning'
})

const uptimeText = computed(() => {
  const seconds = health.value?.uptimeSeconds
  if (!seconds && seconds !== 0) return '—'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  return h > 0 ? `${h} 小时 ${m} 分` : m > 0 ? `${m} 分 ${s} 秒` : `${s} 秒`
})

const skeletonItems = [
  { label: 'Docker 编排', value: 'mysql / redis / backend / frontend 四容器', done: true },
  { label: '建表', value: 'Flyway V1__init_schema.sql · 24 张表', done: true },
  { label: '种子数据', value: '5 角色 / 47 权限 / 16 用户 / 12 实验室 / 84 设备 / 360 预约', done: true },
  { label: '统一响应与异常', value: 'R<T> + GlobalExceptionHandler（HTTP 状态码 = 业务码）', done: true },
  { label: '基础设施', value: 'Redis 分布式锁 / @OpLog 操作日志 / @Sensitive 脱敏 / OSS / 异步线程池', done: true },
  { label: '前端骨架', value: 'Vue3 + TS + Element Plus + Pinia + 统一 post 封装', done: true },
  { label: '登录与权限', value: 'JWT + 精简 RBAC（迭代 1）', done: false }
]

const upcoming = [
  '迭代 1 认证与权限',
  '迭代 2 用户管理',
  '迭代 3 消息通知',
  '迭代 4 资源管理',
  '迭代 5 预约管理',
  '迭代 6 排队与候补',
  '迭代 7 设备运维',
  '迭代 8 统计报表'
]

async function loadHealth(): Promise<void> {
  loading.value = true
  healthError.value = ''
  try {
    health.value = await getHealth()
  } catch (error) {
    health.value = null
    healthError.value = error instanceof Error ? error.message : '无法连接后端服务'
  } finally {
    loading.value = false
  }
}

onMounted(loadHealth)
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
</style>
