<template>
  <div class="page-container">
    <el-card shadow="never" class="filter-card">
      <el-form :model="form" inline @submit.prevent>
        <el-form-item label="资源类型">
          <el-radio-group v-model="resourceType" @change="handleTypeChange">
            <el-radio-button value="lab">实验室</el-radio-button>
            <el-radio-button value="equip">设备</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="resourceType === 'lab' ? '实验室' : '设备'">
          <el-select
            v-model="form.id"
            :placeholder="resourceType === 'lab' ? '请选择实验室' : '请选择设备'"
            filterable
            clearable
            style="width: 280px"
            @change="loadAvailability"
          >
            <el-option
              v-for="item in resourceOptions"
              :key="item.id"
              :label="item.label"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="form.date" type="date" value-format="YYYY-MM-DD" :clearable="false" @change="loadAvailability" />
        </el-form-item>
        <el-form-item>
          <el-button :icon="Refresh" @click="loadAvailability">刷新</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" v-loading="loading">
      <template #header>
        <div class="card-header">
          <span v-if="labData">{{ labData.labNo }} {{ labData.name }} · {{ labData.date }}</span>
          <span v-else-if="equipData">{{ equipData.resourceNo }} {{ equipData.name }} · {{ equipData.date }}</span>
          <span v-else class="text-secondary">请选择资源与日期</span>
          <span v-if="labData" class="text-secondary">开放时间 {{ labData.openTimeText }}</span>
          <span v-if="equipData" class="text-secondary">库存 {{ equipData.stock }}</span>
        </div>
      </template>

      <el-alert
        v-if="labData && !labData.openWeekday"
        class="mb-16"
        type="warning"
        show-icon
        :closable="false"
        title="该日期不在此实验室的开放星期内"
      />

      <div v-if="slots.length > 0" class="slot-grid">
        <div v-for="slot in slots" :key="slot.slotId" class="slot-card" :class="{ 'is-full': !slot.available }">
          <div class="slot-head">
            <span class="slot-name">{{ slot.slotName }}</span>
            <el-tag :type="slot.available ? 'success' : 'danger'" size="small" effect="dark">
              {{ slot.available ? '可用' : '不可用' }}
            </el-tag>
          </div>
          <div class="slot-time">{{ slot.startTime }}-{{ slot.endTime }}</div>

          <template v-if="resourceType === 'lab'">
            <div v-if="slot.reason" class="slot-reason">{{ slot.reason }}</div>
            <div v-else class="slot-reason text-secondary">可预约</div>
          </template>
          <template v-else>
            <div class="slot-stock">
              剩余 <strong>{{ slot.remainCount ?? 0 }}</strong> / {{ equipData?.stock }}
              <span class="text-secondary">（已借 {{ slot.usedCount ?? 0 }}）</span>
            </div>
          </template>

          <div class="slot-queue">
            <span class="text-secondary">排队 {{ slot.queueCount }} 人</span>
            <el-tag v-if="slot.myQueuePosition" type="warning" size="small">我排第 {{ slot.myQueuePosition }} 位</el-tag>
          </div>
        </div>
      </div>
      <el-empty v-else description="选择资源与日期后展示各时段可用情况" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import {
  getEquipmentAvailability,
  getLaboratoryAvailability,
  listEquipmentOptions,
  listLaboratoryOptions,
  type EquipmentAvailability,
  type EquipmentAvailabilitySlot,
  type EquipmentItem,
  type LaboratoryAvailability,
  type LaboratoryOption,
  type SlotAvailability
} from '@/api/resource'

/** 实验室与设备的时段结构合并成一个视图模型，模板里只认这一份字段 */
interface SlotView {
  slotId: number
  slotName: string
  startTime: string
  endTime: string
  available: boolean
  queueCount: number
  myQueuePosition: number | null
  /** 实验室：不可用原因 */
  reason?: string | null
  /** 设备：已借 / 剩余 */
  usedCount?: number
  remainCount?: number
}

interface ResourceOption {
  id: number
  label: string
}

const route = useRoute()
const loading = ref(false)
const resourceType = ref<'lab' | 'equip'>('lab')
const labOptions = ref<LaboratoryOption[]>([])
const equipOptions = ref<EquipmentItem[]>([])

const labData = ref<LaboratoryAvailability | null>(null)
const equipData = ref<EquipmentAvailability | null>(null)

const today = new Date()
const form = reactive<{ id: number | null; date: string }>({
  id: null,
  date: formatDate(today)
})

function formatDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const resourceOptions = computed<ResourceOption[]>(() =>
  resourceType.value === 'lab'
    ? labOptions.value.map((item) => ({ id: item.id, label: `${item.labNo} ${item.name}` }))
    : equipOptions.value.map((item) => ({ id: item.id, label: `${item.resourceNo} ${item.name}` }))
)

const slots = computed<SlotView[]>(() => {
  if (resourceType.value === 'lab') {
    return (labData.value?.slots ?? []).map((item: SlotAvailability) => ({ ...item }))
  }
  return (equipData.value?.slots ?? []).map((item: EquipmentAvailabilitySlot) => ({ ...item }))
})

function handleTypeChange(): void {
  form.id = null
  labData.value = null
  equipData.value = null
  const first = resourceType.value === 'lab' ? labOptions.value[0] : equipOptions.value[0]
  if (first) {
    form.id = first.id
    void loadAvailability()
  }
}

async function loadAvailability(): Promise<void> {
  if (!form.id || !form.date) {
    return
  }
  loading.value = true
  try {
    if (resourceType.value === 'lab') {
      equipData.value = null
      labData.value = await getLaboratoryAvailability({ id: form.id, date: form.date })
    } else {
      labData.value = null
      equipData.value = await getEquipmentAvailability({ id: form.id, date: form.date })
    }
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  const [labs, equips] = await Promise.all([listLaboratoryOptions({}), listEquipmentOptions({})])
  labOptions.value = labs
  equipOptions.value = equips

  // 支持从实验室/设备列表点「可用时段」带参跳转
  const queryType = route.query.type as string | undefined
  const queryId = Number(route.query.id)
  if (queryType === 'equip' && queryId) {
    resourceType.value = 'equip'
    form.id = queryId
  } else if (queryType === 'lab' && queryId) {
    resourceType.value = 'lab'
    form.id = queryId
  } else if (labs.length > 0) {
    form.id = labs[0].id
  }
  void loadAvailability()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.slot-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
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

.slot-stock {
  margin-top: 8px;
  font-size: 13px;
}

.slot-queue {
  margin-top: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
}
</style>
