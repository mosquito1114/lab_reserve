<template>
  <div class="page-container">
    <el-card shadow="never" class="filter-card">
      <el-form :model="query" inline @submit.prevent>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option label="待审批" :value="0" />
            <el-option label="已通过" :value="1" />
            <el-option label="已驳回" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="调整类型">
          <el-select v-model="query.changeType" placeholder="全部" clearable style="width: 130px">
            <el-option label="修改预约" :value="1" />
            <el-option label="取消预约" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="reserveNo" label="预约号" width="165" />
        <el-table-column prop="applicantName" label="申请人" width="110" />
        <el-table-column prop="changeTypeName" label="类型" width="110" align="center" />
        <el-table-column prop="summary" label="变更摘要" min-width="240" show-overflow-tooltip />
        <el-table-column prop="reason" label="申请原因" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 0 ? 'warning' : row.status === 1 ? 'success' : 'danger'" effect="dark">
              {{ row.statusName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="申请时间" width="170" align="center" />
        <el-table-column label="操作" width="170" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDiff(row)">对比</el-button>
            <template v-if="row.status === 0">
              <el-button link type="success" @click="openAudit(row, 1)">通过</el-button>
              <el-button link type="danger" @click="openAudit(row, 2)">驳回</el-button>
            </template>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有调整申请" />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        class="pagination"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :page-sizes="[10, 20, 50]"
        @current-change="loadPage"
        @size-change="handleSearch"
      />
    </el-card>

    <!-- 快照对比 -->
    <el-dialog v-model="diffVisible" title="变更对比" width="700px">
      <div v-if="diffRow">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="预约号">{{ diffRow.reserveNo }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ diffRow.changeTypeName }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ diffRow.applicantName }}</el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ diffRow.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="变更摘要" :span="2">{{ diffRow.summary }}</el-descriptions-item>
          <el-descriptions-item label="申请原因" :span="2">{{ diffRow.reason }}</el-descriptions-item>
          <el-descriptions-item v-if="diffRow.auditOpinion" label="审批意见" :span="2">{{ diffRow.auditOpinion }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="diffRows" border size="small" class="mt-16">
          <el-table-column prop="field" label="字段" width="170" />
          <el-table-column prop="before" label="变更前" min-width="200" show-overflow-tooltip />
          <el-table-column prop="after" label="变更后" min-width="200" show-overflow-tooltip />
        </el-table>
      </div>
      <template #footer>
        <el-button @click="diffVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 审批 -->
    <el-dialog v-model="auditVisible" :title="auditForm.result === 1 ? '通过调整申请' : '驳回调整申请'" width="480px">
      <el-alert v-if="auditForm.result === 1" class="mb-16" type="warning" :closable="false" show-icon
                title="通过时会在锁内再次校验目标时段，并把变更回写到预约单" />
      <el-form label-width="90px">
        <el-form-item label="审批意见">
          <el-input v-model="auditForm.opinion" type="textarea" :rows="3" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button :type="auditForm.result === 1 ? 'success' : 'danger'" :loading="saving" @click="submitAudit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import {
  auditReservationChange,
  getReservationChangePage,
  type ReservationChangeItem
} from '@/api/reservation'

interface DiffRow {
  field: string
  before: string
  after: string
}

const loading = ref(false)
const saving = ref(false)
const rows = ref<ReservationChangeItem[]>([])
const total = ref(0)

const query = reactive<{ pageNum: number; pageSize: number; status: number | null; changeType: number | null }>({
  pageNum: 1,
  pageSize: 10,
  status: 0,
  changeType: null
})

async function loadPage(): Promise<void> {
  loading.value = true
  try {
    const result = await getReservationChangePage(query)
    rows.value = result.records
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
  query.status = 0
  query.changeType = null
  handleSearch()
}

// ---------- 对比 ----------
const diffVisible = ref(false)
const diffRow = ref<ReservationChangeItem | null>(null)
const diffRows = ref<DiffRow[]>([])

function parse(text: string | null): Record<string, unknown> {
  if (!text) {
    return {}
  }
  try {
    return JSON.parse(text) as Record<string, unknown>
  } catch {
    return {}
  }
}

function fmt(value: unknown): string {
  if (value === null || value === undefined) {
    return '—'
  }
  if (typeof value === 'object') {
    return JSON.stringify(value)
  }
  return String(value)
}

function openDiff(row: ReservationChangeItem): void {
  diffRow.value = row
  const before = parse(row.beforeJson)
  const after = parse(row.afterJson)
  const fields = Array.from(new Set([...Object.keys(before), ...Object.keys(after)]))
  diffRows.value = fields.map((field) => ({ field, before: fmt(before[field]), after: fmt(after[field]) }))
  diffVisible.value = true
}

// ---------- 审批 ----------
const auditVisible = ref(false)
const auditForm = reactive<{ id: number; result: number; opinion: string }>({ id: 0, result: 1, opinion: '' })

function openAudit(row: ReservationChangeItem, result: number): void {
  auditForm.id = row.id
  auditForm.result = result
  auditForm.opinion = ''
  auditVisible.value = true
}

async function submitAudit(): Promise<void> {
  saving.value = true
  try {
    await auditReservationChange({ id: auditForm.id, result: auditForm.result, opinion: auditForm.opinion || null })
    ElMessage.success('已处理')
    auditVisible.value = false
    void loadPage()
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  void loadPage()
})
</script>

<style scoped>
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
