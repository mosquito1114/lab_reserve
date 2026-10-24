<template>
  <div class="page-container">
    <el-card shadow="never" class="filter-card">
      <el-form :model="query" inline @submit.prevent>
        <el-form-item label="资源类型">
          <el-select v-model="query.resourceType" placeholder="全部" clearable style="width: 110px">
            <el-option label="实验室" :value="1" />
            <el-option label="设备" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="实验室">
          <el-select v-model="query.labId" placeholder="全部" clearable filterable style="width: 220px">
            <el-option v-for="item in labs" :key="item.id" :label="`${item.labNo} ${item.name}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD"
                          start-placeholder="开始" end-placeholder="结束" style="width: 260px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-tabs v-model="tab" @tab-change="handleSearch">
        <el-tab-pane label="待审核" name="pending" />
        <el-tab-pane label="已通过" name="approved" />
      </el-tabs>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="reserveNo" label="预约号" width="165" />
        <el-table-column label="申请人" width="130">
          <template #default="{ row }">{{ row.applicantName }}</template>
        </el-table-column>
        <el-table-column prop="resourceName" label="资源" min-width="180" show-overflow-tooltip />
        <el-table-column label="时间" width="190" align="center">
          <template #default="{ row }">{{ row.reserveDate }} {{ row.slotName }}</template>
        </el-table-column>
        <el-table-column prop="courseName" label="课程" min-width="130" show-overflow-tooltip />
        <el-table-column prop="usePeople" label="人数" width="80" align="center" />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'PENDING' ? 'warning' : 'success'" effect="dark">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <template v-if="tab === 'pending'">
              <el-button link type="success" @click="openAudit(row, 'APPROVED')">通过</el-button>
              <el-button link type="danger" @click="openAudit(row, 'REJECTED')">驳回</el-button>
            </template>
            <el-button v-else-if="canRelease" link type="warning" @click="handleRelease(row)">释放</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="tab === 'pending' ? '没有待审核的预约' : '没有已通过的预约'" />
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

    <ReservationDetailDialog v-model="detailVisible" :reservation-id="detailId" />

    <el-dialog v-model="auditVisible" :title="auditForm.result === 'APPROVED' ? '审核通过' : '驳回预约'" width="480px">
      <el-alert v-if="auditForm.result === 'APPROVED'" class="mb-16" type="info" :closable="false" show-icon
                title="通过时会再次校验时段是否仍空闲，通过后系统自动通知申请人" />
      <el-form label-width="90px">
        <el-form-item label="审核意见" :required="auditForm.result === 'REJECTED'">
          <el-input v-model="auditForm.opinion" type="textarea" :rows="3" maxlength="255" show-word-limit
                    :placeholder="auditForm.result === 'REJECTED' ? '驳回必须填写原因' : '可选填写审核意见'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button :type="auditForm.result === 'APPROVED' ? 'success' : 'danger'" :loading="saving" @click="submitAudit">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import {
  auditReservation,
  getPendingReservationPage,
  getReservationPage,
  releaseReservation,
  type ReservationItem,
  type ReservationQuery
} from '@/api/reservation'
import { listLaboratoryOptions, type LaboratoryOption } from '@/api/resource'
import { useUserStore } from '@/stores/user'
import ReservationDetailDialog from './components/ReservationDetailDialog.vue'

const userStore = useUserStore()
const canRelease = computed(() => userStore.hasPermission('reservation:release'))

const tab = ref<'pending' | 'approved'>('pending')
const loading = ref(false)
const saving = ref(false)
const rows = ref<ReservationItem[]>([])
const total = ref(0)
const labs = ref<LaboratoryOption[]>([])
const dateRange = ref<[string, string] | null>(null)

const query = reactive<ReservationQuery>({ pageNum: 1, pageSize: 10, resourceType: null, labId: null })

async function loadPage(): Promise<void> {
  loading.value = true
  try {
    query.reserveDateBegin = dateRange.value?.[0] ?? null
    query.reserveDateEnd = dateRange.value?.[1] ?? null
    const payload = { ...query, status: tab.value === 'approved' ? 'APPROVED' : 'PENDING' }
    const result = tab.value === 'pending'
      ? await getPendingReservationPage(payload)
      : await getReservationPage(payload)
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
  query.labId = null
  dateRange.value = null
  handleSearch()
}

// ---------- 详情 ----------
const detailVisible = ref(false)
const detailId = ref<number | null>(null)

function openDetail(row: ReservationItem): void {
  detailId.value = row.id
  detailVisible.value = true
}

// ---------- 审核 ----------
const auditVisible = ref(false)
const auditForm = reactive<{ id: number; result: 'APPROVED' | 'REJECTED'; opinion: string }>({
  id: 0,
  result: 'APPROVED',
  opinion: ''
})

function openAudit(row: ReservationItem, result: 'APPROVED' | 'REJECTED'): void {
  auditForm.id = row.id
  auditForm.result = result
  auditForm.opinion = ''
  auditVisible.value = true
}

async function submitAudit(): Promise<void> {
  if (auditForm.result === 'REJECTED' && !auditForm.opinion.trim()) {
    ElMessage.warning('驳回必须填写原因')
    return
  }
  saving.value = true
  try {
    await auditReservation({ id: auditForm.id, result: auditForm.result, opinion: auditForm.opinion || null })
    ElMessage.success(auditForm.result === 'APPROVED' ? '已通过，已通知申请人' : '已驳回，已通知申请人')
    auditVisible.value = false
    void loadPage()
  } finally {
    saving.value = false
  }
}

async function handleRelease(row: ReservationItem): Promise<void> {
  const { value } = await ElMessageBox.prompt('释放后该时段将空出（候补顺延在迭代 6 开放），请填写原因', '释放时段', {
    type: 'warning',
    inputPlaceholder: '如 实验课临时取消'
  })
  await releaseReservation({ id: row.id, reason: value })
  ElMessage.success('已释放')
  void loadPage()
}

onMounted(async () => {
  labs.value = await listLaboratoryOptions({})
  void loadPage()
})
</script>

<style scoped>
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
