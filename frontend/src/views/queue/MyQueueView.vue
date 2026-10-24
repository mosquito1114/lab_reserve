<template>
  <div class="page-container">
    <el-card shadow="never" class="filter-card">
      <el-form :model="query" inline @submit.prevent>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="item in QUEUE_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" show-icon
                title="顺位实时计算：每 10 秒自动刷新；收到候补通知后请在倒计时结束前确认，超时会进入 7 天排队禁入" />
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="queueNo" label="排队号" width="190" />
        <el-table-column prop="resourceName" label="资源" min-width="180" show-overflow-tooltip />
        <el-table-column label="时间" width="190" align="center">
          <template #default="{ row }">{{ row.reserveDate }} {{ row.slotName }}</template>
        </el-table-column>
        <el-table-column label="顺位" width="140" align="center">
          <template #default="{ row }">
            <span v-if="row.status === 'NOTIFIED'" class="countdown">{{ countdown(row) }}</span>
            <span v-else-if="row.position">第 {{ row.position }} 位 / {{ row.totalCount }} 人</span>
            <span v-else class="text-secondary">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="queueStatusTag(row.status)" effect="dark">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="joinTime" label="入队时间" width="170" align="center" />
        <el-table-column label="操作" width="230" align="center">
          <template #default="{ row }">
            <template v-if="row.status === 'QUEUING'">
              <el-button link type="danger" @click="handleCancel(row)">取消排队</el-button>
            </template>
            <template v-else-if="row.status === 'NOTIFIED'">
              <el-button link type="success" @click="handleConfirm(row)">确认候补</el-button>
              <el-button link type="danger" @click="handleAbandon(row)">放弃</el-button>
            </template>
            <el-button link type="primary" @click="openNotices(row)">通知历史</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有排队记录（时段约满时可在「预约申请」页加入排队）" />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        class="pagination"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :page-sizes="[10, 20, 50]"
        @current-change="loadPage"
        @size-change="handleSearch"
      />
    </el-card>

    <el-dialog v-model="noticeVisible" title="候补通知历史" width="620px">
      <el-table :data="notices" border size="small">
        <el-table-column prop="seq" label="第几次" width="80" align="center" />
        <el-table-column prop="noticeTime" label="通知时间" width="170" align="center" />
        <el-table-column prop="deadline" label="确认截止" width="170" align="center" />
        <el-table-column prop="channelName" label="通道" width="110" align="center" />
        <el-table-column prop="actionName" label="响应" width="100" align="center" />
        <el-table-column prop="actionTime" label="响应时间" min-width="160" align="center" />
        <template #empty>
          <el-empty description="还没有候补通知" />
        </template>
      </el-table>
      <template #footer>
        <el-button @click="noticeVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import {
  QUEUE_STATUS_OPTIONS,
  abandonQueue,
  cancelQueue,
  confirmQueue,
  getMyQueuePage,
  getQueueNoticeList,
  queueStatusTag,
  type QueueItem,
  type QueueNoticeItem,
  type QueueQuery
} from '@/api/queue'

const loading = ref(false)
const rows = ref<QueueItem[]>([])
const total = ref(0)
const now = ref(Date.now())
let timer: number | undefined

const query = reactive<QueueQuery>({ pageNum: 1, pageSize: 10, status: null })

async function loadPage(): Promise<void> {
  loading.value = true
  try {
    const result = await getMyQueuePage(query)
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
  query.status = null
  handleSearch()
}

function countdown(row: QueueItem): string {
  if (!row.deadline) {
    return '待确认'
  }
  const remain = Math.floor((new Date(row.deadline).getTime() - now.value) / 1000)
  if (remain <= 0) {
    return '已超时，等待系统处理'
  }
  const m = Math.floor(remain / 60)
  const s = remain % 60
  return `请在 ${m}:${String(s).padStart(2, '0')} 内确认`
}

async function handleCancel(row: QueueItem): Promise<void> {
  await ElMessageBox.confirm(`确认取消排队「${row.queueNo}」？取消不会产生禁入记录。`, '取消排队', { type: 'warning' })
  await cancelQueue(row.id)
  ElMessage.success('已取消排队')
  void loadPage()
}

async function handleConfirm(row: QueueItem): Promise<void> {
  await ElMessageBox.confirm('确认后系统会直接为你生成已通过的预约单，是否继续？', '确认候补', { type: 'success' })
  const result = await confirmQueue(row.id)
  ElMessage.success(`确认成功：${result.reserveNo}`)
  void loadPage()
}

async function handleAbandon(row: QueueItem): Promise<void> {
  const { value } = await ElMessageBox.prompt('放弃后会进入 7 天排队禁入，请填写原因', '放弃候补', {
    type: 'warning',
    inputPlaceholder: '如 临时有事'
  })
  await abandonQueue({ id: row.id, reason: value })
  ElMessage.warning('已放弃候补，7 天内不能排队该资源')
  void loadPage()
}

const noticeVisible = ref(false)
const notices = ref<QueueNoticeItem[]>([])

async function openNotices(row: QueueItem): Promise<void> {
  notices.value = await getQueueNoticeList(row.id)
  noticeVisible.value = true
}

onMounted(() => {
  void loadPage()
  timer = window.setInterval(() => {
    now.value = Date.now()
    if (!document.hidden) {
      void loadPage()
    }
  }, 10_000)
})

onBeforeUnmount(() => {
  if (timer) {
    window.clearInterval(timer)
    timer = undefined
  }
})
</script>

<style scoped>
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.countdown {
  color: var(--el-color-warning);
  font-weight: 600;
}
</style>
