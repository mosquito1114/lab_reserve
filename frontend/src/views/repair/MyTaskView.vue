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
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="orderNo" label="工单号" width="170" />
        <el-table-column label="设备" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.equipmentNo }} {{ row.equipmentName }}</template>
        </el-table-column>
        <el-table-column prop="faultDesc" label="故障现象" min-width="180" show-overflow-tooltip />
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
        <el-table-column prop="assignTime" label="派单时间" width="170" align="center" />
        <el-table-column label="操作" width="230" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="['ASSIGNED', 'REPAIRING', 'REOPENED'].includes(row.status)" link type="warning"
                       @click="openProgress(row)">更新进度</el-button>
            <el-button v-if="['ASSIGNED', 'REPAIRING', 'REOPENED'].includes(row.status)" link type="success"
                       @click="openComplete(row)">完成维修</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂时没有指派给你的维修任务" />
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

    <el-dialog v-model="progressVisible" title="更新维修进度" width="520px">
      <el-form label-width="100px">
        <el-form-item label="进度说明" required>
          <el-input v-model="progressForm.repairDesc" type="textarea" :rows="3" maxlength="1000" show-word-limit
                    placeholder="如：已定位为主板电容损坏，等待配件" />
        </el-form-item>
        <el-form-item label="配件更换">
          <el-input v-model="progressForm.partsReplaced" maxlength="500" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="progressVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitProgress">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="completeVisible" title="完成维修" width="520px">
      <el-form label-width="100px">
        <el-form-item label="维修说明" required>
          <el-input v-model="completeForm.repairDesc" type="textarea" :rows="3" maxlength="1000" show-word-limit
                    placeholder="如：更换主板电容 2 个，通电测试正常" />
        </el-form-item>
        <el-form-item label="配件更换">
          <el-input v-model="completeForm.partsReplaced" maxlength="500" placeholder="如：电容 25V/470uF × 2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="completeVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitComplete">提交并通知验收</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import {
  REPAIR_STATUS_OPTIONS,
  completeRepair,
  getMyAssignedRepairs,
  repairPriorityTag,
  repairStatusTag,
  updateRepairProgress,
  type RepairOrderItem,
  type RepairQuery
} from '@/api/repair'
import RepairDetailDialog from './components/RepairDetailDialog.vue'

const loading = ref(false)
const saving = ref(false)
const rows = ref<RepairOrderItem[]>([])
const total = ref(0)
const query = reactive<RepairQuery>({ pageNum: 1, pageSize: 10, status: null })

async function loadPage(): Promise<void> {
  loading.value = true
  try {
    const result = await getMyAssignedRepairs(query)
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

const detailVisible = ref(false)
const detailId = ref<number | null>(null)

function openDetail(row: RepairOrderItem): void {
  detailId.value = row.id
  detailVisible.value = true
}

const progressVisible = ref(false)
const progressForm = reactive<{ id: number; repairDesc: string; partsReplaced: string }>({
  id: 0,
  repairDesc: '',
  partsReplaced: ''
})

function openProgress(row: RepairOrderItem): void {
  progressForm.id = row.id
  progressForm.repairDesc = ''
  progressForm.partsReplaced = ''
  progressVisible.value = true
}

async function submitProgress(): Promise<void> {
  if (!progressForm.repairDesc.trim()) {
    ElMessage.warning('请填写进度说明')
    return
  }
  saving.value = true
  try {
    await updateRepairProgress({ ...progressForm })
    ElMessage.success('进度已提交，报修人会收到通知')
    progressVisible.value = false
    void loadPage()
  } finally {
    saving.value = false
  }
}

const completeVisible = ref(false)
const completeForm = reactive<{ id: number; repairDesc: string; partsReplaced: string }>({
  id: 0,
  repairDesc: '',
  partsReplaced: ''
})

function openComplete(row: RepairOrderItem): void {
  completeForm.id = row.id
  completeForm.repairDesc = ''
  completeForm.partsReplaced = ''
  completeVisible.value = true
}

async function submitComplete(): Promise<void> {
  if (!completeForm.repairDesc.trim()) {
    ElMessage.warning('请填写维修说明')
    return
  }
  saving.value = true
  try {
    await completeRepair({ ...completeForm })
    ElMessage.success('已完成维修，等待管理员验收')
    completeVisible.value = false
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
