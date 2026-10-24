<template>
  <div class="page-container">
    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent>
        <el-form-item label="资源类型">
          <el-radio-group v-model="resourceType" :disabled="!isTeacher" @change="handleTypeChange">
            <el-radio-button value="lab">实验室</el-radio-button>
            <el-radio-button value="equip">设备</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="resourceType === 'lab' ? '实验室' : '设备'">
          <el-select
            v-model="resourceId"
            filterable
            clearable
            :placeholder="resourceType === 'lab' ? '请选择实验室' : '请选择设备'"
            style="width: 300px"
            @change="loadSlots"
          >
            <el-option v-for="item in resourceOptions" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="date" type="date" value-format="YYYY-MM-DD" :clearable="false" @change="loadSlots" />
        </el-form-item>
        <el-form-item>
          <el-button :icon="Refresh" @click="loadSlots">刷新时段</el-button>
        </el-form-item>
      </el-form>
      <el-alert v-if="!isTeacher" type="info" :closable="false" show-icon
                title="实验室由教师为班级排课，学生只能预约设备；你的实验课请在「我的实验课表」查看" />
    </el-card>

    <el-card shadow="never" v-loading="loading">
      <template #header>
        <span v-if="resourceLabel">{{ resourceLabel }} · {{ date }}</span>
        <span v-else class="text-secondary">请选择资源与日期</span>
      </template>

      <div v-if="slots.length" class="slot-grid">
        <div v-for="slot in slots" :key="slot.slotId" class="slot-card" :class="{ 'is-full': !slot.available }">
          <div class="slot-head">
            <span class="slot-name">{{ slot.slotName }}</span>
            <el-tag :type="slot.available ? 'success' : 'danger'" size="small" effect="dark">
              {{ slot.available ? '可预约' : '不可约' }}
            </el-tag>
          </div>
          <div class="slot-time">{{ slot.startTime }}-{{ slot.endTime }}</div>
          <div v-if="resourceType === 'lab'" class="slot-reason">{{ slot.reason || '空闲' }}</div>
          <div v-else class="slot-reason">
            剩余 <strong>{{ slot.remainCount ?? 0 }}</strong> / {{ slot.stock ?? '-' }} 台
          </div>
          <div class="slot-queue">
            <span class="text-secondary">排队 {{ slot.queueCount }} 人</span>
            <el-button v-if="slot.available" type="primary" size="small" @click="openSubmit(slot)">预约</el-button>
            <el-button v-else type="warning" size="small" plain @click="openQueue(slot)">排队</el-button>
          </div>
        </div>
      </div>
      <el-empty v-else description="选择资源与日期后展示时段" />
    </el-card>

    <!-- 提交预约 -->
    <el-dialog v-model="submitVisible" title="提交预约" width="640px" destroy-on-close>
      <el-alert class="mb-16" type="info" :closable="false" show-icon
                :title="`${resourceLabel} · ${date} ${currentSlot?.slotName ?? ''}`" />
      <el-form ref="formRef" :model="submitForm" :rules="rules" label-width="110px">
        <el-form-item label="实验类型" prop="experimentTypeId">
          <el-select v-model="submitForm.experimentTypeId" :disabled="resourceType === 'lab'" style="width: 100%">
            <el-option v-for="item in types" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="使用人数" prop="usePeople">
          <el-input-number v-model="submitForm.usePeople" :min="1" :max="1000" controls-position="right" />
        </el-form-item>
        <template v-if="resourceType === 'lab'">
          <el-form-item label="授课课程" prop="courseName">
            <el-input v-model="submitForm.courseName" placeholder="如 操作系统实验" maxlength="100" />
          </el-form-item>
          <el-form-item label="上课班级" prop="classIds">
            <el-select v-model="submitForm.classIds" multiple filterable collapse-tags style="width: 100%"
                       placeholder="至少选择一个班级">
              <el-option v-for="item in classes" :key="item.id" :label="`${item.className}（${item.college}）`" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="附带设备">
            <el-select v-model="attachedIds" multiple filterable collapse-tags style="width: 100%"
                       placeholder="可选，实验室顺带借用的设备">
              <el-option v-for="item in equipments" :key="item.id" :label="`${item.resourceNo} ${item.name}（库存 ${item.stock}）`" :value="item.id" />
            </el-select>
          </el-form-item>
        </template>
        <el-form-item label="实验内容" prop="purpose">
          <el-input v-model="submitForm.purpose" type="textarea" :rows="3" maxlength="500" show-word-limit
                    placeholder="实验内容 / 用途" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="submitVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">提交</el-button>
      </template>
    </el-dialog>

    <!-- 预约结果 / 冲突 -->
    <el-dialog v-model="resultVisible" :title="resultTitle" width="480px">
      <el-result :icon="resultSuccess ? 'success' : 'warning'" :title="resultTitle" :sub-title="resultMessage">
        <template #extra>
          <el-button v-if="resultSuccess" type="primary" @click="goMyReservations">查看我的预约</el-button>
          <el-button v-else-if="queueHintVisible" type="warning" plain @click="joinQueue">加入排队</el-button>
          <el-button v-else @click="resultVisible = false">知道了</el-button>
        </template>
      </el-result>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  getEquipmentAvailability,
  getLaboratoryAvailability,
  getLaboratoryInfo,
  listEquipmentOptions,
  listExperimentTypes,
  listLaboratoryOptions,
  type EquipmentAvailabilitySlot,
  type EquipmentItem,
  type ExperimentTypeItem,
  type LaboratoryOption,
  type SlotAvailability
} from '@/api/resource'
import { getClassList, type ClassItem } from '@/api/class'
import { checkReservation, createReservation, type ReservationSubmitPayload } from '@/api/reservation'
import { createQueue } from '@/api/queue'
import { useUserStore } from '@/stores/user'

