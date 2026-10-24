<template>
  <div class="page-container">
    <el-card shadow="never" class="filter-card">
      <el-form :model="query" inline @submit.prevent>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="item in REPAIR_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button type="success" :icon="Plus" @click="goApply">我要报修</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="orderNo" label="工单号" width="170" />
        <el-table-column label="设备" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.equipmentNo }} {{ row.equipmentName }}</template>
        </el-table-column>
        <el-table-column prop="faultDesc" label="故障现象" min-width="200" show-overflow-tooltip />
        <el-table-column label="优先级" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="repairPriorityTag(row.priority)">{{ row.priorityName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="repairStatusTag(row.status)" effect="dark">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="assigneeName" label="维修人" width="110" align="center" />
        <el-table-column prop="createdAt" label="提交时间" width="170" align="center" />
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="还没有报修记录" />
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

    <RepairDetailDialog v-model="detailVisible" :order-id="detailId" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  REPAIR_STATUS_OPTIONS,
  getMySubmittedRepairs,
  repairPriorityTag,
  repairStatusTag,
  type RepairOrderItem,
  type RepairQuery
} from '@/api/repair'
import RepairDetailDialog from './components/RepairDetailDialog.vue'

const router = useRouter()
const loading = ref(false)
const rows = ref<RepairOrderItem[]>([])
const total = ref(0)
const query = reactive<RepairQuery>({ pageNum: 1, pageSize: 10, status: null })

async function loadPage(): Promise<void> {
  loading.value = true
  try {
    const result = await getMySubmittedRepairs(query)
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
  query.status = null
  handleSearch()
}

function goApply(): void {
  void router.push('/repair/apply')
}

const detailVisible = ref(false)
const detailId = ref<number | null>(null)

function openDetail(row: RepairOrderItem): void {
  detailId.value = row.id
  detailVisible.value = true
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
