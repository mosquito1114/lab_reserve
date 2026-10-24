<template>
  <div class="page-container">
    <el-card shadow="never" class="filter-card">
      <el-form :model="query" inline @submit.prevent>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="item in REPAIR_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="query.priority" placeholder="全部" clearable style="width: 110px">
            <el-option v-for="item in REPAIR_PRIORITY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备编号">
          <el-input v-model="query.equipmentNo" placeholder="如 ZY-01" clearable style="width: 150px" />
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
        <el-table-column prop="submitterName" label="报修人" width="100" />
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
        <el-table-column prop="assigneeName" label="维修人" width="100" align="center" />
        <el-table-column prop="createdAt" label="提交时间" width="170" align="center" />
        <el-table-column label="操作" width="230" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="canReview && (row.status === 'SUBMITTED' || row.status === 'REOPENED')"
                       link type="warning" @click="openReview(row)">复核</el-button>
            <el-button v-if="canAssign && row.status !== 'ACCEPTED' && row.status !== 'REJECTED'"
                       link type="primary" @click="openAssign(row)">派单</el-button>
            <el-button v-if="canAccept && row.status === 'REPAIRED'" link type="success" @click="openAccept(row)">验收</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有符合条件的工单" />
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

    <!-- 复核 -->
    <el-dialog v-model="reviewVisible" title="复核工单" width="520px">
      <el-form label-width="110px">
        <el-form-item label="复核结果">
          <el-radio-group v-model="reviewForm.result">
            <el-radio-button value="CONFIRMED">确认故障</el-radio-button>
            <el-radio-button value="REJECTED">驳回</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <template v-if="reviewForm.result === 'CONFIRMED'">
          <el-form-item label="优先级">
            <el-select v-model="reviewForm.priority" style="width: 100%">
              <el-option v-for="item in REPAIR_PRIORITY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="设备状态">
            <el-select v-model="reviewForm.equipmentStatus" clearable placeholder="不改动" style="width: 100%">
              <el-option label="维修中" :value="2" />
              <el-option label="正常" :value="1" />
            </el-select>
          </el-form-item>
        </template>
        <el-form-item label="复核意见" :required="reviewForm.result === 'REJECTED'">
          <el-input v-model="reviewForm.opinion" type="textarea" :rows="3" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitReview">确定</el-button>
      </template>
    </el-dialog>

    <!-- 派单 -->
    <el-dialog v-model="assignVisible" title="派单" width="480px">
      <el-form label-width="100px">
        <el-form-item label="维修人员" required>
          <el-select v-model="assignForm.assigneeId" filterable placeholder="请选择维修人员" style="width: 100%">
            <el-option v-for="item in maintainers" :key="item.id" :label="`${item.realName}（${item.staffNo}）`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="assignForm.opinion" maxlength="255" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="!assignForm.assigneeId" @click="submitAssign">确定</el-button>
      </template>
    </el-dialog>

    <!-- 验收 -->
    <el-dialog v-model="acceptVisible" title="验收工单" width="480px">
      <el-form label-width="100px">
        <el-form-item label="验收结果">
          <el-radio-group v-model="acceptForm.result">
            <el-radio-button value="PASS">通过</el-radio-button>
            <el-radio-button value="FAIL">不通过</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="验收意见">
          <el-input v-model="acceptForm.opinion" type="textarea" :rows="3" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>
      <el-alert v-if="acceptForm.result === 'PASS'" type="info" :closable="false" show-icon
                title="通过后工单归档，设备状态会自动恢复为「正常」" />
      <template #footer>
        <el-button @click="acceptVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitAccept">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import {
  REPAIR_PRIORITY_OPTIONS,
  REPAIR_STATUS_OPTIONS,
  acceptRepair,
  assignRepair,
  getRepairPage,
  repairPriorityTag,
  repairStatusTag,
  reviewRepair,
  type RepairOrderItem,
  type RepairQuery
} from '@/api/repair'
import { listMaintainer, type MaintainerOption } from '@/api/user'
import { useUserStore } from '@/stores/user'
import RepairDetailDialog from './components/RepairDetailDialog.vue'

