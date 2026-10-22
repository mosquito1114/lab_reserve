<template>
  <div class="page-container">
    <el-card shadow="never" class="filter-card">
      <el-form :model="query" inline @submit.prevent>
        <el-form-item label="资源类型">
          <el-select v-model="query.resourceType" placeholder="全部" clearable style="width: 120px">
            <el-option label="实验室" :value="1" />
            <el-option label="设备" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作">
          <el-select v-model="query.operation" placeholder="全部" clearable style="width: 120px">
            <el-option label="新增" :value="1" />
            <el-option label="修改" :value="2" />
            <el-option label="状态变更" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间">
          <el-date-picker
            v-model="range"
            type="datetimerange"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 340px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column label="资源" min-width="200">
          <template #default="{ row }">
            <el-tag size="small" :type="row.resourceType === 1 ? 'primary' : 'success'" effect="plain">
              {{ row.resourceTypeName }}
            </el-tag>
            <span class="ml-8">{{ row.resourceNo }} {{ row.resourceName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="operationName" label="操作" width="100" align="center" />
        <el-table-column prop="updateReason" label="更新原因" min-width="180" show-overflow-tooltip />
        <el-table-column prop="operatorName" label="操作人" width="110" align="center" />
        <el-table-column prop="createdAt" label="时间" width="170" align="center" />
        <el-table-column label="快照" width="100" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDiff(row)">查看对比</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="还没有资源变更记录" />
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

    <el-dialog v-model="diffVisible" title="变更对比" width="700px">
      <div v-if="diffRow">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="资源">{{ diffRow.resourceTypeName }} {{ diffRow.resourceNo }}</el-descriptions-item>
          <el-descriptions-item label="操作">{{ diffRow.operationName }}</el-descriptions-item>
          <el-descriptions-item label="操作人">{{ diffRow.operatorName }}</el-descriptions-item>
          <el-descriptions-item label="时间">{{ diffRow.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="更新原因" :span="2">{{ diffRow.updateReason }}</el-descriptions-item>
        </el-descriptions>

        <el-table :data="diffRows" border size="small" class="mt-16">
          <el-table-column prop="field" label="字段" width="180" />
          <el-table-column prop="before" label="变更前" min-width="200" show-overflow-tooltip />
          <el-table-column prop="after" label="变更后" min-width="200" show-overflow-tooltip />
          <el-table-column label="" width="80" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.changed" type="warning" size="small" effect="dark">已改</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="diffVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { getResourceLogPage, type ResourceLogItem, type ResourceLogQuery } from '@/api/resource'

interface DiffRow {
  field: string
  before: string
  after: string
  changed: boolean
}

const loading = ref(false)
const rows = ref<ResourceLogItem[]>([])
const total = ref(0)
const range = ref<[string, string] | null>(null)

const query = reactive<ResourceLogQuery>({
  pageNum: 1,
  pageSize: 10,
  resourceType: null,
  operation: null,
  beginTime: null,
  endTime: null
})

async function loadPage(): Promise<void> {
  loading.value = true
  try {
    query.beginTime = range.value?.[0] ?? null
    query.endTime = range.value?.[1] ?? null
    const result = await getResourceLogPage(query)
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
  query.resourceType = null
  query.operation = null
  range.value = null
  handleSearch()
}

// ---------- 快照对比 ----------
const diffVisible = ref(false)
const diffRow = ref<ResourceLogItem | null>(null)
const diffRows = ref<DiffRow[]>([])

function parseJson(text: string | null): Record<string, unknown> {
  if (!text) {
    return {}
  }
  try {
    return JSON.parse(text) as Record<string, unknown>
  } catch {
    return {}
  }
}

function formatValue(value: unknown): string {
  if (value === null || value === undefined) {
    return '—'
  }
  if (typeof value === 'object') {
    return JSON.stringify(value)
  }
  return String(value)
}

function openDiff(row: ResourceLogItem): void {
  diffRow.value = row
  const before = parseJson(row.beforeJson)
  const after = parseJson(row.afterJson)
  const fields = Array.from(new Set([...Object.keys(before), ...Object.keys(after)]))
  diffRows.value = fields.map((field) => {
    const beforeText = formatValue(before[field])
    const afterText = formatValue(after[field])
    return { field, before: beforeText, after: afterText, changed: beforeText !== afterText }
  })
  diffVisible.value = true
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

.ml-8 {
  margin-left: 8px;
}
</style>
