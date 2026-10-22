<template>
  <div class="page-container">
    <el-card shadow="never" class="filter-card">
      <el-form :model="query" inline @submit.prevent>
        <el-form-item label="邮件状态">
          <el-select v-model="query.emailState" placeholder="全部" clearable style="width: 130px">
            <el-option label="投递中" value="SENDING" />
            <el-option label="发送成功" value="OK" />
            <el-option label="死信" value="DEAD" />
          </el-select>
        </el-form-item>
        <el-form-item label="收件人">
          <el-input v-model="query.receiverKeyword" placeholder="姓名 / 账号" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="时间">
          <el-date-picker
            v-model="range"
            type="datetimerange"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 340px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="!smtpHintDismissed"
        class="mb-16"
        type="info"
        show-icon
        :closable="true"
        title="这里只记录「开启了邮件通知」的消息。状态说明：投递中 = 已入队未发出；发送成功 = SMTP 已接收；死信 = 重试次数用尽。"
        @close="smtpHintDismissed = true"
      />
    </el-card>

    <el-card shadow="never">
      <div class="table-toolbar">
        <span class="text-secondary">共 {{ total }} 条投递记录</span>
        <div>
          <el-input v-model="testEmail" placeholder="收件邮箱，用于发测试邮件" clearable style="width: 240px" />
          <el-button type="primary" plain :loading="sending" style="margin-left: 8px" @click="handleSendTest">
            发送测试邮件
          </el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="messageId" label="消息 ID" width="90" align="center" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="收件人" min-width="170">
          <template #default="{ row }">
            <span>{{ row.receiverName || '-' }}</span>
            <span class="text-secondary"> / {{ row.receiverUsername || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="receiverEmail" label="邮箱（脱敏）" min-width="160" />
        <el-table-column label="邮件状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.emailState === 'OK'" type="success" size="small" effect="dark">发送成功</el-tag>
            <el-tag v-else-if="row.emailState === 'DEAD'" type="danger" size="small" effect="dark">死信</el-tag>
            <el-tag v-else-if="row.emailState === 'SENDING'" type="warning" size="small" effect="dark">投递中</el-tag>
            <el-tag v-else type="info" size="small">未入队</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="失败原因" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.emailLastError" class="text-danger">{{ row.emailLastError }}</span>
            <span v-else class="text-secondary">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" align="center" />
        <el-table-column prop="updatedAt" label="最后更新" width="170" align="center" />
        <template #empty>
          <el-empty description="还没有邮件投递记录" />
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import {
  getDeliveryLogPage,
  sendTestEmail,
  type DeliveryLogItem,
  type DeliveryLogQuery
} from '@/api/message'

const loading = ref(false)
const rows = ref<DeliveryLogItem[]>([])
const total = ref(0)

const smtpHintDismissed = ref(false)
const testEmail = ref('')
const sending = ref(false)

const range = ref<[string, string] | null>(null)

const query = reactive<DeliveryLogQuery>({
  pageNum: 1,
  pageSize: 10,
  emailState: null,
  receiverKeyword: null,
  beginTime: null,
  endTime: null
})

async function loadPage(): Promise<void> {
  loading.value = true
  try {
    query.beginTime = range.value?.[0] ?? null
    query.endTime = range.value?.[1] ?? null
    const result = await getDeliveryLogPage(query)
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
  query.emailState = null
  query.receiverKeyword = null
  range.value = null
  handleSearch()
}

async function handleSendTest(): Promise<void> {
  const to = testEmail.value.trim()
  if (!to) {
    ElMessage.warning('请先填写收件邮箱')
    return
  }
  sending.value = true
  try {
    await sendTestEmail(to)
    ElMessage.success('测试邮件已发送，请到收件箱确认')
  } finally {
    sending.value = false
  }
}

onMounted(() => {
  void loadPage()
})
</script>

<style scoped>
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