const userStore = useUserStore()
const canReview = computed(() => userStore.hasPermission('repair:review'))
const canAssign = computed(() => userStore.hasPermission('repair:assign'))
const canAccept = computed(() => userStore.hasPermission('repair:accept'))

const loading = ref(false)
const saving = ref(false)
const rows = ref<RepairOrderItem[]>([])
const total = ref(0)
const maintainers = ref<MaintainerOption[]>([])
const query = reactive<RepairQuery>({ pageNum: 1, pageSize: 10, status: null, priority: null, equipmentNo: null })

async function loadPage(): Promise<void> {
  loading.value = true
  try {
    const result = await getRepairPage(query)
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
  query.priority = null
  query.equipmentNo = null
  handleSearch()
}

const detailVisible = ref(false)
const detailId = ref<number | null>(null)

function openDetail(row: RepairOrderItem): void {
  detailId.value = row.id
  detailVisible.value = true
}

// ---------- 复核 ----------
const reviewVisible = ref(false)
const reviewForm = reactive<{ id: number; result: 'CONFIRMED' | 'REJECTED'; priority: number; equipmentStatus: number | null; opinion: string }>({
  id: 0,
  result: 'CONFIRMED',
  priority: 2,
  equipmentStatus: 2,
  opinion: ''
})

function openReview(row: RepairOrderItem): void {
  reviewForm.id = row.id
  reviewForm.result = 'CONFIRMED'
  reviewForm.priority = row.priority ?? 2
  reviewForm.equipmentStatus = 2
  reviewForm.opinion = ''
  reviewVisible.value = true
}

async function submitReview(): Promise<void> {
  if (reviewForm.result === 'REJECTED' && !reviewForm.opinion.trim()) {
    ElMessage.warning('驳回必须填写原因')
    return
  }
  saving.value = true
  try {
    await reviewRepair({
      id: reviewForm.id,
      result: reviewForm.result,
      priority: reviewForm.result === 'CONFIRMED' ? reviewForm.priority : null,
      equipmentStatus: reviewForm.result === 'CONFIRMED' ? reviewForm.equipmentStatus : null,
      opinion: reviewForm.opinion || null
    })
    ElMessage.success('复核完成')
    reviewVisible.value = false
    void loadPage()
  } finally {
    saving.value = false
  }
}

// ---------- 派单 ----------
const assignVisible = ref(false)
const assignForm = reactive<{ id: number; assigneeId: number | null; opinion: string }>({ id: 0, assigneeId: null, opinion: '' })

function openAssign(row: RepairOrderItem): void {
  assignForm.id = row.id
  assignForm.assigneeId = row.assigneeId ?? maintainers.value[0]?.id ?? null
  assignForm.opinion = ''
  assignVisible.value = true
}

async function submitAssign(): Promise<void> {
  saving.value = true
  try {
    await assignRepair({ id: assignForm.id, assigneeId: assignForm.assigneeId as number, opinion: assignForm.opinion || null })
    ElMessage.success('已派单')
    assignVisible.value = false
    void loadPage()
  } finally {
    saving.value = false
  }
}

// ---------- 验收 ----------
const acceptVisible = ref(false)
const acceptForm = reactive<{ id: number; result: 'PASS' | 'FAIL'; opinion: string }>({ id: 0, result: 'PASS', opinion: '' })

function openAccept(row: RepairOrderItem): void {
  acceptForm.id = row.id
  acceptForm.result = 'PASS'
  acceptForm.opinion = ''
  acceptVisible.value = true
}

async function submitAccept(): Promise<void> {
  saving.value = true
  try {
    await acceptRepair({ id: acceptForm.id, result: acceptForm.result, opinion: acceptForm.opinion || null })
    ElMessage.success(acceptForm.result === 'PASS' ? '验收通过，已归档' : '已退回维修人')
    acceptVisible.value = false
    void loadPage()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  maintainers.value = await listMaintainer({})
  void loadPage()
})
</script>

<style scoped>
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
