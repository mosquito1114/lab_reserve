<template>
  <div class="page-container">
    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent>
        <el-form-item label="日期范围">
          <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD"
                          start-placeholder="开始" end-placeholder="结束" style="width: 260px" />
        </el-form-item>
        <el-form-item label="实验室">
          <el-select v-model="labId" placeholder="全部" clearable style="width: 220px">
            <el-option v-for="item in labs" :key="item.id" :label="`${item.labNo} ${item.name}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="load">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" v-loading="loading">
      <template #header>
        <span>我的实验课表（按班级反查已通过的实验课，共 {{ groups.length }} 天 / {{ items.length }} 节）</span>
      </template>
      <div v-if="groups.length">
        <div v-for="group in groups" :key="group.date" class="day-block">
          <div class="day-title">
            {{ group.date }}
            <span class="text-secondary">{{ weekdayName(group.date) }}</span>
          </div>
          <div class="card-grid">
            <div v-for="item in group.items" :key="item.reservationId" class="course-card">
              <div class="course-head">
                <span class="course-name">{{ item.courseName || '实验课' }}</span>
                <el-tag size="small" type="success" effect="dark">{{ item.slotName }}</el-tag>
              </div>
              <div class="course-meta">{{ item.startTime }}-{{ item.endTime }}</div>
              <div class="course-meta">{{ item.labNo }} {{ item.labName }} · {{ item.labLocation }}</div>
              <div class="course-meta">任课教师：{{ item.teacherName || '—' }}</div>
            </div>
          </div>
        </div>
      </div>
      <el-empty v-else description="这段时间没有实验课（实验室由教师为你所在的班级排课）" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { getMyTimetable, type TimetableItem } from '@/api/reservation'
import { listLaboratoryOptions, type LaboratoryOption } from '@/api/resource'

const loading = ref(false)
const items = ref<TimetableItem[]>([])
const labs = ref<LaboratoryOption[]>([])
const labId = ref<number | null>(null)
const dateRange = ref<[string, string] | null>([formatDate(-14), formatDate(30)])

function formatDate(offsetDays: number): string {
  const d = new Date()
  d.setDate(d.getDate() + offsetDays)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

const WEEKDAYS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

function weekdayName(date: string): string {
  const d = new Date(date)
  return WEEKDAYS[(d.getDay() + 6) % 7] ?? ''
}

const groups = computed(() => {
  const map = new Map<string, TimetableItem[]>()
  for (const item of items.value) {
    const list = map.get(item.reserveDate) ?? []
    list.push(item)
    map.set(item.reserveDate, list)
  }
  return Array.from(map.entries())
    .sort((a, b) => a[0].localeCompare(b[0]))
    .map(([date, list]) => ({ date, items: list }))
})

async function load(): Promise<void> {
  loading.value = true
  try {
    items.value = await getMyTimetable({
      beginDate: dateRange.value?.[0] ?? null,
      endDate: dateRange.value?.[1] ?? null,
      labId: labId.value
    })
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  labs.value = await listLaboratoryOptions({})
  await load()
})
</script>

<style scoped>
.day-block {
  margin-bottom: 20px;
}

.day-title {
  font-weight: 600;
  margin-bottom: 10px;
  padding-left: 8px;
  border-left: 3px solid var(--el-color-primary);
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}

.course-card {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 12px;
  background: #fff;
}

.course-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.course-name {
  font-weight: 600;
}

.course-meta {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-top: 6px;
}
</style>
