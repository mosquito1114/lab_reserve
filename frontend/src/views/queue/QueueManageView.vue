<template>
  <div class="page-container">
    <el-card shadow="never" class="filter-card">
      <el-form :model="form" inline @submit.prevent>
        <el-form-item label="资源类型">
          <el-radio-group v-model="form.resourceType" @change="handleResourceTypeChange">
            <el-radio-button :value="1">实验室</el-radio-button>
            <el-radio-button :value="2">设备</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="form.resourceType === 1 ? '实验室' : '设备'">
          <el-select v-model="form.resourceId" filterable placeholder="请选择" style="width: 260px" @change="handleSlotChange">
            <el-option v-for="item in resourceOptions" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="form.date" type="date" value-format="YYYY-MM-DD" :clearable="false" @change="handleSlotChange" />
        </el-form-item>
        <el-form-item label="时段">
          <el-select v-model="form.slotId" placeholder="请选择" style="width: 160px" @change="loadQueue">
            <el-option v-for="slot in slots" :key="slot.slotId" :label="slot.slotName" :value="slot.slotId" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button :icon="Refresh" @click="loadQueue">刷新队列</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <span>队列 · {{ resourceLabel || '未选择资源' }} {{ form.date }}（共 {{ rows.length }} 人）</span>
      </template>
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column label="顺位" width="80" align="center">
          <template #default="{ row }">{{ row.position ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="queueNo" label="排队号" width="190" />
        <el-table-column prop="applicantName" label="申请人" width="120" />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="queueStatusTag(row.status)" effect="dark">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="joinTime" label="入队时间" width="180" align="center" />
        <el-table-column label="确认截止" min-width="180" align="center">
          <template #default="{ row }">{{ row.deadline || '—' }}</template>
        </el-table-column>
        <template #empty>
          <el-empty description="该时段还没有人排队" />
        </template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getQueuePage, queueStatusTag, type QueueItem } from '@/api/queue'
import {
  getEquipmentAvailability,
  getLaboratoryAvailability,
  listEquipmentOptions,
  listLaboratoryOptions,
  type EquipmentItem,
  type LaboratoryOption
} from '@/api/resource'

const loading = ref(false)
const rows = ref<QueueItem[]>([])
const labs = ref<LaboratoryOption[]>([])
const equipments = ref<EquipmentItem[]>([])
const slots = ref<{ slotId: number; slotName: string }[]>([])
const resourceLabel = ref('')

const today = new Date()
const form = reactive<{ resourceType: number; resourceId: number | null; date: string; slotId: number | null }>({
  resourceType: 1,
  resourceId: null,
  date: formatDate(today),
  slotId: null
})

function formatDate(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

const resourceOptions = computed(() =>
  form.resourceType === 1
    ? labs.value.map((item) => ({ id: item.id, label: `${item.labNo} ${item.name}` }))
    : equipments.value.map((item) => ({ id: item.id, label: `${item.resourceNo} ${item.name}` }))
)

function handleResourceTypeChange(): void {
  form.resourceId = null
  form.slotId = null
  rows.value = []
  const first = resourceOptions.value[0]
  if (first) {
    form.resourceId = first.id
    void handleSlotChange()
  }
}

async function handleSlotChange(): Promise<void> {
  if (!form.resourceId || !form.date) {
    return
  }
  if (form.resourceType === 1) {
    const data = await getLaboratoryAvailability({ id: form.resourceId, date: form.date })
    resourceLabel.value = `${data.labNo} ${data.name}`
    slots.value = data.slots.map((item) => ({ slotId: item.slotId, slotName: item.slotName }))
  } else {
    const data = await getEquipmentAvailability({ id: form.resourceId, date: form.date })
    resourceLabel.value = `${data.resourceNo} ${data.name}`
    slots.value = data.slots.map((item) => ({ slotId: item.slotId, slotName: item.slotName }))
  }
  if (!form.slotId || !slots.value.some((item) => item.slotId === form.slotId)) {
    form.slotId = slots.value[0]?.slotId ?? null
  }
  await loadQueue()
}

async function loadQueue(): Promise<void> {
  if (!form.resourceId || !form.slotId || !form.date) {
    return
  }
  loading.value = true
  try {
    const result = await getQueuePage({
      pageNum: 1,
      pageSize: 50,
      resourceType: form.resourceType,
      labId: form.resourceType === 1 ? form.resourceId : null,
      equipmentId: form.resourceType === 2 ? form.resourceId : null,
      reserveDate: form.date,
      slotId: form.slotId
    })
    rows.value = result.records
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  const [labList, equipList] = await Promise.all([listLaboratoryOptions({}), listEquipmentOptions({})])
  labs.value = labList
  equipments.value = equipList
  const first = resourceOptions.value[0]
  if (first) {
    form.resourceId = first.id
    await handleSlotChange()
  }
})
</script>
