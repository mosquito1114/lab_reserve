<template>
  <div class="page-container">
    <el-card shadow="never" class="filter-card">
      <el-form :model="query" inline @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="编号 / 名称 / 型号" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="所属实验室">
          <el-select v-model="query.labId" placeholder="全部" clearable filterable style="width: 200px">
            <el-option v-for="lab in labs" :key="lab.id" :label="`${lab.labNo} ${lab.name}`" :value="lab.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="item in EQUIPMENT_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button v-if="canCreate" type="success" :icon="Plus" @click="openCreate">新增设备</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="resourceNo" label="资源编号" width="130" />
        <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="model" label="型号" min-width="140" show-overflow-tooltip />
        <el-table-column label="所属实验室" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.labNo }} {{ row.labName }}</template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="80" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)" effect="dark">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="nextCalibrationDate" label="下次校准" width="120" align="center" />
        <el-table-column label="操作" width="230" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="goAvailability(row)">库存可用性</el-button>
            <el-button v-if="canUpdate" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="canDisable" link type="warning" @click="openStatus(row)">改状态</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有符合条件的设备" />
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
    <el-dialog v-model="dialogVisible" :title="isCreate ? '新增设备' : '编辑设备'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="资源编号" prop="resourceNo">
          <el-input v-model="form.resourceNo" placeholder="格式 ZY-XX-YYY，如 ZY-01-003" :disabled="!isCreate" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如 微型计算机" />
        </el-form-item>
        <el-form-item label="型号">
          <el-input v-model="form.model" placeholder="如 联想 M720q" />
        </el-form-item>
        <el-form-item label="所属实验室" prop="labId">
          <el-select v-model="form.labId" placeholder="请选择" filterable style="width: 100%">
            <el-option v-for="lab in labs" :key="lab.id" :label="`${lab.labNo} ${lab.name}`" :value="lab.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="库存数量" prop="stock">
          <el-input-number v-model="form.stock" :min="1" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item label="下次校准">
          <el-date-picker v-model="form.nextCalibrationDate" type="date" value-format="YYYY-MM-DD" placeholder="可选" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="更新原因" prop="updateReason">
          <el-input v-model="form.updateReason" placeholder="必填，会写入资源更新日志" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="!form.updateReason.trim()" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 状态变更 -->
    <el-dialog v-model="statusVisible" title="变更设备状态" width="460px">
      <el-form label-width="90px">
        <el-form-item label="当前状态">
          <el-tag :type="statusTag(statusForm.current)" effect="dark">{{ currentStatusName }}</el-tag>
        </el-form-item>
        <el-form-item label="新状态" required>
          <el-select v-model="statusForm.status" style="width: 100%">
            <el-option v-for="item in EQUIPMENT_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="更新原因" required>
          <el-input v-model="statusForm.updateReason" placeholder="必填，如 主板故障送修" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="statusVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!statusForm.updateReason.trim()" :loading="saving" @click="handleStatus">确定</el-button>
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
  EQUIPMENT_STATUS_OPTIONS,
  createEquipment,
  getEquipmentPage,
  listLaboratoryOptions,
  updateEquipment,
  updateEquipmentStatus,
  type EquipmentItem,
  type EquipmentPayload,
  type EquipmentQuery,
  type LaboratoryOption
} from '@/api/resource'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const canCreate = computed(() => userStore.hasPermission('resource:equip:create'))
const canUpdate = computed(() => userStore.hasPermission('resource:equip:update'))
const canDisable = computed(() => userStore.hasPermission('resource:equip:disable'))

const loading = ref(false)
const saving = ref(false)
const rows = ref<EquipmentItem[]>([])
const labs = ref<LaboratoryOption[]>([])
const total = ref(0)

const query = reactive<EquipmentQuery>({
  pageNum: 1,
  pageSize: 10,
  keyword: null,
  labId: null,
  status: null
})

function statusTag(status: number): 'success' | 'warning' | 'info' | 'danger' {
  return EQUIPMENT_STATUS_OPTIONS.find((item) => item.value === status)?.tag ?? 'info'
}

async function loadPage(): Promise<void> {
  loading.value = true
  try {
    const result = await getEquipmentPage(query)
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
  query.labId = null
  query.status = null
  handleSearch()
}

function goAvailability(row: EquipmentItem): void {
  void router.push({ path: '/resource/availability', query: { type: 'equip', id: String(row.id) } })
}

// ---------- 表单 ----------
const dialogVisible = ref(false)
const isCreate = ref(true)
const formRef = ref<FormInstance>()

const form = reactive<EquipmentPayload>({
  id: null,
  resourceNo: '',
  name: '',
  model: '',
  stock: 1,
  labId: 0,
  nextCalibrationDate: null,
  remark: '',
  updateReason: ''
})

const rules: FormRules<EquipmentPayload> = {
  resourceNo: [
    { required: true, message: '请输入资源编号', trigger: 'blur' },
    { pattern: /^ZY-\d{2}-\d{3}$/, message: '格式应为 ZY-两位数字-三位数字，如 ZY-01-003', trigger: 'blur' }
  ],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  stock: [{ required: true, message: '请输入库存数量', trigger: 'blur' }],
  labId: [{ required: true, message: '请选择所属实验室', trigger: 'change' }],
  updateReason: [{ required: true, message: '请填写更新原因（资源变更必须留痕）', trigger: 'blur' }]
}

function openCreate(): void {
  isCreate.value = true
  form.id = null
  form.resourceNo = ''
  form.name = ''
  form.model = ''
  form.stock = 1
  form.labId = (labs.value[0]?.id ?? 0) as number
  form.nextCalibrationDate = null
  form.remark = ''
  form.updateReason = ''
  dialogVisible.value = true
}

function openEdit(row: EquipmentItem): void {
  isCreate.value = false
  form.id = row.id
  form.resourceNo = row.resourceNo
  form.name = row.name
  form.model = row.model ?? ''
  form.stock = row.stock
  form.labId = row.labId
  form.nextCalibrationDate = row.nextCalibrationDate
  form.remark = row.remark ?? ''
  form.updateReason = ''
  dialogVisible.value = true
}

async function handleSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  saving.value = true
  try {
    if (isCreate.value) {
      await createEquipment(form)
      ElMessage.success('新增成功')
    } else {
      await updateEquipment(form)
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
const statusForm = reactive({ id: 0, current: 1, status: 1, updateReason: '' })
const currentStatusName = computed(
  () => EQUIPMENT_STATUS_OPTIONS.find((item) => item.value === statusForm.current)?.label ?? '-'
)

function openStatus(row: EquipmentItem): void {
  statusForm.id = row.id
  statusForm.current = row.status
  statusForm.status = row.status
  statusForm.updateReason = ''
  statusVisible.value = true
}

async function handleStatus(): Promise<void> {
  saving.value = true
  try {
    await updateEquipmentStatus({ id: statusForm.id, status: statusForm.status, updateReason: statusForm.updateReason })
    ElMessage.success('状态已更新')
    statusVisible.value = false
    void loadPage()
  } finally {
    saving.value = false
  }
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
