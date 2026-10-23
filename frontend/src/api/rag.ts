import { post, getToken, clearToken } from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/type'

/**
 * RAG 智能问答接口（后端 `com.labreserve.rag.controller`）。
 *
 * <h3>两组接口</h3>
 * <ul>
 *   <li><b>聊天</b>（`rag:chat:query` / `rag:chat:history`，5 角色全开）</li>
 *   <li><b>知识库管理</b>（`rag:doc:*`，仅 LAB_ADMIN / SYS_ADMIN）</li>
 * </ul>
 *
 * <h3>为什么聊天不用 axios</h3>
 * `/api/rag/chat/send` 是 **POST + JSON body + Authorization 头 + text/event-stream**。
 * 浏览器原生 `EventSource` 只支持 GET 且不能带自定义头，所以必须用
 * `fetch` + `ReadableStream` 手动解析 SSE（见 {@link sendChatStream}）。
 * 代价：fetch 不走 axios 拦截器，401 / 429 要在这里自己处理。
 */

// ============================================================
// 类型
// ============================================================

/** SSE 事件类型（与后端 RagStreamItem.Kind 对应） */
export type RagEventKind =
  | 'CHUNK'
  | 'CITATION'
  | 'MESSAGE'
  | 'DEGRADED'
  | 'DONE'
  | 'ERROR'

/** 引用条目（可追溯到 chunk） */
export interface CitationItem {
  index: number
  documentId: number
  title: string | null
  category: string | null
  chunkId: number
  vectorRef: string | null
  /** 200 字短片段（给 UI 展示；LLM 用的是完整正文） */
  snippet: string | null
  score: number | null
}

/** 流式事件负载 */
export interface RagStreamItem {
  kind: RagEventKind
  delta?: string
  citations?: CitationItem[]
  messageId?: number
  totalTokens?: number
  costMs?: number
  reason?: string
  error?: string
}

/** 会话列表项 */
export interface RagSessionItem {
  id: number
  title: string
  messageCount: number
  lastMessageAt: string
  createdAt: string
}

/** 会话消息（后端返回 RagMessage 实体，这里只声明用到的字段） */
export interface RagMessageItem {
  id: number
  sessionId: number
  role: 'USER' | 'ASSISTANT' | 'SYSTEM' | 'TOOL'
  content: string
  rewritten: string | null
  intent: string | null
  citations: string | null
  feedback: number | null
  costMs: number | null
  createdAt: string
}

/** 文档类别（与后端 RagDocumentCategory 一致） */
export type DocCategory = 'REGULATION' | 'MANUAL' | 'FAQ' | 'SOP'

/** 文档详情（后端 RagDocumentVO） */
export interface RagDocumentItem {
  id: number
  title: string
  category: DocCategory | string
  sourceType: string
  fileUrl: string | null
  charCount: number
  chunkCount: number
  pipelineState: string
  ingestVersion: number
  contentSha256: string | null
  lastErrorStep: string | null
  lastErrorMsg: string | null
  lastErrorAt: string | null
  chunkingStrategy: string | null
  chunkMaxChars: number | null
  chunkOverlap: number | null
  status: number
  uploadedBy: number
  createdAt: string
  updatedAt: string
}

/** 步骤日志条目（后端 RagStepTimelineVO.StepEntry） */
export interface RagStepItem {
  id: number
  stepCode: string
  /** 1 RUNNING / 2 SUCCESS / 3 FAILED / 4 SKIPPED */
  status: number
  outputMeta: string | null
  traceId: string | null
  costMs: number | null
  errorMsg: string | null
  startedAt: string | null
  finishedAt: string | null
}

/** 切片明细 */
export interface RagChunkItem {
  id: number
  documentId: number
  ingestVersion: number
  chunkIndex: number
  content: string
  charStart: number
  charEnd: number
  strategyCode: string | null
  vectorRef: string | null
  createdAt: string
}

/** 健康检查返回（用于拉切片策略列表） */
export interface RagHealthInfo {
  enabled: boolean
  chatModelReady: boolean
  qdrantReady: boolean
  defaultChunking: string
  strategies: Array<{
    code: string
    name: string
    description: string
    recommendedFor: string[]
    defaultMaxChars: number
    defaultOverlap: number
  }>
}

/** 上传表单（除文件外的字段） */
export interface UploadDocForm {
  category: DocCategory | string
  title?: string
  chunkingStrategy?: string | null
  chunkMaxChars?: number | null
  chunkOverlap?: number | null
}

