<template>
  <div class="page-container">
    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent>
        <el-form-item label="范围">
          <el-switch v-model="activeOnly" active-text="只看生效中" inactive-text="全部记录" @change="load" />
        </el-form-item>
        <el-form-item>
          <el-button :icon="Refresh" @click="load">刷新</el-button>
        </el-form-item>
      </el-form>
      <el-alert type="warning" :closable="false" show-icon
                title="候补超时未确认或主动放弃候补会进入 7 天禁入；禁入期间不能对同一资源排队（取消排队不会产生禁入）" />
    </el-card>

    <el-card shadow="never" v-loading="loading">
      <el-table :data="rows" border stripe>
        <el-table-column prop="resourceName" label="资源" min-width="200" show-overflow-tooltip />
        <el-table-column prop="reason" label="原因" min-width="160" />
        <el-table-column prop="startTime" label="开始时间" width="170" align="center" />
        <el-table-column prop="endTime" label="解除时间" width="170" align="center" />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.active ? 'danger' : 'info'" :effect="row.active ? 'dark' : 'plain'">
              {{ row.active ? '禁入中' : '已解除' }}
            </el-tag>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有禁入记录" />
        </template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getMyQueueBanList, type QueueBanItem } from '@/api/queue'

const loading = ref(false)
const rows = ref<QueueBanItem[]>([])
const activeOnly = ref(true)

async function load(): Promise<void> {
  loading.value = true
  try {
    rows.value = await getMyQueueBanList(activeOnly.value)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
</script>
