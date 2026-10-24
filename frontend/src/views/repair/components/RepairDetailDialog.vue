<template>
  <el-dialog :model-value="modelValue" title="工单详情" width="760px" @update:model-value="emit('update:modelValue', $event)">
    <div v-if="detail" v-loading="loading">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="工单号">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="repairStatusTag(detail.status)" effect="dark">{{ detail.statusName }}</el-tag>
          <el-tag size="small" :type="repairPriorityTag(detail.priority)" style="margin-left: 6px">
            {{ detail.priorityName }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="设备">{{ detail.equipmentNo }} {{ detail.equipmentName }}</el-descriptions-item>
        <el-descriptions-item label="所属实验室">{{ detail.labNo || '—' }}</el-descriptions-item>
        <el-descriptions-item label="报修人">{{ detail.submitterName }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ detail.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="故障现象" :span="2">{{ detail.faultDesc }}</el-descriptions-item>
        <el-descriptions-item label="发生场景" :span="2">{{ detail.faultScene }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.reviewerName" label="复核人">{{ detail.reviewerName }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.reviewTime" label="复核时间">{{ detail.reviewTime }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.reviewOpinion" label="复核意见" :span="2">{{ detail.reviewOpinion }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.assigneeName" label="维修人">{{ detail.assigneeName }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.assignTime" label="派单时间">{{ detail.assignTime }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.acceptTime" label="验收时间">{{ detail.acceptTime }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.archiveTime" label="归档时间">{{ detail.archiveTime }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.acceptOpinion" label="验收意见" :span="2">{{ detail.acceptOpinion }}</el-descriptions-item>
      </el-descriptions>

      <template v-if="detail.faultImages && detail.faultImages.length">
        <el-divider content-position="left">故障截图</el-divider>
        <div class="image-row">
          <el-image
            v-for="url in detail.faultImages"
            :key="url"
            :src="url"
            :preview-src-list="detail.faultImages"
            fit="cover"
            class="image-thumb"
            preview-teleported
          />
        </div>
      </template>

      <el-divider content-position="left">维修时间线</el-divider>
      <el-timeline v-if="detail.records.length">
        <el-timeline-item
          v-for="record in detail.records"
          :key="record.id"
          :timestamp="record.createdAt"
          :type="record.status === 2 ? 'success' : 'primary'"
        >
          <strong>{{ record.maintainerName }}</strong>
          <el-tag size="small" :type="record.status === 2 ? 'success' : 'warning'" style="margin-left: 8px">
            {{ record.statusName }}
          </el-tag>
          <div class="record-text">{{ record.repairDesc || '—' }}</div>
          <div v-if="record.partsReplaced" class="text-secondary">配件：{{ record.partsReplaced }}</div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="还没有维修记录" :image-size="60" />
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
  getRepairInfo,
  repairPriorityTag,
  repairStatusTag,
  type RepairOrderDetail
} from '@/api/repair'

const props = defineProps<{ modelValue: boolean; orderId: number | null }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void }>()

const loading = ref(false)
const detail = ref<RepairOrderDetail | null>(null)

async function load(): Promise<void> {
  if (!props.orderId) {
    return
  }
  loading.value = true
  try {
    detail.value = await getRepairInfo(props.orderId)
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.modelValue, props.orderId],
  ([visible]) => {
    if (visible) {
      void load()
    }
  }
)
</script>

<style scoped>
.image-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.image-thumb {
  width: 120px;
  height: 90px;
  border-radius: 6px;
  border: 1px solid var(--el-border-color-light);
}

.record-text {
  margin-top: 4px;
  font-size: 13px;
}
</style>
