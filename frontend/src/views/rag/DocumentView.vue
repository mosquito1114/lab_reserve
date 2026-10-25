<template>
  <div class="page-container">
    <!-- ==================== 筛选 + 工具栏 ==================== -->
    <el-card shadow="never" class="filter-card">
      <el-form :model="query" inline @submit.prevent>
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            placeholder="文档标题"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="query.category" placeholder="全部" clearable style="width: 130px">
            <el-option
              v-for="item in DOC_CATEGORY_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.pipelineState" placeholder="全部" clearable style="width: 130px">
            <el-option
              v-for="(label, code) in PIPELINE_STATE_LABEL"
              :key="code"
              :label="label"
              :value="code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="启停">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 110px">
            <el-option label="已启用" :value="1" />
            <el-option label="已停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Upload" @click="openUpload">上传文档</el-button>
          <el-button :icon="RefreshRight" :loading="loading" @click="loadPage">刷新</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- ==================== 列表 ==================== -->
    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" border stripe :row-class-name="rowClass">
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="类别" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ categoryLabel(row.category) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sourceType" label="格式" width="80" align="center" />
        <el-table-column label="字符/切片" width="110" align="center">
          <template #default="{ row }">
            <span class="text-secondary">{{ row.charCount }} / {{ row.chunkCount }}</span>
          </template>
        </el-table-column>

        <!-- 处理进度：摄取是分步流水线，这里直接展示到第几步 -->
        <el-table-column label="状态" width="210">
          <template #default="{ row }">
            <div class="state-cell">
              <el-tag :type="pipelineStateTagType(row.pipelineState)" size="small" effect="dark">
                {{ PIPELINE_STATE_LABEL[row.pipelineState] || row.pipelineState }}
              </el-tag>
              <!-- 处理中：显示 步骤 x/6 + 当前步骤名 -->
              <template v-if="isProcessing(row.pipelineState)">
                <span class="state-progress">{{ progressOf(row).done }}/{{ STEP_CODES.length }}</span>
                <span class="state-running">{{ progressOf(row).running }}</span>
              </template>
              <!-- 失败：显示卡在哪一步 -->
              <el-tooltip v-if="row.pipelineState === 'FAILED'" :content="row.lastErrorMsg || ''" placement="top">
                <span class="state-error">{{ row.lastErrorStep || '未知步骤' }}</span>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="启停" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="上传时间" width="165" align="center" />

        <el-table-column label="操作" width="230" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="row.pipelineState === 'FAILED'"
              link
              type="warning"
              @click="handleReingest(row)"
            >
              重建
            </el-button>
            <el-button
              v-else-if="row.pipelineState === 'READY' || row.pipelineState === 'DISABLED'"
              link
              :type="row.status === 1 ? 'danger' : 'success'"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button
              v-if="row.pipelineState === 'READY'"
              link
              @click="handleReingest(row)"
            >
              重建
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="还没有文档，点右上角「上传文档」开始" />
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

    <!-- ==================== 上传对话框 ==================== -->
    <el-dialog v-model="uploadVisible" title="上传知识库文档" width="560px" destroy-on-close>
      <el-alert
        class="mb-16"
        type="info"
        show-icon
        :closable="false"
        title="上传后会自动执行 解析 → 清洗 → 切片 → 向量化 → 写入 → 收尾，约 10~20 秒"
      />
      <el-form ref="uploadFormRef" :model="uploadForm" :rules="uploadRules" label-width="96px">
        <el-form-item label="文件" prop="file">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            :on-change="onFileChange"
            :on-remove="() => (uploadForm.file = null)"
            accept=".pdf,.docx,.md,.markdown,.txt,.pptx,.xlsx"
            drag
            style="width: 100%"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em></div>
            <template #tip>
              <div class="el-upload__tip">
                支持 PDF / DOCX / MD / TXT / PPTX / XLSX，单个文件 ≤ 50MB
              </div>
            </template>
          </el-upload>
        </el-form-item>

        <el-form-item label="类别" prop="category">
          <el-select v-model="uploadForm.category" placeholder="请选择" style="width: 100%">
            <el-option
              v-for="item in DOC_CATEGORY_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="标题">
          <el-input v-model="uploadForm.title" placeholder="留空则使用文件名" maxlength="255" show-word-limit />
        </el-form-item>

        <el-form-item label="切片策略">
          <el-select
            v-model="uploadForm.chunkingStrategy"
            placeholder="留空走全局默认（推荐）"
            clearable
            style="width: 100%"
            @change="onStrategyChange"
          >
            <el-option
              v-for="s in strategies"
              :key="s.code"
              :label="`${s.name}（${s.code}）`"
              :value="s.code"
            >
              <div class="strategy-option">
                <span>{{ s.name }}</span>
                <span class="strategy-desc">{{ s.description }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="切长 / 重叠">
          <div class="inline-fields">
            <el-input-number
              v-model="uploadForm.chunkMaxChars"
              :min="100"
              :max="4000"
              :step="100"
              placeholder="切长上限"
              controls-position="right"
            />
            <el-input-number
              v-model="uploadForm.chunkOverlap"
              :min="0"
              :max="1000"
              :step="50"
              placeholder="重叠"
              controls-position="right"
            />
            <span class="text-secondary field-hint">留空走策略默认</span>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">开始上传</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 详情抽屉 ==================== -->
    <el-drawer v-model="detailVisible" :title="detail?.title || '文档详情'" size="640px" destroy-on-close>
      <div v-if="detail" v-loading="detailLoading">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="文档 ID">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="类别">{{ categoryLabel(detail.category) }}</el-descriptions-item>
          <el-descriptions-item label="格式">{{ detail.sourceType }}</el-descriptions-item>
          <el-descriptions-item label="版本">v{{ detail.ingestVersion }}</el-descriptions-item>
          <el-descriptions-item label="字符数">{{ detail.charCount }}</el-descriptions-item>
          <el-descriptions-item label="切片数">{{ detail.chunkCount }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="pipelineStateTagType(detail.pipelineState)" size="small">
              {{ PIPELINE_STATE_LABEL[detail.pipelineState] || detail.pipelineState }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="启停">
            <el-tag :type="detail.status === 1 ? 'success' : 'info'" size="small">
              {{ detail.status === 1 ? '启用（参与检索）' : '停用' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="切片策略" :span="2">
            {{ detail.chunkingStrategy || '全局默认' }}
            <span v-if="detail.chunkMaxChars || detail.chunkOverlap" class="text-secondary">
              （切长 {{ detail.chunkMaxChars ?? '默认' }} / 重叠 {{ detail.chunkOverlap ?? '默认' }}）
            </span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.lastErrorStep" label="失败步骤" :span="2">
            <span class="text-danger">{{ detail.lastErrorStep }}</span>
            <div class="error-msg">{{ detail.lastErrorMsg }}</div>
          </el-descriptions-item>
          <el-descriptions-item label="上传时间" :span="2">{{ detail.createdAt }}</el-descriptions-item>
        </el-descriptions>

        <el-tabs v-model="detailTab" class="mt-16">
          <!-- 步骤时间线：分步展示流水线进度 -->
          <el-tab-pane label="处理步骤" name="steps">
            <div class="steps-list">
              <div
                v-for="step in mergedSteps"
                :key="step.code"
                class="step-item"
                :class="step.state"
              >
                <div class="step-icon">
                  <el-icon v-if="step.state === 'success'"><CircleCheckFilled /></el-icon>
                  <el-icon v-else-if="step.state === 'failed'"><CircleCloseFilled /></el-icon>
                  <el-icon v-else-if="step.state === 'running'" class="is-loading"><Loading /></el-icon>
                  <el-icon v-else><Clock /></el-icon>
                </div>
                <div class="step-main">
                  <div class="step-title">
                    <span class="step-code">{{ step.code }}</span>
                    <span class="step-label">{{ STEP_LABEL[step.code] || '' }}</span>
                    <span v-if="step.costMs != null" class="step-cost">{{ step.costMs }}ms</span>
                    <el-button
                      v-if="step.state === 'failed'"
                      link
                      type="warning"
                      size="small"
                      @click="handleRunStep(step.code)"
                    >
                      重跑此步
                    </el-button>
                  </div>
                  <div v-if="step.errorMsg" class="step-error">{{ step.errorMsg }}</div>
                  <div v-if="step.outputMeta" class="step-meta">{{ prettyMeta(step.outputMeta) }}</div>
                </div>
              </div>
            </div>
          </el-tab-pane>

          <!-- 切片预览 -->
          <el-tab-pane label="切片预览" name="chunks">
            <el-empty v-if="chunks.length === 0" description="暂无切片（文档尚未完成 CHUNK 步骤）" />
            <div v-for="c in chunks" :key="c.id" class="chunk-item">
              <div class="chunk-head">
                <span class="chunk-index">#{{ c.chunkIndex }}</span>
                <el-tag size="small" effect="plain">{{ c.strategyCode || '-' }}</el-tag>
                <span class="text-secondary">
                  {{ c.charStart }}–{{ c.charEnd }}（{{ c.content.length }} 字）
                </span>
                <span v-if="c.vectorRef" class="text-secondary chunk-ref">{{ c.vectorRef }}</span>
              </div>
              <p class="chunk-content">{{ c.content }}</p>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="detail" type="warning" plain :icon="RefreshRight" @click="handleReingest(detail)">
          重建索引
        </el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadFile } from 'element-plus'
import {
  CircleCheckFilled,
  CircleCloseFilled,
  Clock,
  Loading,
  Refresh,
  RefreshRight,
  Search,
  Upload,
  UploadFilled
} from '@element-plus/icons-vue'
import {
  DOC_CATEGORY_OPTIONS,
  PIPELINE_STATE_LABEL,
  STEP_CODES,
  STEP_LABEL,
  STEP_STATUS,
  disableDocument,
  enableDocument,
  getDocument,
  getRagHealth,
  getTimeline,
  listChunks,
  listDocuments,
  pipelineStateTagType,
  reingestDocument,
  runStep,
  uploadDocument,
  type RagChunkItem,
  type RagDocumentItem,
  type RagDocumentQuery,
  type RagStepItem,
  type UploadDocForm
} from '@/api/rag'

// ============================================================
// 列表
// ============================================================

const query = ref<RagDocumentQuery>({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  category: null,
  pipelineState: null,
  status: null
})

const rows = ref<RagDocumentItem[]>([])
const total = ref(0)
const loading = ref(false)

/** key = documentId，value = 当前进度（用于"处理中"行展示步骤 x/6） */
const progressMap = ref<Record<number, { done: number; running: string }>>({})

/** 处理中的状态集合（这些状态需要轮询） */
const PROCESSING_STATES = [
  'CREATED',
  'EXTRACTING',
  'EXTRACTED',
  'SANITIZING',
  'SANITIZED',
  'CHUNKING',
  'CHUNKED',
  'EMBEDDING',
  'EMBEDDED',
  'UPSERTING',
  'UPSERTED'
]

function isProcessing(state: string): boolean {
  return PROCESSING_STATES.includes(state)
}

function progressOf(row: RagDocumentItem): { done: number; running: string } {
  return progressMap.value[row.id] ?? { done: 0, running: '' }
}

function rowClass({ row }: { row: RagDocumentItem }): string {
  if (row.pipelineState === 'FAILED') return 'row-failed'
  if (isProcessing(row.pipelineState)) return 'row-processing'
  return ''
}

function categoryLabel(code: string | null): string {
  return DOC_CATEGORY_OPTIONS.find((o) => o.value === code)?.label ?? code ?? '-'
}

async function loadPage(): Promise<void> {
  loading.value = true
  try {
    const page = await listDocuments(query.value)
    rows.value = page.records
    total.value = page.total
    void refreshProgressForProcessing()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  query.value.pageNum = 1
  void loadPage()
}

function handleReset(): void {
  query.value = { pageNum: 1, pageSize: 10, keyword: '', category: null, pipelineState: null, status: null }
  void loadPage()
}

// ============================================================
// 处理进度：轮询 /timeline（后端每步落一行，能精确反映"到第几步"）
// ============================================================

let pollTimer: number | undefined

async function refreshProgressForProcessing(): Promise<void> {
  const processing = rows.value.filter((r) => isProcessing(r.pipelineState))
  if (processing.length === 0) return

  await Promise.all(
    processing.map(async (row) => {
      try {
        const tl = await getTimeline(row.id)
        const steps = tl.steps ?? []
        const done = steps.filter((s) => s.status === STEP_STATUS.SUCCESS).length
        const running = steps.find((s) => s.status === STEP_STATUS.RUNNING)?.stepCode ?? ''
        progressMap.value = {
          ...progressMap.value,
          [row.id]: { done, running: running ? STEP_LABEL[running] || running : '' }
        }
      } catch {
        /* 单条失败忽略 */
      }
    })
  )
}

function startPolling(): void {
  stopPolling()
  pollTimer = window.setInterval(async () => {
    if (document.hidden || !rows.value.some((r) => isProcessing(r.pipelineState))) {
      return
    }
    // 处理中的文档状态会变，重新拉列表 + 进度
    await loadPage()
  }, 3000)
}

function stopPolling(): void {
  if (pollTimer != null) {
    window.clearInterval(pollTimer)
    pollTimer = undefined
  }
}

// ============================================================
// 上传
// ============================================================

const uploadVisible = ref(false)
const uploading = ref(false)
const uploadFormRef = ref<FormInstance>()
const uploadRef = ref<{ clearFiles: () => void } | null>(null)

/** 切片策略列表（从 /api/rag/health 拉，避免前端硬编码） */
const strategies = ref<Array<{ code: string; name: string; description: string }>>([])

const uploadForm = ref<{
  file: File | null
  category: string
  title: string
  chunkingStrategy: string | null
  chunkMaxChars: number | null
  chunkOverlap: number | null
}>({
  file: null,
  category: '',
  title: '',
  chunkingStrategy: null,
  chunkMaxChars: null,
  chunkOverlap: null
})

const uploadRules: FormRules = {
  file: [{ required: true, message: '请选择文件', trigger: 'change' }],
  category: [{ required: true, message: '请选择文档类别', trigger: 'change' }]
}

function openUpload(): void {
  uploadForm.value = {
    file: null,
    category: '',
    title: '',
    chunkingStrategy: null,
    chunkMaxChars: null,
    chunkOverlap: null
  }
  uploadVisible.value = true
  void loadStrategies()
}

async function loadStrategies(): Promise<void> {
  if (strategies.value.length > 0) return
  try {
    const health = await getRagHealth()
    strategies.value = health.strategies ?? []
  } catch {
    /* health 是公开接口，失败也不阻塞上传 */
  }
}

function onFileChange(file: UploadFile): void {
  uploadForm.value.file = (file.raw as File) ?? null
  // 未填标题时自动带出文件名（去掉扩展名）
  if (!uploadForm.value.title && file.name) {
    uploadForm.value.title = file.name.replace(/\.[^.]+$/, '')
  }
}

/** 切换策略时把默认切长/重叠填进表单，方便用户微调 */
function onStrategyChange(code: string | null): void {
  if (!code) return
  const s = strategies.value.find((x) => x.code === code)
  if (!s) return
  // 从 health 返回里没有默认值时留空，走后端三层回退
  const full = (s as { defaultMaxChars?: number; defaultOverlap?: number })
  uploadForm.value.chunkMaxChars = full.defaultMaxChars ?? null
  uploadForm.value.chunkOverlap = full.defaultOverlap ?? null
}

async function submitUpload(): Promise<void> {
  if (!uploadFormRef.value) return
  const valid = await uploadFormRef.value.validate().catch(() => false)
  if (!valid || !uploadForm.value.file) return

  uploading.value = true
  try {
    const form: UploadDocForm = {
      category: uploadForm.value.category,
      title: uploadForm.value.title || undefined,
      chunkingStrategy: uploadForm.value.chunkingStrategy,
      chunkMaxChars: uploadForm.value.chunkMaxChars,
      chunkOverlap: uploadForm.value.chunkOverlap
    }
    const docId = await uploadDocument(uploadForm.value.file, form)
    ElMessage.success(`上传成功（文档 #${docId}），正在后台处理…`)
    uploadVisible.value = false
    uploadRef.value?.clearFiles()
    await loadPage()
    startPolling()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '上传失败')
  } finally {
    uploading.value = false
  }
}

// ============================================================
// 详情抽屉
// ============================================================

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<RagDocumentItem | null>(null)
const detailTab = ref('steps')
const steps = ref<RagStepItem[]>([])
const chunks = ref<RagChunkItem[]>([])

/**
 * 把「后端已落库的步骤」和「尚未执行的步骤」合并成完整 6 步，
 * 这样用户能看到"当前在第几步、后面还剩哪些"。
 */
const mergedSteps = computed(() => {
  return STEP_CODES.map((code) => {
    // 同一 code 可能有多次执行（重跑），取最后一次
    const runs = steps.value.filter((s) => s.stepCode === code)
    const last = runs.length ? runs[runs.length - 1] : null

    let state: 'pending' | 'running' | 'success' | 'failed' | 'skipped' = 'pending'
    if (last) {
      if (last.status === STEP_STATUS.RUNNING) state = 'running'
      else if (last.status === STEP_STATUS.SUCCESS) state = 'success'
      else if (last.status === STEP_STATUS.FAILED) state = 'failed'
      else if (last.status === STEP_STATUS.SKIPPED) state = 'skipped'
    }
    return {
      code,
      state,
      costMs: last?.costMs ?? null,
      errorMsg: last?.errorMsg ?? null,
      outputMeta: last?.outputMeta ?? null
    }
  })
})

async function openDetail(row: RagDocumentItem): Promise<void> {
  detailVisible.value = true
  detailTab.value = 'steps'
  detail.value = row
  steps.value = []
  chunks.value = []
  await reloadDetail(row.id)
}

async function reloadDetail(id: number): Promise<void> {
  detailLoading.value = true
  try {
    const [doc, tl] = await Promise.all([getDocument(id), getTimeline(id)])
    detail.value = doc
    steps.value = tl.steps ?? []
    // 只有走过 CHUNK 才有切片
    if (doc.chunkCount > 0) {
      chunks.value = await listChunks(id)
    } else {
      chunks.value = []
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

/** 步骤 output_meta 是 JSON 串，格式化成人读的形式 */
function prettyMeta(meta: string): string {
  try {
    const obj = JSON.parse(meta) as Record<string, unknown>
    return Object.entries(obj)
      .map(([k, v]) => `${k}=${String(v)}`)
      .join('　')
  } catch {
    return meta
  }
}

// ============================================================
// 操作
// ============================================================

async function toggleStatus(row: RagDocumentItem): Promise<void> {
  const enable = row.status !== 1
  try {
    await ElMessageBox.confirm(
      enable
        ? `启用后该文档将参与知识库检索，确定启用「${row.title}」吗？`
        : `停用后该文档将不再被检索到（切片与向量保留），确定停用「${row.title}」吗？`,
      '提示',
      { type: 'warning', confirmButtonText: enable ? '启用' : '停用', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    if (enable) {
      await enableDocument(row.id)
    } else {
      await disableDocument(row.id)
    }
    ElMessage.success(enable ? '已启用' : '已停用')
    await loadPage()
    if (detail.value?.id === row.id) await reloadDetail(row.id)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败')
  }
}

async function handleReingest(row: RagDocumentItem): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `重建会把版本号 +1 并重新执行整条流水线（解析→清洗→切片→向量化→写入），确定重建「${row.title}」吗？`,
      '重建索引',
      { type: 'warning', confirmButtonText: '开始重建', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await reingestDocument(row.id, '管理端手动重建')
    ElMessage.success('已派发重建任务，后台处理中…')
    await loadPage()
    startPolling()
    if (detailVisible.value) await reloadDetail(row.id)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '重建失败')
  }
}

async function handleRunStep(stepCode: string): Promise<void> {
  if (!detail.value) return
  try {
    await runStep(detail.value.id, stepCode, '管理端手动重跑')
    ElMessage.success(`已触发 ${stepCode}，处理中…`)
    await loadPage()
    startPolling()
    await reloadDetail(detail.value.id)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '重跑失败')
  }
}

// ============================================================

onMounted(() => {
  void loadPage()
})

onBeforeUnmount(() => {
  stopPolling()
})
</script>

<style scoped lang="scss">
.filter-card {
  margin-bottom: 16px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* ---------- 状态列 ---------- */
.state-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.state-progress {
  font-size: 12px;
  font-weight: 600;
  color: var(--lr-primary);
}

.state-running {
  font-size: 12px;
  color: var(--lr-text-secondary);
}

.state-error {
  font-size: 12px;
  color: var(--lr-danger);
  cursor: help;
}

:deep(.row-processing) {
  background-color: var(--lr-success-soft) !important;
}

:deep(.row-failed) {
  background-color: var(--lr-danger-soft) !important;
}

/* ---------- 上传表单 ---------- */
.strategy-option {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}

.strategy-desc {
  font-size: 11px;
  color: var(--lr-text-secondary);
}

.inline-fields {
  display: flex;
  gap: 8px;
  align-items: center;
}

.field-hint {
  font-size: 12px;
}

/* ---------- 步骤时间线 ---------- */
.steps-list {
  padding: 4px 0;
}

.step-item {
  display: flex;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--lr-border-light);

  &:last-child {
    border-bottom: none;
  }

  &.pending {
    opacity: 0.55;
  }
}

.step-icon {
  flex: 0 0 20px;
  padding-top: 1px;
  font-size: 18px;

  .success & {
    color: var(--lr-success);
  }

  .failed & {
    color: var(--lr-danger);
  }

  .running & {
    color: var(--lr-primary);
  }

  .skipped & {
    color: var(--lr-text-secondary);
  }

  .pending & {
    color: var(--lr-text-secondary);
  }
}

.step-main {
  flex: 1;
  min-width: 0;
}

.step-title {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.step-code {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  font-weight: 600;
  color: var(--lr-text-primary);
}

.step-label {
  font-size: 13px;
  color: var(--lr-text-regular);
}

.step-cost {
  margin-left: auto;
  font-size: 12px;
  color: var(--lr-text-secondary);
}

.step-error {
  margin-top: 4px;
  font-size: 12px;
  color: var(--lr-danger);
  word-break: break-word;
}

.step-meta {
  margin-top: 4px;
  font-family: Consolas, Monaco, monospace;
  font-size: 11px;
  line-height: 1.6;
  color: var(--lr-text-secondary);
  word-break: break-word;
}

/* ---------- 切片预览 ---------- */
.chunk-item {
  padding: 12px;
  margin-bottom: 12px;
  background: var(--lr-bg-page);
  border: 1px solid var(--lr-border-light);
  border-radius: var(--lr-radius-sm);
}

.chunk-head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
  font-size: 12px;
}

.chunk-index {
  font-weight: 600;
  color: var(--lr-primary);
}

.chunk-ref {
  margin-left: auto;
  font-family: Consolas, Monaco, monospace;
}

.chunk-content {
  margin: 0;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.8;
  color: var(--lr-text-regular);
  white-space: pre-wrap;
  display: -webkit-box;
  -webkit-line-clamp: 6;
  -webkit-box-orient: vertical;
}

.error-msg {
  margin-top: 4px;
  font-size: 12px;
  color: var(--lr-danger);
  word-break: break-word;
}
</style>
