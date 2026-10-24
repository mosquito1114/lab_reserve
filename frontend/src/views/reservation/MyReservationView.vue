<template>
  <div class="page-container">
    <el-card shadow="never" class="filter-card">
      <el-form :model="query" inline @submit.prevent>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="item in RESERVATION_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="资源类型">
          <el-select v-model="query.resourceType" placeholder="全部" clearable style="width: 110px">
            <el-option label="实验室" :value="1" />
            <el-option label="设备" :value="2" />
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
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="reserveNo" label="预约号" width="170" />
        <el-table-column prop="resourceName" label="资源" min-width="190" show-overflow-tooltip />
        <el-table-column label="时间" width="200" align="center">
          <template #default="{ row }">{{ row.reserveDate }} {{ row.slotName }}</template>
        </el-table-column>
        <el-table-column prop="purpose" label="实验内容" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)" effect="dark">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="提交时间" width="170" align="center" />
        <el-table-column label="操作" width="220" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'PENDING'" link type="danger" @click="handleCancel(row)">撤销</el-button>
            <el-button v-if="row.status === 'APPROVED' && canChange" link type="warning" @click="openChange(row)">申请调整</el-button>
            <el-button v-if="row.status === 'CHANGE_PENDING'" link type="info" @click="openDetail(row)">查看调整</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="还没有预约记录" />
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

    <!-- 申请调整 -->
    <el-dialog v-model="changeVisible" title="申请调整预约" width="560px" destroy-on-close>
      <el-alert class="mb-16" type="warning" :closable="false" show-icon
                title="审批通过前预约单不会变化；距实验开始不足 24 小时无法调整" />
      <el-form label-width="110px">
        <el-form-item label="调整类型">
          <el-radio-group v-model="changeForm.changeType" @change="loadChangeSlots">
            <el-radio-button :value="1">修改时间</el-radio-button>
            <el-radio-button :value="2">取消预约</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <template v-if="changeForm.changeType === 1">
          <el-form-item label="新日期">
            <el-date-picker v-model="changeForm.newReserveDate" type="date" value-format="YYYY-MM-DD" @change="loadChangeSlots" />
          </el-form-item>
          <el-form-item label="新时段">
            <el-select v-model="changeForm.newSlotId" placeholder="请选择可用时段" style="width: 100%">
              <el-option v-for="slot in changeSlots" :key="slot.slotId" :disabled="!slot.available"
                         :label="`${slot.slotName} ${slot.startTime}-${slot.endTime}${slot.available ? '' : '（不可用）'}`"
                         :value="slot.slotId" />
            </el-select>
          </el-form-item>
        </template>
        <el-form-item label="申请原因">
          <el-input v-model="changeForm.reason" type="textarea" :rows="3" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="changeVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitChange">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import {
  cancelReservation,
  createReservationChange,
  getMyReservationPage,
  RESERVATION_STATUS_OPTIONS,
  type ReservationItem,
  type ReservationQuery
} from '@/api/reservation'
import { getEquipmentAvailability, getLaboratoryAvailability } from '@/api/resource'
import { useUserStore } from '@/stores/user'
import ReservationDetailDialog from './components/ReservationDetailDialog.vue'

const userStore = useUserStore()
const canChange = computed(() => userStore.hasPermission('change:create'))

const loading = ref(false)
const rows = ref<ReservationItem[]>([])
const total = ref(0)
const dateRange = ref<[string, string] | null>(null)

const query = reactive<ReservationQuery>({ pageNum: 1, pageSize: 10, status: null, resourceType: null })

function statusTag(status: string): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  return (RESERVATION_STATUS_OPTIONS.find((item) => item.value === status)?.tag ?? 'info') as
    'success' | 'warning' | 'danger' | 'info' | 'primary'
}

async function loadPage(): Promise<void> {
  loading.value = true
  try {
    query.reserveDateBegin = dateRange.value?.[0] ?? null
    query.reserveDateEnd = dateRange.value?.[1] ?? null
    const result = await getMyReservationPage(query)
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
  query.resourceType = null
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

async function handleCancel(row: ReservationItem): Promise<void> {
  await ElMessageBox.confirm(`确认撤销预约「${row.reserveNo}」？`, '撤销确认', { type: 'warning' })
  await cancelReservation({ id: row.id, reason: '申请人撤销' })
  ElMessage.success('已撤销')
  void loadPage()
}

// ---------- 申请调整 ----------
const changeVisible = ref(false)
const saving = ref(false)
const changeSlots = ref<{ slotId: number; slotName: string; startTime: string; endTime: string; available: boolean }[]>([])
const changeForm = reactive<{ reservationId: number; changeType: number; reason: string; newReserveDate: string | null; newSlotId: number | null }>({
  reservationId: 0,
  changeType: 1,
  reason: '',
  newReserveDate: null,
  newSlotId: null
})

function openChange(row: ReservationItem): void {
  changeForm.reservationId = row.id
  changeForm.changeType = 1
  changeForm.reason = ''
  changeForm.newReserveDate = row.reserveDate
  changeForm.newSlotId = null
  changeSlots.value = []
  changeVisible.value = true
  void loadChangeSlots()
}

async function loadChangeSlots(): Promise<void> {
  if (changeForm.changeType !== 1 || !changeForm.newReserveDate) {
    return
  }
  const row = rows.value.find((item) => item.id === changeForm.reservationId)
  if (!row) {
    return
  }
  if (row.resourceType === 1 && row.labId) {
    const data = await getLaboratoryAvailability({ id: row.labId, date: changeForm.newReserveDate })
    changeSlots.value = data.slots.map((item) => ({ ...item }))
  } else if (row.equipmentId) {
    const data = await getEquipmentAvailability({ id: row.equipmentId, date: changeForm.newReserveDate })
    changeSlots.value = data.slots.map((item) => ({
      slotId: item.slotId,
      slotName: item.slotName,
      startTime: item.startTime,
      endTime: item.endTime,
      available: item.available
    }))
  }
}

async function submitChange(): Promise<void> {
  if (!changeForm.reason.trim()) {
    ElMessage.warning('请填写申请原因')
    return
  }
  if (changeForm.changeType === 1 && (!changeForm.newReserveDate || !changeForm.newSlotId)) {
    ElMessage.warning('请选择新的日期与时段')
    return
  }
  saving.value = true
  try {
    await createReservationChange({
      reservationId: changeForm.reservationId,
      changeType: changeForm.changeType,
      reason: changeForm.reason,
      newReserveDate: changeForm.changeType === 1 ? changeForm.newReserveDate : null,
      newSlotId: changeForm.changeType === 1 ? changeForm.newSlotId : null
    })
    ElMessage.success('调整申请已提交，等待管理员审批')
    changeVisible.value = false
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
