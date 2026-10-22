<template>
  <div class="page-container">
    <el-card shadow="never" class="filter-card">
      <el-form :model="query" inline @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="编号 / 名称 / 位置" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="实验类型">
          <el-select v-model="query.experimentTypeId" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="item in types" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="最小容量">
          <el-input-number v-model="query.minCapacity" :min="0" :step="10" controls-position="right" style="width: 120px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 110px">
            <el-option label="可用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button v-if="canCreate" type="success" :icon="Plus" @click="openCreate">新增实验室</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="labNo" label="编号" width="100" />
        <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="location" label="位置" min-width="150" show-overflow-tooltip />
        <el-table-column label="开放时间" width="150" align="center">
          <template #default="{ row }">{{ row.openTimeStart }}-{{ row.openTimeEnd }}</template>
        </el-table-column>
        <el-table-column prop="openWeekdaysText" label="开放星期" width="130" align="center" />
        <el-table-column prop="capacity" label="容量" width="80" align="center" />
        <el-table-column prop="experimentTypeName" label="实验类型" width="100" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'" :effect="row.status === 1 ? 'dark' : 'plain'">
              {{ row.status === 1 ? '可用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="goAvailability(row)">可用时段</el-button>
            <el-button v-if="canUpdate" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="canDisable" link :type="row.status === 1 ? 'danger' : 'success'" @click="openStatus(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有符合条件的实验室" />
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

    <!-- 新增 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="isCreate ? '新增实验室' : '编辑实验室'" width="620px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="实验室编号" prop="labNo">
          <el-input v-model="form.labNo" placeholder="格式 LAB-XX，如 LAB-01" :disabled="!isCreate" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如 计算机基础实验室" />
        </el-form-item>
        <el-form-item label="位置" prop="location">
          <el-input v-model="form.location" placeholder="如 实验楼 A301" />
        </el-form-item>
        <el-form-item label="开放时间" prop="openTimeStart">
          <el-time-picker
            v-model="openTimeRange"
            is-range
            format="HH:mm"
            value-format="HH:mm"
            start-placeholder="开始"
            end-placeholder="结束"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="开放星期" prop="openWeekdays">
          <el-checkbox-group v-model="weekdayList">
            <el-checkbox v-for="day in WEEKDAYS" :key="day.value" :value="day.value">{{ day.label }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="容纳人数" prop="capacity">
          <el-input-number v-model="form.capacity" :min="1" :max="1000" controls-position="right" />
        </el-form-item>
        <el-form-item label="实验类型" prop="experimentTypeId">
          <el-select v-model="form.experimentTypeId" placeholder="请选择" style="width: 100%">
            <el-option v-for="item in types" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="更新原因" prop="updateReason">
          <el-input v-model="form.updateReason" placeholder="必填，会写入资源更新日志" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="!form.updateReason.trim()" @click="handleSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 停用 / 启用 -->
    <el-dialog v-model="statusVisible" :title="statusForm.status === 0 ? '停用实验室' : '启用实验室'" width="440px">
      <el-alert
        class="mb-16"
        type="warning"
        :closable="false"
        show-icon
        title="停用后预约页将选不到该实验室，正在占用中的预约不受影响"
      />
      <el-form label-width="90px">
        <el-form-item label="更新原因" required>
          <el-input v-model="statusForm.updateReason" placeholder="必填" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="statusVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!statusForm.updateReason.trim()" :loading="saving" @click="handleStatus">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  createLaboratory,
  listExperimentTypes,
  updateLaboratory,
  updateLaboratoryStatus,
  getLaboratoryPage,
  type ExperimentTypeItem,
  type LaboratoryItem,
  type LaboratoryPayload,
  type LaboratoryQuery
} from '@/api/resource'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const canCreate = computed(() => userStore.hasPermission('resource:lab:create'))
const canUpdate = computed(() => userStore.hasPermission('resource:lab:update'))
const canDisable = computed(() => userStore.hasPermission('resource:lab:disable'))

const WEEKDAYS = [
  { value: 1, label: '周一' },
  { value: 2, label: '周二' },
  { value: 3, label: '周三' },
  { value: 4, label: '周四' },
  { value: 5, label: '周五' },
  { value: 6, label: '周六' },
  { value: 7, label: '周日' }
]

const loading = ref(false)
const saving = ref(false)
const rows = ref<LaboratoryItem[]>([])
const types = ref<ExperimentTypeItem[]>([])
const total = ref(0)

const query = reactive<LaboratoryQuery>({
  pageNum: 1,
  pageSize: 10,
  keyword: null,
  experimentTypeId: null,
  minCapacity: null,
  status: null
})

async function loadPage(): Promise<void> {
  loading.value = true
  try {
    const result = await getLaboratoryPage(query)
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
  query.keyword = null
  query.experimentTypeId = null
  query.minCapacity = null
  query.status = null
  handleSearch()
}

function goAvailability(row: LaboratoryItem): void {
  void router.push({ path: '/resource/availability', query: { type: 'lab', id: String(row.id) } })
}

// ---------- 表单 ----------
const dialogVisible = ref(false)
const isCreate = ref(true)
const formRef = ref<FormInstance>()
const openTimeRange = ref<[string, string] | null>(['08:00', '18:00'])
const weekdayList = ref<number[]>([1, 2, 3, 4, 5])

const form = reactive<LaboratoryPayload>({
  id: null,
  labNo: '',
  name: '',
  location: '',
  openTimeStart: '08:00',
  openTimeEnd: '18:00',
  openWeekdays: '1,2,3,4,5',
  capacity: 30,
  experimentTypeId: 0 as unknown as number,
  description: '',
  updateReason: ''
})

const rules: FormRules<LaboratoryPayload> = {
  labNo: [
    { required: true, message: '请输入实验室编号', trigger: 'blur' },
    { pattern: /^LAB-\d{2}$/, message: '格式应为 LAB-两位数字，如 LAB-01', trigger: 'blur' }
  ],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  location: [{ required: true, message: '请输入位置', trigger: 'blur' }],
  capacity: [{ required: true, message: '请输入容纳人数', trigger: 'blur' }],
  experimentTypeId: [{ required: true, message: '请选择实验类型', trigger: 'change' }],
  updateReason: [{ required: true, message: '请填写更新原因（资源变更必须留痕）', trigger: 'blur' }]
}

function resetForm(): void {
  form.id = null
  form.labNo = ''
  form.name = ''
  form.location = ''
  form.openTimeStart = '08:00'
  form.openTimeEnd = '18:00'
  form.openWeekdays = '1,2,3,4,5'
  form.capacity = 30
  form.experimentTypeId = (types.value[0]?.id ?? 0) as number
  form.description = ''
  form.updateReason = ''
  openTimeRange.value = ['08:00', '18:00']
  weekdayList.value = [1, 2, 3, 4, 5]
}

function openCreate(): void {
  isCreate.value = true
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: LaboratoryItem): void {
  isCreate.value = false
  form.id = row.id
  form.labNo = row.labNo
  form.name = row.name
  form.location = row.location
  form.openTimeStart = row.openTimeStart
  form.openTimeEnd = row.openTimeEnd
  form.openWeekdays = row.openWeekdays
  form.capacity = row.capacity
  form.experimentTypeId = row.experimentTypeId
  form.description = row.description ?? ''
  form.updateReason = ''
  openTimeRange.value = [row.openTimeStart.slice(0, 5), row.openTimeEnd.slice(0, 5)]
  weekdayList.value = row.openWeekdays.split(',').map((item) => Number(item))
  dialogVisible.value = true
}

async function handleSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (!openTimeRange.value) {
    ElMessage.warning('请选择开放时间')
    return
  }
  if (weekdayList.value.length === 0) {
    ElMessage.warning('请至少选择一个开放星期')
    return
  }
  form.openTimeStart = openTimeRange.value[0]
  form.openTimeEnd = openTimeRange.value[1]
  form.openWeekdays = [...weekdayList.value].sort((a, b) => a - b).join(',')

  saving.value = true
  try {
    if (isCreate.value) {
      await createLaboratory(form)
      ElMessage.success('新增成功')
    } else {
      await updateLaboratory(form)
      ElMessage.success('修改成功')
    }
    dialogVisible.value = false
    void loadPage()
  } finally {
    saving.value = false
  }
}

// ---------- 状态 ----------
const statusVisible = ref(false)
const statusForm = reactive({ id: 0, status: 1, updateReason: '' })

function openStatus(row: LaboratoryItem): void {
  statusForm.id = row.id
  statusForm.status = row.status === 1 ? 0 : 1
  statusForm.updateReason = ''
  statusVisible.value = true
}

async function handleStatus(): Promise<void> {
  saving.value = true
  try {
    await updateLaboratoryStatus({ ...statusForm })
    ElMessage.success('状态已更新')
    statusVisible.value = false
    void loadPage()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  types.value = await listExperimentTypes({ status: 1 })
  void loadPage()
})
</script>

<style scoped>
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
