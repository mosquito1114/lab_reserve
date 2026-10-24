<template>
  <el-dialog :model-value="modelValue" title="预约详情" width="720px" @update:model-value="emit('update:modelValue', $event)">
    <div v-if="detail" v-loading="loading">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="预约号">{{ detail.reserveNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="statusTag(detail.status)" effect="dark">{{ detail.statusName }}</el-tag>
          <el-tag v-if="detail.fromQueue === 1" size="small" type="warning" style="margin-left: 6px">排队转正</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="资源">{{ detail.resourceName }}</el-descriptions-item>
        <el-descriptions-item label="位置">{{ detail.labLocation || '—' }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ detail.reserveDate }}</el-descriptions-item>
        <el-descriptions-item label="时段">{{ detail.slotName }}（{{ detail.startTime }}-{{ detail.endTime }}）</el-descriptions-item>
        <el-descriptions-item label="申请教师">{{ detail.applicantName }}（{{ detail.applicantCollege || '—' }}）</el-descriptions-item>
        <el-descriptions-item label="使用人数">{{ detail.usePeople }}</el-descriptions-item>
        <el-descriptions-item label="实验类型">{{ detail.experimentTypeName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="授课课程">{{ detail.courseName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="实验内容" :span="2">{{ detail.purpose }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.auditUserName" label="审核人">{{ detail.auditUserName }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.auditTime" label="审核时间">{{ detail.auditTime }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.rejectReason" label="驳回 / 取消原因" :span="2">
          <span class="text-danger">{{ detail.rejectReason }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <template v-if="detail.equipments && detail.equipments.length > 0">
        <el-divider content-position="left">附带设备</el-divider>
        <el-table :data="detail.equipments" border size="small">
          <el-table-column prop="resourceNo" label="编号" width="130" />
          <el-table-column prop="name" label="名称" min-width="140" />
          <el-table-column prop="model" label="型号" min-width="120" />
          <el-table-column prop="equipmentCount" label="数量" width="80" align="center" />
        </el-table>
      </template>

      <template v-if="detail.changePending">
        <el-divider content-position="left">调整申请（审批中）</el-divider>
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          :title="`${detail.changePending.changeTypeName}：${describeChange(detail.changePending)}`"
          :description="`申请原因：${detail.changePending.reason}（审批通过前，预约单的业务字段不会改变）`"
        />
      </template>

      <template v-if="changes.length > 0">
        <el-divider content-position="left">调整历史</el-divider>
        <el-timeline>
          <el-timeline-item
            v-for="item in changes"
            :key="item.id"
            :timestamp="item.createdAt"
            :type="item.status === 1 ? 'success' : item.status === 2 ? 'danger' : 'primary'"
          >
            <strong>{{ item.changeTypeName }}</strong>
            <span class="text-secondary"> · {{ item.summary }}</span>
            <div class="text-secondary">原因：{{ item.reason }}</div>
            <div v-if="item.auditOpinion" class="text-secondary">审批意见：{{ item.auditOpinion }}</div>
            <el-tag size="small" :type="item.status === 1 ? 'success' : item.status === 2 ? 'danger' : 'warning'">
              {{ item.statusName }}
            </el-tag>
          </el-timeline-item>
        </el-timeline>
      </template>
    </div>
    <template #footer>
      <slot name="footer">
        <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      </slot>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import {
  getReservationInfo,
  listChangesByReservation,
  RESERVATION_STATUS_OPTIONS,
  type ReservationChangeItem,
  type ReservationDetail
} from '@/api/reservation'

const props = defineProps<{ modelValue: boolean; reservationId: number | null }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void }>()

const loading = ref(false)
const detail = ref<ReservationDetail | null>(null)
const changes = ref<ReservationChangeItem[]>([])

function statusTag(status: string): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  return (RESERVATION_STATUS_OPTIONS.find((item) => item.value === status)?.tag ?? 'info') as
    | 'success'
    | 'warning'
    | 'danger'
    | 'info'
    | 'primary'
}

function describeChange(item: ReservationChangeItem): string {
  return item.summary || ''
}

async function load(): Promise<void> {
  if (!props.reservationId) {
    return
  }
  loading.value = true
  try {
    detail.value = await getReservationInfo(props.reservationId)
    changes.value = await listChangesByReservation(props.reservationId)
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.modelValue, props.reservationId],
  ([visible]) => {
    if (visible) {
      void load()
    }
  }
)
</script>