interface SlotView {
  slotId: number
  slotName: string
  startTime: string
  endTime: string
  available: boolean
  reason?: string | null
  queueCount: number
  myQueuePosition: number | null
  remainCount?: number
  usedCount?: number
  stock?: number
}

const router = useRouter()
const userStore = useUserStore()
const isTeacher = computed(() => userStore.roleCode === 'TEACHER')

const loading = ref(false)
const resourceType = ref<'lab' | 'equip'>('lab')
const resourceId = ref<number | null>(null)
const date = ref<string>(formatDate(new Date()))
const slots = ref<SlotView[]>([])
const resourceLabel = ref('')

const labOptions = ref<LaboratoryOption[]>([])
const equipOptions = ref<EquipmentItem[]>([])
const types = ref<ExperimentTypeItem[]>([])
const classes = ref<ClassItem[]>([])
const equipments = ref<EquipmentItem[]>([])
const attachedIds = ref<number[]>([])

function formatDate(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

const resourceOptions = computed(() =>
  resourceType.value === 'lab'
    ? labOptions.value.map((item) => ({ id: item.id, label: `${item.labNo} ${item.name}（容量 ${item.capacity}）` }))
    : equipOptions.value.map((item) => ({ id: item.id, label: `${item.resourceNo} ${item.name}（库存 ${item.stock}）` }))
)

function handleTypeChange(): void {
  resourceId.value = null
  slots.value = []
  resourceLabel.value = ''
  const first = resourceOptions.value[0]
  if (first) {
    resourceId.value = first.id
    void loadSlots()
  }
}

async function loadSlots(): Promise<void> {
  if (!resourceId.value) {
    return
  }
  loading.value = true
  try {
    if (resourceType.value === 'lab') {
      const data = await getLaboratoryAvailability({ id: resourceId.value, date: date.value })
      resourceLabel.value = `${data.labNo} ${data.name}`
      slots.value = data.slots.map((item: SlotAvailability) => ({ ...item }))
    } else {
      const data = await getEquipmentAvailability({ id: resourceId.value, date: date.value })
      const stock = data.stock
      resourceLabel.value = `${data.resourceNo} ${data.name}`
      slots.value = data.slots.map((item: EquipmentAvailabilitySlot) => ({ ...item, stock }))
    }
  } finally {
    loading.value = false
  }
}

// ---------- 提交 ----------
const submitVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const currentSlot = ref<SlotView | null>(null)
const submitForm = reactive<ReservationSubmitPayload>({
  resourceType: 1,
  labId: null,
  equipmentId: null,
  reserveDate: date.value,
  slotId: 0,
  usePeople: 30,
  experimentTypeId: 0,
  classIds: [],
  courseName: '',
  purpose: ''
})

const rules: FormRules<ReservationSubmitPayload> = {
  experimentTypeId: [{ required: true, message: '请选择实验类型', trigger: 'change' }],
  usePeople: [{ required: true, message: '请输入使用人数', trigger: 'blur' }],
  purpose: [{ required: true, message: '请填写实验内容', trigger: 'blur' }]
}

async function openSubmit(slot: SlotView): Promise<void> {
  currentSlot.value = slot
  submitForm.resourceType = resourceType.value === 'lab' ? 1 : 2
  submitForm.labId = resourceType.value === 'lab' ? resourceId.value : null
  submitForm.equipmentId = resourceType.value === 'equip' ? resourceId.value : null
  submitForm.reserveDate = date.value
  submitForm.slotId = slot.slotId
  submitForm.classIds = []
  attachedIds.value = []

  if (resourceType.value === 'lab' && resourceId.value) {
    const info = await getLaboratoryInfo(resourceId.value)
    submitForm.experimentTypeId = info.experimentTypeId
    submitForm.usePeople = Math.min(info.capacity, 60)
    equipments.value = await listEquipmentOptions({ labId: resourceId.value })
  } else {
    submitForm.experimentTypeId = types.value[0]?.id ?? 0
    submitForm.usePeople = 1
  }
  submitVisible.value = true
}

async function handleSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (resourceType.value === 'lab') {
    if (!submitForm.courseName) {
      ElMessage.warning('教师预约实验室必须填写授课课程')
      return
    }
    if (!submitForm.classIds || submitForm.classIds.length === 0) {
      ElMessage.warning('请至少选择一个上课班级')
      return
    }
    submitForm.equipments = attachedIds.value.map((id) => ({ equipmentId: id, equipmentCount: 1 }))
  } else {
    submitForm.equipments = []
  }

  submitting.value = true
  try {
    const check = await checkReservation(submitForm)
    if (!check.available) {
      resultSuccess.value = false
      queueHintVisible.value = check.canQueue
      resultTitle.value = '暂时不能提交'
      resultMessage.value = check.conflict
        ? `${check.conflict.message}${check.conflict.queueCount > 0 ? `（当前 ${check.conflict.queueCount} 人排队）` : ''}`
        : '存在冲突'
      resultVisible.value = true
      return
    }
    const created = await createReservation(submitForm)
    submitVisible.value = false
    resultSuccess.value = true
    queueHintVisible.value = false
    resultTitle.value = '提交成功'
    resultMessage.value = `预约号 ${created.reserveNo}，状态：${created.statusName}（等待管理员审核）`
    resultVisible.value = true
    void loadSlots()
  } catch (error) {
    const conflictCode = (error as { data?: { conflictCode?: string; queueCount?: number } }).data?.conflictCode
    resultSuccess.value = false
    queueHintVisible.value = conflictCode === 'SLOT_OCCUPIED' || conflictCode === 'STOCK_NOT_ENOUGH'
    resultTitle.value = '提交失败'
    resultMessage.value = (error as Error).message
    resultVisible.value = true
  } finally {
    submitting.value = false
  }
}

