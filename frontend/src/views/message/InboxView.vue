<template>
  <div class="page-container">
    <el-card shadow="never" class="filter-card">
      <el-form :model="query" inline @submit.prevent>
        <el-form-item label="类型">
          <el-select v-model="query.type" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="item in MESSAGE_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.isRead" placeholder="全部" clearable style="width: 110px">
            <el-option label="未读" :value="0" />
            <el-option label="已读" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
        <el-form-item>
          <el-button type="success" plain :icon="Check" @click="handleReadAll">全部已读</el-button>
          <el-button type="danger" plain :icon="Delete" @click="handleClear">一键清空</el-button>
          <el-button v-if="canSend" type="primary" :icon="Promotion" @click="openSend">发送系统通知</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table
        v-loading="loading"
        :data="rows"
        border
        stripe
        :row-class-name="rowClass"
        @row-click="openDetail"
      >
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isRead === 0" type="danger" size="small" effect="dark">未读</el-tag>
            <el-tag v-else type="info" size="small">已读</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" type="primary" effect="plain">{{ row.typeName || row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="内容" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="text-secondary">{{ row.content }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="170" align="center" />
        <el-table-column label="操作" width="150" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openDetail(row)">查看</el-button>
            <el-button link type="danger" @click.stop="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="收件箱是空的" />
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

    <el-dialog v-model="detailVisible" :title="detail?.title" width="560px">
      <div v-if="detail" class="detail">
        <div class="detail-meta">
          <el-tag size="small" type="primary" effect="plain">{{ detail.typeName || detail.type }}</el-tag>
          <span class="text-secondary">{{ detail.createdAt }}</span>
        </div>
        <el-divider />
        <p class="detail-content">{{ detail.content }}</p>
        <p v-if="detail.bizType && detail.bizId" class="text-secondary mt-16">
          关联业务：{{ bizLabel(detail.bizType) }} #{{ detail.bizId }}
          <span class="text-secondary">（对应页面随迭代开放）</span>
        </p>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="danger" plain @click="handleDelete(detail!)">删除</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="sendVisible" title="发送系统通知" width="560px" destroy-on-close>
      <el-alert
        class="mb-16"
        type="info"
        show-icon
        :closable="false"
        title="接收人开启「邮件通知」时会同时收到邮件，否则只收站内信"
      />
      <el-form ref="sendFormRef" :model="sendForm" :rules="sendRules" label-width="80px">
        <el-form-item label="接收人" prop="receiverIds">
          <el-select
            v-model="sendForm.receiverIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择接收人（可多选）"
            style="width: 100%"
          >
            <el-option
              v-for="item in receiverOptions"
              :key="item.id"
              :label="`${item.realName}（${item.username}）`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="sendForm.title" maxlength="100" show-word-limit placeholder="如 实验室开放时间调整通知" />
        </el-form-item>
        <el-form-item label="正文" prop="content">
          <el-input
            v-model="sendForm.content"
            type="textarea"
            :rows="5"
            maxlength="1000"
            show-word-limit
            placeholder="请输入通知正文"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sendVisible = false">取消</el-button>
        <el-button type="primary" :loading="sending" @click="handleSend">发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Check, Delete, Promotion, Refresh, Search } from '@element-plus/icons-vue'
import {
  MESSAGE_TYPE_OPTIONS,
  clearMessages,
  deleteMessage,
  getMessageInfo,
  getMessagePage,
  readAllMessages,
  sendSystemMessage,
  type MessageItem,
  type MessageQuery
} from '@/api/message'
import { listUserOption, type UserOption } from '@/api/user'
import { useMessageStore } from '@/stores/message'
import { useUserStore } from '@/stores/user'

const messageStore = useMessageStore()

const loading = ref(false)
const rows = ref<MessageItem[]>([])
const total = ref(0)

const query = reactive<MessageQuery>({
  pageNum: 1,
  pageSize: 10,
  type: null,
  isRead: null
})

const detailVisible = ref(false)
const detail = ref<MessageItem | null>(null)

async function loadPage(): Promise<void> {
  loading.value = true
  try {
    const result = await getMessagePage(query)
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
  query.type = null
  query.isRead = null
  handleSearch()
}

function rowClass({ row }: { row: MessageItem }): string {
  return row.isRead === 0 ? 'row-unread' : ''
}

/** 点开即已读（后端 get_info 会自动置读，这里只需刷新角标） */
async function openDetail(row: MessageItem): Promise<void> {
  const wasUnread = row.isRead === 0
  const result = await getMessageInfo(row.id)
  detail.value = result
  detailVisible.value = true
  if (wasUnread) {
    row.isRead = 1
    messageStore.decrease()
  }
  void messageStore.fetchUnread()
}

async function handleDelete(row: MessageItem): Promise<void> {
  await ElMessageBox.confirm(`确认删除消息「${row.title}」？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await deleteMessage(row.id)
  ElMessage.success('已删除')
  detailVisible.value = false
  void loadPage()
  void messageStore.fetchUnread()
}

async function handleReadAll(): Promise<void> {
  await readAllMessages()
  ElMessage.success('已全部标记为已读')
  void loadPage()
  void messageStore.fetchUnread()
}

async function handleClear(): Promise<void> {
  await ElMessageBox.confirm(
    '一键清空会把收件箱里的所有消息标记为删除（正文保留 30 天供管理员排查），确认继续？',
    '清空确认',
    { type: 'warning', confirmButtonText: '清空', cancelButtonText: '取消' }
  )
  await clearMessages()
  ElMessage.success('收件箱已清空')
  void loadPage()
  void messageStore.fetchUnread()
}

function bizLabel(bizType: string): string {
  const map: Record<string, string> = {
    RESERVATION: '预约单',
    QUEUE: '排队记录',
    REPAIR_ORDER: '报修工单'
  }
  return map[bizType] ?? bizType
}

// ---------- 发送系统通知（仅 message:send） ----------
const userStore = useUserStore()
const canSend = computed(() => userStore.hasPermission('message:send'))

const sendVisible = ref(false)
const sending = ref(false)
const sendFormRef = ref<FormInstance>()
const receiverOptions = ref<UserOption[]>([])

const sendForm = reactive({
  receiverIds: [] as number[],
  title: '',
  content: ''
})

const sendRules: FormRules<typeof sendForm> = {
  receiverIds: [{ required: true, message: '请选择接收人', trigger: 'change' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入正文', trigger: 'blur' }]
}

async function openSend(): Promise<void> {
  sendVisible.value = true
  sendForm.receiverIds = []
  sendForm.title = ''
  sendForm.content = ''
  if (receiverOptions.value.length === 0) {
    receiverOptions.value = await listUserOption({})
  }
}

async function handleSend(): Promise<void> {
  const valid = await sendFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  sending.value = true
  try {
    const count = await sendSystemMessage(sendForm)
    ElMessage.success(`已发送给 ${count} 位用户`)
    sendVisible.value = false
    void loadPage()
  } finally {
    sending.value = false
  }
}

onMounted(() => {
  void loadPage()
  void messageStore.fetchUnread()
})
</script>

<style scoped>
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}

.detail-content {
  margin: 0;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

:deep(.row-unread) {
  font-weight: 600;
}

:deep(.el-table__row) {
  cursor: pointer;
}
</style>
