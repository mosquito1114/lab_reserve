<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- ---------------- 实验类型 ---------------- -->
        <el-tab-pane label="实验类型" name="experiment">
          <div class="table-toolbar">
            <span class="text-secondary">实验室归属的实验类型，停用后新增/修改实验室不可选</span>
            <el-button type="success" :icon="Plus" @click="openExperimentCreate">新增实验类型</el-button>
          </div>
          <el-table v-loading="loading" :data="experiments" border stripe>
            <el-table-column prop="code" label="编码" width="160" />
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column prop="sort" label="排序" width="90" align="center" />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'" :effect="row.status === 1 ? 'dark' : 'plain'">
                  {{ row.status === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" align="center">
              <template #default="{ row }">
                <el-button link type="primary" @click="openExperimentEdit(row)">编辑</el-button>
                <el-button link type="danger" @click="handleExperimentDelete(row)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无实验类型" />
            </template>
          </el-table>
        </el-tab-pane>

        <!-- ---------------- 时段 ---------------- -->
        <el-tab-pane label="时段" name="slot">
          <div class="table-toolbar">
            <span class="text-secondary">预约的粒度就是时段，全校统一</span>
            <el-button type="success" :icon="Plus" @click="openSlotCreate">新增时段</el-button>
          </div>
          <el-table v-loading="loading" :data="slots" border stripe>
            <el-table-column prop="code" label="编码" width="120" />
            <el-table-column prop="name" label="名称" min-width="140" />
            <el-table-column label="时间" width="180" align="center">
              <template #default="{ row }">{{ row.startTime }} - {{ row.endTime }}</template>
            </el-table-column>
            <el-table-column prop="sort" label="排序" width="90" align="center" />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'" :effect="row.status === 1 ? 'dark' : 'plain'">
                  {{ row.status === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" align="center">
              <template #default="{ row }">
                <el-button link type="primary" @click="openSlotEdit(row)">编辑</el-button>
                <el-button link type="danger" @click="handleSlotDelete(row)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无时段" />
            </template>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 实验类型弹窗 -->
    <el-dialog v-model="experimentVisible" :title="experimentForm.id ? '编辑实验类型' : '新增实验类型'" width="440px" destroy-on-close>
      <el-form ref="experimentFormRef" :model="experimentForm" :rules="experimentRules" label-width="80px">
        <el-form-item label="编码" prop="code">
          <el-input v-model="experimentForm.code" placeholder="如 VALIDATE" :disabled="!!experimentForm.id" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="experimentForm.name" placeholder="如 验证性" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="experimentForm.sort" :min="0" :max="999" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="experimentForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="experimentVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleExperimentSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 时段弹窗 -->
    <el-dialog v-model="slotVisible" :title="slotForm.id ? '编辑时段' : '新增时段'" width="440px" destroy-on-close>
      <el-form ref="slotFormRef" :model="slotForm" :rules="slotRules" label-width="80px">
        <el-form-item label="编码" prop="code">
          <el-input v-model="slotForm.code" placeholder="如 S2" :disabled="!!slotForm.id" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="slotForm.name" placeholder="如 3-4节" />
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <el-time-picker v-model="slotForm.startTime" format="HH:mm" value-format="HH:mm" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-time-picker v-model="slotForm.endTime" format="HH:mm" value-format="HH:mm" style="width: 100%" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="slotForm.sort" :min="0" :max="999" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="slotForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="slotVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSlotSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  createExperimentType,
  createTimeSlot,
  deleteExperimentType,
  deleteTimeSlot,
  listExperimentTypes,
  listTimeSlots,
  updateExperimentType,
  updateTimeSlot,
  type ExperimentTypeItem,
  type ExperimentTypePayload,
  type TimeSlotItem,
  type TimeSlotPayload
} from '@/api/resource'

const activeTab = ref<'experiment' | 'slot'>('experiment')
const loading = ref(false)
const saving = ref(false)

const experiments = ref<ExperimentTypeItem[]>([])
const slots = ref<TimeSlotItem[]>([])

async function loadAll(): Promise<void> {
  loading.value = true
  try {
    experiments.value = await listExperimentTypes({})
    slots.value = await listTimeSlots({})
  } finally {
    loading.value = false
  }
}

function handleTabChange(): void {
  void loadAll()
}

// ---------- 实验类型 ----------
const experimentVisible = ref(false)
const experimentFormRef = ref<FormInstance>()
const experimentForm = reactive<ExperimentTypePayload>({ id: null, code: '', name: '', sort: 0, status: 1 })

const experimentRules: FormRules<ExperimentTypePayload> = {
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

function openExperimentCreate(): void {
  experimentForm.id = null
  experimentForm.code = ''
  experimentForm.name = ''
  experimentForm.sort = experiments.value.length + 1
  experimentForm.status = 1
  experimentVisible.value = true
}

function openExperimentEdit(row: ExperimentTypeItem): void {
  experimentForm.id = row.id
  experimentForm.code = row.code
  experimentForm.name = row.name
  experimentForm.sort = row.sort
  experimentForm.status = row.status
  experimentVisible.value = true
}

async function handleExperimentSubmit(): Promise<void> {
  const valid = await experimentFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  saving.value = true
  try {
    if (experimentForm.id) {
      await updateExperimentType({ ...experimentForm })
    } else {
      await createExperimentType({ ...experimentForm })
    }
    ElMessage.success('保存成功')
    experimentVisible.value = false
    void loadAll()
  } finally {
    saving.value = false
  }
}

async function handleExperimentDelete(row: ExperimentTypeItem): Promise<void> {
  await ElMessageBox.confirm(`确认删除实验类型「${row.name}」？被实验室或预约引用时不允许删除。`, '删除确认', {
    type: 'warning'
  })
  await deleteExperimentType(row.id)
  ElMessage.success('已删除')
  void loadAll()
}

// ---------- 时段 ----------
const slotVisible = ref(false)
const slotFormRef = ref<FormInstance>()
const slotForm = reactive<TimeSlotPayload>({ id: null, code: '', name: '', startTime: '08:00', endTime: '09:40', sort: 0, status: 1 })

const slotRules: FormRules<TimeSlotPayload> = {
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }]
}

function openSlotCreate(): void {
  slotForm.id = null
  slotForm.code = ''
  slotForm.name = ''
  slotForm.startTime = '08:00'
  slotForm.endTime = '09:40'
  slotForm.sort = slots.value.length + 1
  slotForm.status = 1
  slotVisible.value = true
}

function openSlotEdit(row: TimeSlotItem): void {
  slotForm.id = row.id
  slotForm.code = row.code
  slotForm.name = row.name
  slotForm.startTime = row.startTime.slice(0, 5)
  slotForm.endTime = row.endTime.slice(0, 5)
  slotForm.sort = row.sort
  slotForm.status = row.status
  slotVisible.value = true
}

async function handleSlotSubmit(): Promise<void> {
  const valid = await slotFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  saving.value = true
  try {
    if (slotForm.id) {
      await updateTimeSlot({ ...slotForm })
    } else {
      await createTimeSlot({ ...slotForm })
    }
    ElMessage.success('保存成功')
    slotVisible.value = false
    void loadAll()
  } finally {
    saving.value = false
  }
}

async function handleSlotDelete(row: TimeSlotItem): Promise<void> {
  await ElMessageBox.confirm(`确认删除时段「${row.name}」？被预约引用时不允许删除。`, '删除确认', { type: 'warning' })
  await deleteTimeSlot(row.id)
  ElMessage.success('已删除')
  void loadAll()
}

onMounted(() => {
  void loadAll()
})
</script>

<style scoped>
.table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
</style>
