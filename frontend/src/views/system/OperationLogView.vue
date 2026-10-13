<template>
  <div class="page-container">
    <!-- ---------- 筛选 ---------- -->
    <el-card shadow="never" class="filter-card">
      <el-form :model="query" inline label-width="72px" @submit.prevent>
        <el-form-item label="操作类型">
          <el-select v-model="query.operationType" placeholder="全部" clearable class="w-150">
            <el-option
              v-for="item in operationTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="模块">
          <el-select v-model="query.module" placeholder="全部" clearable class="w-150">
            <el-option
              v-for="item in moduleOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="query.operatorKeyword" placeholder="姓名或账号" clearable class="w-160" />
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="query.result" placeholder="全部" clearable class="w-120">
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            class="w-360"
          />
        </el-form-item>
        <el-form-item label="IP">
          <el-input v-model="query.ip" placeholder="客户端 IP" clearable class="w-140" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button
            v-if="canExport"
            type="success"
            plain
            :icon="Download"
            :loading="exporting"
            @click="handleExport"
          >
            导出 Excel
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- ---------- 列表 ---------- -->
    <el-card shadow="never">
      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column prop="createdAt" label="操作时间" width="170" />
        <el-table-column label="操作人" width="120">
          <template #default="{ row }">
            {{ row.operatorName ?? '—' }}
          </template>
        </el-table-column>
        <el-table-column label="角色" width="130">
          <template #default="{ row }">
            <el-tag v-if="row.roleCode" size="small" effect="plain">{{ roleLabel(row.roleCode) }}</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="模块" width="110">
          <template #default="{ row }">{{ row.moduleName ?? row.module }}</template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.operationType === 'LOGIN' ? 'success' : 'info'" effect="plain">
              {{ row.operationTypeName ?? row.operationType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="操作内容" min-width="200" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="140" />
        <el-table-column label="结果" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.result === 1 ? 'success' : 'danger'">
              {{ row.result === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="90" align="right">
          <template #default="{ row }">{{ row.costMs != null ? `${row.costMs} ms` : '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有符合条件的操作日志" />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        class="pagination"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="loadPage"
        @size-change="handleSearch"
      />
    </el-card>

    <!-- ---------- 详情 ---------- -->
    <el-dialog v-model="detailVisible" title="操作日志详情" width="720px">
      <el-descriptions v-if="detail" :column="2" border size="small">
        <el-descriptions-item label="日志 ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ detail.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detail.operatorName ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="角色">{{ detail.roleCode ? roleLabel(detail.roleCode) : '—' }}</el-descriptions-item>
        <el-descriptions-item label="模块">{{ detail.moduleName ?? detail.module }}</el-descriptions-item>
        <el-descriptions-item label="操作类型">{{ detail.operationTypeName ?? detail.operationType }}</el-descriptions-item>
        <el-descriptions-item label="操作内容" :span="2">{{ detail.content }}</el-descriptions-item>
        <el-descriptions-item label="请求" :span="2">
          {{ detail.requestMethod }} {{ detail.requestUrl }}
        </el-descriptions-item>
        <el-descriptions-item label="客户端 IP">{{ detail.ip ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ detail.costMs != null ? `${detail.costMs} ms` : '—' }}</el-descriptions-item>
        <el-descriptions-item label="结果">
          <el-tag size="small" :type="detail.result === 1 ? 'success' : 'danger'">
            {{ detail.result === 1 ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="User-Agent">
          <span class="ua">{{ detail.userAgent ?? '—' }}</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.errorMsg" label="失败原因" :span="2">
          <span class="error-text">{{ detail.errorMsg }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="请求参数（已脱敏）" :span="2">
          <pre class="param">{{ detail.requestParam ?? '—' }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Refresh, Search } from '@element-plus/icons-vue'
import {
  exportOperationLog,
  getOperationLogInfo,
  getOperationLogPage,
  type OperationLogItem,
  type OperationLogQuery
} from '@/api/operationLog'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const canExport = computed(() => userStore.hasPermission('system:log:export'))

/** 下拉选项与后端枚举保持一致（OperationType / OperationModule） */
const operationTypeOptions = [
  { value: 'LOGIN', label: '登录' },
  { value: 'LOGOUT', label: '登出' },
  { value: 'CREATE', label: '新增' },
  { value: 'UPDATE', label: '修改' },
  { value: 'DELETE', label: '删除' },
  { value: 'AUDIT', label: '审核' },
  { value: 'EXPORT', label: '导出' },
  { value: 'ASSIGN', label: '派单' },
  { value: 'ACCEPT', label: '验收' },
  { value: 'QUERY_PHONE', label: '查看完整手机号' }
]

const moduleOptions = [
  { value: 'auth', label: '认证' },
  { value: 'user', label: '用户管理' },
  { value: 'resource', label: '资源管理' },
  { value: 'reservation', label: '预约管理' },
  { value: 'queue', label: '排队候补' },
  { value: 'repair', label: '设备运维' },
  { value: 'message', label: '消息通知' },
  { value: 'statistics', label: '数据统计' },
  { value: 'system', label: '系统管理' }
]

const roleLabels: Record<string, string> = {
  STUDENT: '学生',
  TEACHER: '教师',
  SYS_ADMIN: '系统管理员',
  LAB_ADMIN: '实验室管理员',
  MAINTAINER: '维修人员'
}

const loading = ref(false)
const exporting = ref(false)
const records = ref<OperationLogItem[]>([])
const total = ref(0)
const timeRange = ref<[string, string] | null>(null)

const query = reactive<OperationLogQuery>({
  pageNum: 1,
  pageSize: 10,
  operationType: null,
  module: null,
  operatorKeyword: null,
  ip: null,
  result: null,
  beginTime: null,
  endTime: null
})

const detailVisible = ref(false)
const detail = ref<OperationLogItem | null>(null)

function roleLabel(code: string): string {
  return roleLabels[code] ?? code
}

/** 时间区间 → beginTime / endTime */
function syncTimeRange(): void {
  query.beginTime = timeRange.value?.[0] ?? null
  query.endTime = timeRange.value?.[1] ?? null
}

async function loadPage(): Promise<void> {
  loading.value = true
  syncTimeRange()
  try {
    const result = await getOperationLogPage(query)
    records.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  query.pageNum = 1
  void loadPage()
}

function handleReset(): void {
  query.operationType = null
  query.module = null
  query.operatorKeyword = null
  query.ip = null
  query.result = null
  timeRange.value = null
  handleSearch()
}

async function openDetail(id: number): Promise<void> {
  detail.value = await getOperationLogInfo(id)
  detailVisible.value = true
}

async function handleExport(): Promise<void> {
  exporting.value = true
  syncTimeRange()
  try {
    // 导出不分页，但仍带上筛选条件
    const { pageNum, pageSize, ...filters } = query
    const blob = await exportOperationLog(filters)
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `操作日志_${new Date().toISOString().slice(0, 10)}.xlsx`
    link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功，请查看浏览器下载')
  } finally {
    exporting.value = false
  }
}

onMounted(loadPage)
</script>

<style scoped lang="scss">
.filter-card {
  margin-bottom: 16px;
}

.w-120 {
  width: 120px;
}

.w-140 {
  width: 140px;
}

.w-150 {
  width: 150px;
}

.w-160 {
  width: 160px;
}

.w-360 {
  width: 360px;
}

.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}

.ua {
  font-size: 12px;
  word-break: break-all;
}

.error-text {
  color: var(--el-color-danger);
}

.param {
  max-height: 160px;
  margin: 0;
  overflow: auto;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