// ============================================================
// 常量
// ============================================================

export const DOC_CATEGORY_OPTIONS = [
  { value: 'REGULATION', label: '规章制度' },
  { value: 'MANUAL', label: '操作手册' },
  { value: 'FAQ', label: '常见问题' },
  { value: 'SOP', label: '实验流程' }
] as const

/** 流水线状态中文名（与后端 RagPipelineState 枚举对应） */
export const PIPELINE_STATE_LABEL: Record<string, string> = {
  CREATED: '已创建',
  EXTRACTING: '解析中',
  EXTRACTED: '已解析',
  SANITIZING: '清洗中',
  SANITIZED: '已清洗',
  CHUNKING: '切片中',
  CHUNKED: '已切片',
  EMBEDDING: '向量化中',
  EMBEDDED: '已向量化',
  UPSERTING: '写入中',
  UPSERTED: '已写入',
  READY: '就绪',
  DISABLED: '已停用',
  FAILED: '失败'
}

/** 流水线状态对应的 tag 颜色 */
export function pipelineStateTagType(state: string): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  if (state === 'READY') return 'success'
  if (state === 'FAILED') return 'danger'
  if (state === 'DISABLED') return 'info'
  if (['CREATED', 'EXTRACTED', 'SANITIZED', 'CHUNKED', 'EMBEDDED', 'UPSERTED'].includes(state)) return 'primary'
  return 'warning'
}

/** 步骤编码（展示顺序 = 执行顺序） */
export const STEP_CODES = ['EXTRACT', 'SANITIZE', 'CHUNK', 'EMBED', 'UPSERT', 'FINALIZE'] as const

export const STEP_LABEL: Record<string, string> = {
  EXTRACT: '解析正文',
  SANITIZE: '敏感清洗',
  CHUNK: '文本切片',
  EMBED: '向量化',
  UPSERT: '写入向量库',
  FINALIZE: '收尾统计'
}

/** 步骤状态：1 RUNNING / 2 SUCCESS / 3 FAILED / 4 SKIPPED */
export const STEP_STATUS = {
  RUNNING: 1,
  SUCCESS: 2,
  FAILED: 3,
  SKIPPED: 4
} as const

// ============================================================
// 聊天接口
// ============================================================

/**
 * 发起问答（SSE 流式）。
 *
 * <p>用 fetch 手动解析 SSE：`EventSource` 不支持 POST + Authorization 头。
 *
 * @param message   用户问题
 * @param sessionId 会话 ID（空 = 新会话）
 * @param onEvent   每个事件回调
 * @param signal    AbortController.signal（用于「停止生成」）
 * @throws Error 401（未登录）/ 429（限流）/ 其他 HTTP 错误
 */
export async function sendChatStream(
  message: string,
  sessionId: number | null,
  onEvent: (evt: RagStreamItem) => void,
  signal?: AbortSignal
): Promise<void> {
  const token = getToken()
  const baseUrl = (import.meta.env.VITE_API_BASE_URL as string) || '/api'

  const resp = await fetch(`${baseUrl}/rag/chat/send`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json;charset=UTF-8',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify({ message, sessionId, stream: true }),
    signal
  })

  // ---- 非 200：fetch 不走 axios 拦截器，这里自己翻译 ----
  if (!resp.ok) {
    let body: { code?: number; message?: string } | null = null
    try {
      body = await resp.json()
    } catch {
      /* 可能是纯文本错误页，忽略 */
    }
    if (resp.status === 401) {
      clearToken()
      window.location.href = `/login?redirect=${encodeURIComponent(window.location.pathname)}`
      throw new Error('登录已失效，请重新登录')
    }
    if (resp.status === 429) {
      throw new Error(body?.message || 'AI 请求过于频繁，请稍后再试')
    }
    throw new Error(body?.message || `请求失败（HTTP ${resp.status}）`)
  }

  if (!resp.body) {
    throw new Error('服务端未返回流式响应体')
  }

  // ---- 逐行解析 SSE ----
  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      // SSE 以空行分隔事件；这里按行累积，遇到 "data:" 行就解析
      let idx: number
      while ((idx = buffer.indexOf('\n')) >= 0) {
        const line = buffer.slice(0, idx).replace(/\r$/, '')
        buffer = buffer.slice(idx + 1)

        if (!line.startsWith('data:')) continue
        const payload = line.slice(5).trim()
        if (!payload) continue
        try {
          const evt = JSON.parse(payload) as RagStreamItem
          onEvent(evt)
        } catch {
          // 单个事件解析失败不影响后续
        }
      }
    }
    // 冲刷残留（有些服务端最后一行没有换行）
    const rest = buffer.trim()
    if (rest.startsWith('data:')) {
      try {
        onEvent(JSON.parse(rest.slice(5).trim()) as RagStreamItem)
      } catch {
        /* ignore */
      }
    }
  } finally {
    reader.releaseLock()
  }
}