/** 时段已满 → 加入排队（ZADD NX 幂等，重复点只会得到"你已在队列中"） */
async function openQueue(slot: SlotView): Promise<void> {
  currentSlot.value = slot
  queueCountHint.value = slot.queueCount ?? 0
  const tip = slot.reason ? `原因：${slot.reason}` : '该时段已被预约'
  await ElMessageBox.confirm(
    `${tip}；当前已有 ${queueCountHint.value} 人排队。\n排队后如果时段空出，系统会通知你限时确认（超时未确认会进入 7 天禁入）。`,
    '加入排队',
    { type: 'warning', confirmButtonText: '加入排队', cancelButtonText: '取消' }
  ).catch(() => 'cancel')
    .then(async (action) => {
      if (action === 'cancel') {
        return
      }
      if (!resourceId.value) {
        return
      }
      try {
        const created = await createQueue({
          resourceType: resourceType.value === 'lab' ? 1 : 2,
          labId: resourceType.value === 'lab' ? resourceId.value : null,
          equipmentId: resourceType.value === 'equip' ? resourceId.value : null,
          reserveDate: date.value,
          slotId: slot.slotId
        })
        ElMessage.success(`已加入排队：${created.queueNo}，当前第 ${created.position} 位`)
        void router.push('/queue/my')
      } catch (error) {
        ElMessage.warning((error as Error).message)
      }
    })
}

/** 冲突弹窗里的"加入排队" */
function joinQueue(): void {
  resultVisible.value = false
  if (currentSlot.value) {
    void openQueue(currentSlot.value)
  }
}

// ---------- 结果弹窗 ----------
const resultVisible = ref(false)
const resultSuccess = ref(false)
const resultTitle = ref('')
const resultMessage = ref('')
const queueHintVisible = ref(false)
const queueCountHint = ref(0)

function goMyReservations(): void {
  resultVisible.value = false
  void router.push('/reservation/my')
}

onMounted(async () => {
  const [labs, equips, typeList, classList] = await Promise.all([
    listLaboratoryOptions({}),
    listEquipmentOptions({}),
    listExperimentTypes({ status: 1 }),
    getClassList({})
  ])
  labOptions.value = labs
  equipOptions.value = equips
  types.value = typeList
  classes.value = classList

  if (!isTeacher.value) {
    resourceType.value = 'equip'
  }
  const first = resourceOptions.value[0]
  if (first) {
    resourceId.value = first.id
    void loadSlots()
  }
})
</script>

<style scoped>
.slot-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 12px;
}

.slot-card {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 12px;
  background: #fff;
}

.slot-card.is-full {
  background: #fafafa;
}

.slot-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.slot-name {
  font-weight: 600;
}

.slot-time {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-top: 4px;
}

.slot-reason {
  margin-top: 8px;
  font-size: 13px;
}

.slot-queue {
  margin-top: 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
}
</style>