/** 我的会话列表 */
export function listSessions() {
  return post<RagSessionItem[]>('/rag/chat/sessions')
}

/** 某会话的消息历史（时间正序） */
export function listMessages(sessionId: number) {
  return post<RagMessageItem[]>('/rag/chat/messages', { id: sessionId })
}

/** 删除会话（逻辑删除，仅本人） */
export function deleteSession(sessionId: number) {
  return post<void>('/rag/chat/delete_session', { id: sessionId })
}

/** 消息反馈：1 有用 / 0 没用 */
export function feedbackMessage(messageId: number, feedback: number) {
  return post<void>('/rag/chat/feedback', { messageId, feedback })
}

// ============================================================
// 知识库管理接口（仅 LAB_ADMIN / SYS_ADMIN）
// ============================================================

/** RAG 健康检查（公开接口，用于拉切片策略列表与依赖状态） */
export function getRagHealth() {
  return post<RagHealthInfo>('/rag/health')
}

/**
 * 上传文档（multipart）。
 *
 * <p>axios 的 `post()` 只适合 JSON body，multipart 需要直接用它提供的实例，
 * 这里用原生 fetch 以保持与 {@link sendChatStream} 一致的错误处理口径。
 *
 * @returns 新文档 ID
 */
export async function uploadDocument(file: File, form: UploadDocForm): Promise<number> {
  const token = getToken()
  const baseUrl = (import.meta.env.VITE_API_BASE_URL as string) || '/api'

  const fd = new FormData()
  fd.append('file', file)
  fd.append('category', form.category)
  if (form.title) fd.append('title', form.title)
  if (form.chunkingStrategy) fd.append('chunkingStrategy', form.chunkingStrategy)
  if (form.chunkMaxChars != null) fd.append('chunkMaxChars', String(form.chunkMaxChars))
  if (form.chunkOverlap != null) fd.append('chunkOverlap', String(form.chunkOverlap))

  const resp = await fetch(`${baseUrl}/rag/admin/upload`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: fd
  })

  const body = (await resp.json().catch(() => null)) as
    | { code: number; message: string; data: number }
    | null

  if (!resp.ok || !body || body.code !== 200) {
    if (resp.status === 401) {
      clearToken()
      window.location.href = '/login'
    }
    throw new Error(body?.message || `上传失败（HTTP ${resp.status}）`)
  }
  return body.data
}

/** 文档分页查询条件 */
export interface RagDocumentQuery extends PageQuery {
  keyword?: string | null
  category?: string | null
  pipelineState?: string | null
  status?: number | null
}

/** 文档分页列表（知识库管理页） */
export function listDocuments(data: RagDocumentQuery) {
  return post<PageResult<RagDocumentItem>>('/rag/admin/list', data)
}

/** 文档详情 */
export function getDocument(id: number) {
  return post<RagDocumentItem>('/rag/admin/get', { id })
}

/** 步骤时间线（按当前 ingestVersion） */
export function getTimeline(id: number) {
  return post<{
    documentId: number
    ingestVersion: number
    pipelineState: string
    steps: RagStepItem[]
  }>('/rag/admin/timeline', { id })
}

/** 切片明细 */
export function listChunks(id: number) {
  return post<RagChunkItem[]>('/rag/admin/list_chunks', { id })
}

/** 启用文档（status=1，参与检索） */
export function enableDocument(id: number) {
  return post<void>('/rag/admin/enable', { id })
}

/** 停用文档（status=0，不参与检索） */
export function disableDocument(id: number) {
  return post<void>('/rag/admin/disable', { id })
}

/** 重建索引（ingestVersion+1，全链重跑） */
export function reingestDocument(id: number, reason?: string) {
  return post<void>('/rag/admin/reingest', { documentId: id, reason })
}

/** 手动重跑单个 Step */
export function runStep(id: number, stepCode: string, remark?: string) {
  return post<void>('/rag/admin/run_step', { documentId: id, stepCode, remark })
}
