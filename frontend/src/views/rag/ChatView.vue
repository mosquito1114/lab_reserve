<template>
  <div class="chat-page">
    <!-- ==================== 左：会话列表 ==================== -->
    <aside class="session-panel" :class="{ collapsed: sessionsCollapsed }">
      <div class="session-head">
        <el-button
          v-show="!sessionsCollapsed"
          type="primary"
          :icon="Plus"
          class="session-new"
          @click="startNewSession"
        >
          新会话
        </el-button>
        <el-icon class="session-toggle" @click="sessionsCollapsed = !sessionsCollapsed">
          <component :is="sessionsCollapsed ? 'Expand' : 'Fold'" />
        </el-icon>
      </div>

      <el-scrollbar v-show="!sessionsCollapsed" class="session-list">
        <el-empty v-if="sessions.length === 0" description="还没有会话" :image-size="60" />
        <div
          v-for="item in sessions"
          :key="item.id"
          class="session-item"
          :class="{ active: item.id === currentSessionId }"
          @click="switchSession(item.id)"
        >
          <div class="session-title">{{ item.title }}</div>
          <div class="session-meta">
            <span>{{ item.messageCount }} 条</span>
            <span>{{ shortTime(item.lastMessageAt) }}</span>
          </div>
          <el-icon class="session-del" title="删除会话" @click.stop="handleDeleteSession(item)">
            <Delete />
          </el-icon>
        </div>
      </el-scrollbar>
    </aside>

    <!-- ==================== 右：聊天区 ==================== -->
    <section class="chat-panel">
      <header class="chat-head">
        <div class="chat-head-left">
          <span class="chat-title">{{ currentSession?.title || '新会话' }}</span>
          <el-tag v-if="degradedReason" type="warning" size="small" effect="plain">
            降级：{{ degradedReason }}
          </el-tag>
        </div>
        <el-button link :icon="Refresh" @click="startNewSession">清空当前对话</el-button>
      </header>

      <!-- 消息流 -->
      <el-scrollbar ref="scrollRef" class="chat-body" @scroll="onScroll">
        <!-- 空态：推荐问题 -->
        <div v-if="messages.length === 0" class="chat-empty">
          <el-icon :size="44" color="var(--lr-primary)"><ChatLineSquare /></el-icon>
          <h3>实验室知识库问答</h3>
          <p class="text-secondary">
            我可以回答规章制度、操作手册、实验 SOP、常见问题。<br />
            业务操作（查/取消预约、报修进度）请到对应系统页面完成。
          </p>
          <div class="suggest-list">
            <div
              v-for="q in SUGGESTIONS"
              :key="q"
              class="suggest-item"
              @click="askSuggestion(q)"
            >
              {{ q }}
            </div>
          </div>
        </div>

        <!-- 消息气泡 -->
        <div v-for="msg in messages" :key="msg.key" class="msg-row" :class="msg.role.toLowerCase()">
          <div class="msg-avatar" :class="msg.role.toLowerCase()">
            {{ msg.role === 'USER' ? avatarText : 'AI' }}
          </div>

          <div class="msg-main">
            <div v-if="msg.role === 'USER'" class="msg-bubble user-bubble">{{ msg.content }}</div>

            <template v-else>
              <!-- 引用角标需要 data-cite，用 v-html + 事件委托（见 onBodyClick） -->
              <div
                class="msg-bubble ai-bubble markdown-body"
                v-html="renderAi(msg)"
                @click="onBodyClick($event, msg)"
              />

              <div v-if="msg.pending" class="typing">
                <span /><span /><span />
              </div>

              <!-- 引用来源：默认收起，只显示名称；点标题栏展开 -->
              <div v-if="msg.citations && msg.citations.length" class="cite-block">
                <div class="cite-head" @click="msg.citesOpen = !msg.citesOpen">
                  <el-icon class="cite-caret" :class="{ open: msg.citesOpen }">
                    <ArrowRight />
                  </el-icon>
                  <span class="cite-head-text">引用来源 {{ msg.citations.length }} 条</span>
                  <!-- 收起时用一行标题预览，让用户不展开也知道引了哪些文档 -->
                  <span class="cite-preview">{{ msg.citations.map((c) => c.title).join(' · ') }}</span>
                </div>

                <div v-show="msg.citesOpen" class="cite-list">
                  <div
                    v-for="c in msg.citations"
                    :key="c.index"
                    class="cite-row"
                    :class="{ highlight: msg.activeCite === c.index }"
                    :data-cite-ref="`${msg.key}-${c.index}`"
                  >
                    <span class="cite-index">{{ c.index }}</span>
                    <span class="cite-title">{{ c.title || '未命名文档' }}</span>
                    <el-tag size="small" effect="plain" class="cite-cat">
                      {{ categoryLabel(c.category) }}
                    </el-tag>
                    <span class="cite-score">{{ (c.score ?? 0).toFixed(2) }}</span>
                    <!-- 片段放 tooltip 里，正文区不再占地方 -->
                    <el-tooltip :content="c.snippet || '（无片段）'" placement="top" :show-after="200">
                      <el-icon class="cite-info"><InfoFilled /></el-icon>
                    </el-tooltip>
                  </div>
                </div>
              </div>

              <!-- 操作条 -->
              <div v-if="!msg.pending && msg.messageId" class="msg-actions">
                <el-tooltip content="复制回答" placement="top">
                  <el-button link :icon="CopyDocument" @click="copyText(msg.content)" />
                </el-tooltip>
                <el-tooltip content="有用" placement="top">
                  <el-button
                    link
                    :type="msg.feedback === 1 ? 'primary' : undefined"
                    :icon="Select"
                    @click="sendFeedback(msg, 1)"
                  />
                </el-tooltip>
                <el-tooltip content="没用" placement="top">
                  <el-button
                    link
                    :type="msg.feedback === 0 ? 'danger' : undefined"
                    :icon="CloseBold"
                    @click="sendFeedback(msg, 0)"
                  />
                </el-tooltip>
                <span v-if="msg.costMs" class="msg-cost">耗时 {{ (msg.costMs / 1000).toFixed(1) }}s</span>
              </div>
            </template>
          </div>
        </div>

        <!-- 底部错位提示 -->
        <div v-if="lastError" class="chat-error">
          <el-alert type="error" :closable="false" show-icon :title="lastError" />
          <el-button class="mt-8" size="small" :icon="RefreshRight" @click="retryLast">
            重试
          </el-button>
        </div>
      </el-scrollbar>

      <!-- 输入区 -->
      <footer class="chat-input">
        <el-input
          ref="inputRef"
          v-model="draft"
          type="textarea"
          :rows="2"
          resize="none"
          maxlength="4000"
          show-word-limit
          placeholder="输入问题，Enter 发送，Shift + Enter 换行"
          :disabled="sending"
          @keydown="onKeydown"
        />
        <div class="chat-input-actions">
          <el-button v-if="sending" :icon="VideoPause" @click="stopGenerating">停止</el-button>
          <el-button
            v-else
            type="primary"
            :icon="Promotion"
            :disabled="!draft.trim()"
            @click="send"
          >
            发送
          </el-button>
        </div>
      </footer>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowRight,
  ChatLineSquare,
  CloseBold,
  CopyDocument,
  Delete,
  InfoFilled,
  Loading,
  Plus,
  Promotion,
  Refresh,
  RefreshRight,
  Select,
  VideoPause
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import {
  deleteSession,
  feedbackMessage,
  listMessages,
  listSessions,
  sendChatStream,
  type CitationItem,
  type RagMessageItem,
  type RagSessionItem,
  type RagStreamItem
} from '@/api/rag'
import { readCiteIndex, renderRichText } from '@/utils/markdown'

// ============================================================
// 常量
// ============================================================

/** 空态推荐问题（覆盖知识库主要章节，便于演示） */
const SUGGESTIONS = [
  '离心机使用前要检查什么',
  '危险化学品五双管理是什么',
  '易燃易爆化学品怎么存放',
  '实验室发生火警怎么办'
]

/**
 * 打字机参数。
 *
 * <p>为什么需要：后端 LLM 的 chunk 本身就是「一块一块」的
 * （实测 199 字只有 18 个 chunk，平均每块约 11 字），
 * 前端如果原样追加，视觉上就是"整块跳出"，不像打字机。
 *
 * <p>做法：收到的文字先进 queue，再由定时泵按固定节奏逐字吐到 content，
 * 末速与到达速度解耦，观感稳定。
 */
const TYPE_TICK_MS = 24
/** 每 tick 吐几个字（约 3 字/24ms ≈ 125 字/秒） */
const TYPE_CHARS_PER_TICK = 3
/** 积压超过这个长度就加速（避免长回答越拖越久） */
const TYPE_BACKLOG_FAST = 160

// ============================================================
// 状态
// ============================================================

const userStore = useUserStore()
const avatarText = computed(() => (userStore.realName ? userStore.realName.charAt(0) : '我'))

const sessionsCollapsed = ref(false)
const sessions = ref<RagSessionItem[]>([])
const currentSessionId = ref<number | null>(null)

/** 聊天消息（前端本地模型，与后端 RagMessage 不完全同构） */
interface UiMessage {
  key: string
  role: 'USER' | 'ASSISTANT'
  content: string
  citations: CitationItem[]
  messageId: number | null
  feedback: number | null
  costMs: number | null
  /** 流是否还在进行（后端没发 DONE） */
  pending: boolean
  /**
   * 阶段：
   *   retrieving —— 已发出问题，等待首个正文 chunk（检索 + 意图 + 判相关性）
   *   generating —— 正在吐字
   */
  phase: 'retrieving' | 'generating'
  /** 待吐出的文字缓冲（打字机泵从中取字） */
  queue: string
  /** 后端流是否已结束（结束后泵把残余吐完就收工） */
  streamEnded: boolean
  /** 引用来源是否展开（默认收起，点击标题栏展开） */
  citesOpen?: boolean
  /** 当前高亮的引用编号（点击角标时设置） */
  activeCite: number | null
}

const messages = ref<UiMessage[]>([])
const draft = ref('')
const sending = ref(false)
const lastError = ref('')
const degradedReason = ref('')

const scrollRef = ref<{ wrapRef?: HTMLElement; setScrollTop?: (v: number) => void } | null>(null)
const inputRef = ref<{ focus: () => void } | null>(null)

/** 当前会话（从列表里取，用于标题展示） */
const currentSession = computed(() =>
  sessions.value.find((s) => s.id === currentSessionId.value) ?? null
)

// ============================================================
// 流式生成控制器
// ============================================================

let abortCtrl: AbortController | null = null
/** 上一次提问（用于「重试」） */
let lastQuestion = ''

/** 打字机泵：key → 消息（只放还没吐完的） */
const typingTargets = new Map<string, UiMessage>()
let typeTimer: number | undefined

/** 把一段增量放进该消息的待吐队列，并确保泵在跑 */
function enqueueText(msg: UiMessage, chunk: string): void {
  msg.queue += chunk
  typingTargets.set(msg.key, msg)
  if (typeTimer == null) {
    typeTimer = window.setInterval(typeTick, TYPE_TICK_MS)
  }
}

/** 泵的一拍：从各消息队列里取字吐到 content */
function typeTick(): void {
  if (typingTargets.size === 0) {
    stopTyping()
    return
  }

  let painted = false
  for (const msg of typingTargets.values()) {
    if (msg.queue.length > 0) {
      // 积压多时加速，避免长回答尾段拖太久
      const speed = msg.queue.length > TYPE_BACKLOG_FAST
        ? TYPE_CHARS_PER_TICK * 4
        : TYPE_CHARS_PER_TICK
      const take = Math.min(speed, msg.queue.length)
      msg.content += msg.queue.slice(0, take)
      msg.queue = msg.queue.slice(take)
      // 首个字出现 → 退出「检索中」阶段
      if (msg.phase === 'retrieving') msg.phase = 'generating'
      painted = true
    } else if (msg.streamEnded) {
      // 队列空了且流已结束 → 这条消息收工
      typingTargets.delete(msg.key)
      msg.pending = false
      painted = true
    }
  }

  if (painted) scrollToBottom()
  if (typingTargets.size === 0) stopTyping()
}

function stopTyping(): void {
  if (typeTimer != null) {
    window.clearInterval(typeTimer)
    typeTimer = undefined
  }
}

/** 立即把队列吐完（用于「停止生成」或出错，避免卡一半） */
function flushTyping(msg: UiMessage): void {
  if (msg.queue) {
    msg.content += msg.queue
    msg.queue = ''
  }
  msg.streamEnded = true
  msg.pending = false
  typingTargets.delete(msg.key)
}

// ============================================================
// 渲染
// ============================================================

/**
 * 渲染 AI 回答。
 *
 * <p>流式期间 maxCitation 用「已知引用数」；如果引用事件还没到（后端是正文先流、
 * 引用后发），此时 maxCitation=0，角标不会被渲染成 badge ——
 * 所以流式结束后必须重渲染一次（靠引用到达后 messages 引用变化触发）。
 */
function renderAi(msg: UiMessage): string {
  const maxCite = msg.citations.length
  return renderRichText(msg.content, maxCite)
}

/** 点击角标 → 高亮对应引用项并滚动到可见 */
function onBodyClick(evt: MouseEvent, msg: UiMessage): void {
  const idx = readCiteIndex(evt.target)
  if (idx == null) return
  // 点了角标却看不到引用行，体验很怪 —— 自动展开
  if (!msg.citesOpen) msg.citesOpen = true
  msg.activeCite = idx
  void nextTick(() => {
    const el = document.querySelector(`[data-cite-ref="${msg.key}-${idx}"]`)
    el?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  })
  // 2 秒后取消高亮
  window.setTimeout(() => {
    if (msg.activeCite === idx) msg.activeCite = null
  }, 2000)
}

function categoryLabel(code: string | null): string {
  switch (code) {
    case 'REGULATION':
      return '规章制度'
    case 'MANUAL':
      return '操作手册'
    case 'FAQ':
      return '常见问题'
    case 'SOP':
      return '实验流程'
    default:
      return code || '未知'
  }
}

function shortTime(value: string): string {
  if (!value) return ''
  // "2026-10-23 17:30:12" → "10-23 17:30"
  return value.length >= 16 ? value.slice(5, 16) : value
}

// ============================================================
// 滚动
// ============================================================

/** 用户是否贴底（贴底才自动滚动，避免打断用户翻历史） */
let stickToBottom = true

function onScroll(): void {
  const wrap = scrollRef.value?.wrapRef
  if (!wrap) return
  const distance = wrap.scrollHeight - wrap.scrollTop - wrap.clientHeight
  stickToBottom = distance < 80
}

function scrollToBottom(force = false): void {
  if (!force && !stickToBottom) return
  void nextTick(() => {
    const wrap = scrollRef.value?.wrapRef
    if (wrap) wrap.scrollTop = wrap.scrollHeight
  })
}

// ============================================================
// 会话
// ============================================================

async function loadSessions(): Promise<void> {
  try {
    sessions.value = await listSessions()
  } catch {
    /* 无权限或网络问题时静默，聊天仍可用 */
  }
}

async function switchSession(id: number): Promise<void> {
  if (sending.value) {
    ElMessage.warning('正在生成回答，请先停止或等待完成')
    return
  }
  if (id === currentSessionId.value) return

  currentSessionId.value = id
  lastError.value = ''
  degradedReason.value = ''
  messages.value = []

  try {
    const rows = await listMessages(id)
    messages.value = rows
      .filter((r) => r.role === 'USER' || r.role === 'ASSISTANT')
      .map((r: RagMessageItem) => ({
        key: `h-${r.id}`,
        role: r.role === 'USER' ? 'USER' : 'ASSISTANT',
        content: r.content,
        citations: parseCitations(r.citations),
        messageId: r.role === 'ASSISTANT' ? r.id : null,
        feedback: r.feedback,
        costMs: r.costMs,
        pending: false,
        phase: 'generating' as const,
        queue: '',
        streamEnded: true,
        citesOpen: false,
        activeCite: null
      }))
    scrollToBottom(true)
  } catch (e) {
    lastError.value = e instanceof Error ? e.message : '加载会话失败'
  }
}

/** 后端把 citations 存成 JSON 字符串，这里解析（解析失败当作空） */
function parseCitations(raw: string | null): CitationItem[] {
  if (!raw) return []
  try {
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? (arr as CitationItem[]) : []
  } catch {
    return []
  }
}

function startNewSession(): void {
  if (sending.value) {
    ElMessage.warning('正在生成回答，请先停止')
    return
  }
  currentSessionId.value = null
  messages.value = []
  lastError.value = ''
  degradedReason.value = ''
  draft.value = ''
  void nextTick(() => inputRef.value?.focus())
}

async function handleDeleteSession(item: RagSessionItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除会话「${item.title}」吗？`, '提示', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteSession(item.id)
    ElMessage.success('已删除')
    if (currentSessionId.value === item.id) {
      startNewSession()
    }
    await loadSessions()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

// ============================================================
// 发送 / 流式接收
// ============================================================

function onKeydown(evt: KeyboardEvent): void {
  if (evt.key !== 'Enter' || evt.shiftKey) return
  evt.preventDefault()
  void send()
}

function askSuggestion(q: string): void {
  draft.value = q
  void send()
}

async function send(): Promise<void> {
  const text = draft.value.trim()
  if (!text || sending.value) return

  lastQuestion = text
  draft.value = ''
  lastError.value = ''
  degradedReason.value = ''
  sending.value = true
  stickToBottom = true

  // 用户气泡
  messages.value.push({
    key: `u-${Date.now()}`,
    role: 'USER',
    content: text,
    citations: [],
    messageId: null,
    feedback: null,
    costMs: null,
    pending: false,
    phase: 'generating' as const,
    queue: '',
    streamEnded: true,
    activeCite: null
  })

  // AI 气泡（占位，流式填充）
  messages.value.push({
    key: `a-${Date.now()}`,
    role: 'ASSISTANT',
    content: '',
    citations: [],
    messageId: null,
    feedback: null,
    costMs: null,
    pending: true,
    // 此时后端正在 意图识别 → 检索 → 相关性判定 → 调 LLM，尚无正文
    phase: 'retrieving',
    queue: '',
    streamEnded: false,
    citesOpen: false,
    activeCite: null
  })

  // ★ 关键：必须取 push 之后的**响应式代理**，不能用 push 进去的原始对象。
  //   messages 是 ref([])，push 的是裸对象；闭包里继续改裸对象会绕过 Vue 的 Proxy，
  //   视图不会更新 —— 现象就是"一直在加载，只有再发一条消息（数组变更触发整体重渲染）
  //   才把之前累积的正文一次性刷出来"，而不是打字机效果。
  const aiMsg = messages.value[messages.value.length - 1]!
  scrollToBottom(true)

  abortCtrl = new AbortController()

  try {
    await sendChatStream(
      text,
      currentSessionId.value,
      (evt: RagStreamItem) => handleEvent(evt, aiMsg),
      abortCtrl.signal
    )
  } catch (e) {
    if ((e as Error).name === 'AbortError') {
      flushTyping(aiMsg)
      aiMsg.content += aiMsg.content ? '\n\n（已停止生成）' : '（已停止生成）'
    } else {
      flushTyping(aiMsg)
      lastError.value = e instanceof Error ? e.message : '请求失败'
    }
  } finally {
    // 流结束：让泵把残余吐完再收工（不直接置 pending=false，
    // 否则会丢掉队列里还没打出来的字）
    aiMsg.streamEnded = true
    if (abortCtrl?.signal.aborted) {
      flushTyping(aiMsg)
    }
    sending.value = false
    abortCtrl = null
    // 会话可能是新建的（后端返回的 messageId 才带 sessionId），统一刷新一次列表
    void refreshAfterSend()
  }
}

/** 处理单个 SSE 事件 */
function handleEvent(evt: RagStreamItem, aiMsg: UiMessage): void {
  switch (evt.kind) {
    case 'CHUNK':
      // 进队列，由打字机泵匀速吐出（不直接写 content）
      if (evt.delta) enqueueText(aiMsg, evt.delta)
      break

    case 'CITATION':
      aiMsg.citations = evt.citations ?? []
      break

    case 'MESSAGE':
      // 后端在 MESSAGE 里也带 citations（与 CITATION 重复，取一次即可）
      if (evt.citations?.length) aiMsg.citations = evt.citations
      aiMsg.messageId = evt.messageId ?? null
      aiMsg.costMs = evt.costMs ?? null
      break

    case 'DEGRADED':
      degradedReason.value = evt.reason || '服务降级'
      break

    case 'ERROR':
      lastError.value = evt.error || '生成失败'
      break

    case 'DONE':
      aiMsg.streamEnded = true
      break
  }
}

/** 发送后刷新会话列表（新会话需要拿到 id 才能继续追问） */
async function refreshAfterSend(): Promise<void> {
  try {
    const before = sessions.value.length
    const list = await listSessions()
    sessions.value = list
    if (!currentSessionId.value && list.length > before) {
      // 新建了会话：取最近活跃的那条（后端 bumpMessage 后 last_message_at 最大）
      currentSessionId.value = list[0]?.id ?? null
    }
  } catch {
    /* ignore */
  }
}

function stopGenerating(): void {
  abortCtrl?.abort()
}

function retryLast(): void {
  if (!lastQuestion || sending.value) return
  // 去掉上次失败的 AI 气泡与用户气泡，重新发
  messages.value.pop()
  messages.value.pop()
  draft.value = lastQuestion
  void send()
}

// ============================================================
// 反馈 / 复制
// ============================================================

async function sendFeedback(msg: UiMessage, value: number): Promise<void> {
  if (!msg.messageId) return
  try {
    await feedbackMessage(msg.messageId, value)
    msg.feedback = value
    ElMessage.success(value === 1 ? '感谢反馈' : '已记录，我们会改进')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '反馈失败')
  }
}

async function copyText(text: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('浏览器不允许访问剪贴板')
  }
}

// ============================================================

onMounted(() => {
  void loadSessions()
  void nextTick(() => inputRef.value?.focus())
})

onBeforeUnmount(() => {
  stopTyping()
  abortCtrl?.abort()
})
</script>

<style scoped lang="scss">
.chat-page {
  display: flex;
  height: 100%;
  overflow: hidden;
  background: #fff;
}

/* ==================== 会话列表 ==================== */
.session-panel {
  display: flex;
  flex-direction: column;
  width: 240px;
  border-right: 1px solid var(--lr-border);
  background: #fafbfc;
  transition: width 0.2s;

  &.collapsed {
    width: 44px;
  }
}

.session-head {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 12px;
  border-bottom: 1px solid var(--lr-border);
}

.session-new {
  flex: 1;
}

.session-toggle {
  font-size: 16px;
  color: var(--lr-text-secondary);
  cursor: pointer;

  &:hover {
    color: var(--lr-primary);
  }
}

.session-list {
  flex: 1;
  padding: 8px;
}

.session-item {
  position: relative;
  padding: 10px 12px;
  margin-bottom: 4px;
  cursor: pointer;
  border-radius: var(--lr-radius-sm);

  &:hover {
    background: #eef2f7;

    .session-del {
      opacity: 1;
    }
  }

  &.active {
    background: #e8f1fe;
  }
}

.session-title {
  overflow: hidden;
  font-size: 13px;
  color: var(--lr-text-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-meta {
  display: flex;
  justify-content: space-between;
  margin-top: 4px;
  font-size: 12px;
  color: var(--lr-text-secondary);
}

.session-del {
  position: absolute;
  top: 8px;
  right: 8px;
  font-size: 13px;
  color: var(--lr-text-secondary);
  opacity: 0;
  transition: opacity 0.15s;

  &:hover {
    color: var(--lr-danger);
  }
}

/* ==================== 聊天区 ==================== */
.chat-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  padding: 0 16px;
  border-bottom: 1px solid var(--lr-border);
}

.chat-head-left {
  display: flex;
  gap: 12px;
  align-items: center;
}

.chat-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--lr-text-primary);
}

.chat-body {
  flex: 1;
  padding: 20px 24px;
  overflow-y: auto;
}

/* ---------- 空态 ---------- */
.chat-empty {
  padding-top: 60px;
  text-align: center;

  h3 {
    margin: 12px 0 8px;
    font-size: 18px;
    color: var(--lr-text-primary);
  }

  p {
    font-size: 13px;
    line-height: 1.8;
  }
}

.suggest-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  max-width: 640px;
  margin: 24px auto 0;
}

.suggest-item {
  padding: 8px 14px;
  font-size: 13px;
  color: var(--lr-text-regular);
  cursor: pointer;
  background: #f5f7fa;
  border: 1px solid var(--lr-border);
  border-radius: 18px;
  transition: all 0.15s;

  &:hover {
    color: var(--lr-primary);
    background: #ecf5ff;
    border-color: var(--lr-primary);
  }
}

/* ---------- 消息 ---------- */
.msg-row {
  display: flex;
  gap: 12px;
  margin-bottom: 22px;

  &.user {
    flex-direction: row-reverse;
  }
}

.msg-avatar {
  flex: 0 0 32px;
  width: 32px;
  height: 32px;
  font-size: 12px;
  font-weight: 600;
  line-height: 32px;
  color: #fff;
  text-align: center;
  border-radius: 6px;

  &.user {
    background: var(--lr-primary);
  }

  &.assistant {
    background: linear-gradient(135deg, #67c23a, #409eff);
  }
}

.msg-main {
  max-width: 78%;
  min-width: 0;
}

.msg-bubble {
  padding: 10px 14px;
  font-size: 14px;
  line-height: 1.75;
  border-radius: var(--lr-radius);
  word-break: break-word;
}

.user-bubble {
  color: #fff;
  white-space: pre-wrap;
  background: var(--lr-primary);
}

.ai-bubble {
  background: #f5f7fa;
  border: 1px solid #ebeef5;
}

/* ---------- markdown 渲染样式（v-html 生成，不可 scoped 到子元素之外，用 :deep） ---------- */
.markdown-body {
  :deep(.md-p) {
    margin: 0 0 6px;
  }

  :deep(.md-p:last-child) {
    margin-bottom: 0;
  }

  :deep(.md-list) {
    margin: 4px 0 8px;
    padding-left: 22px;
  }

  :deep(.md-list li) {
    margin-bottom: 3px;
  }

  :deep(.cite-badge) {
    display: inline-block;
    min-width: 17px;
    height: 17px;
    padding: 0 4px;
    margin: 0 2px;
    font-size: 11px;
    line-height: 17px;
    color: var(--lr-primary);
    text-align: center;
    cursor: pointer;
    user-select: none;
    background: #ecf5ff;
    border: 1px solid #b3d8ff;
    border-radius: 4px;
    transition: all 0.15s;

    &:hover {
      color: #fff;
      background: var(--lr-primary);
    }
  }
}

/* ---------- 检索中提示 ---------- */
.retrieving {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--lr-text-secondary);
  background: #f5f7fa;
  border: 1px solid #ebeef5;
  border-radius: var(--lr-radius);
}

/* ---------- 打字光标 ---------- */
.type-caret {
  display: inline-block;
  width: 2px;
  height: 14px;
  margin-left: 2px;
  vertical-align: -2px;
  background: var(--lr-primary);
  animation: caret-blink 1s steps(2, start) infinite;
}

@keyframes caret-blink {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0;
  }
}

/* ---------- 打字动画 ---------- */
.typing {
  display: flex;
  gap: 4px;
  padding: 6px 4px;

  span {
    width: 6px;
    height: 6px;
    background: var(--lr-text-secondary);
    border-radius: 50%;
    animation: typing-bounce 1.2s infinite;

    &:nth-child(2) {
      animation-delay: 0.15s;
    }

    &:nth-child(3) {
      animation-delay: 0.3s;
    }
  }
}

@keyframes typing-bounce {
  0%,
  60%,
  100% {
    opacity: 0.3;
    transform: translateY(0);
  }

  30% {
    opacity: 1;
    transform: translateY(-4px);
  }
}

/* ---------- 引用（默认收起，只占一行） ---------- */
.cite-block {
  margin-top: 8px;
  overflow: hidden;
  border: 1px solid #ebeef5;
  border-radius: var(--lr-radius);
}

.cite-head {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 6px 10px;
  cursor: pointer;
  user-select: none;
  background: #fafbfc;
  transition: background 0.15s;

  &:hover {
    background: #f2f4f7;
  }
}

.cite-caret {
  font-size: 12px;
  color: var(--lr-text-secondary);
  transition: transform 0.18s;

  &.open {
    transform: rotate(90deg);
  }
}

.cite-head-text {
  flex: 0 0 auto;
  font-size: 12px;
  font-weight: 600;
  color: var(--lr-text-regular);
}

/* 收起状态下的文档名预览：单行省略，不占高度 */
.cite-preview {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: 12px;
  color: var(--lr-text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cite-list {
  border-top: 1px solid #f2f3f5;
}

.cite-row {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 6px 10px;
  font-size: 12px;
  border-bottom: 1px solid #f7f8fa;
  transition: background 0.25s;

  &:last-child {
    border-bottom: none;
  }

  &.highlight {
    background: #fdf6ec;
  }
}

.cite-index {
  flex: 0 0 16px;
  width: 16px;
  height: 16px;
  font-size: 10px;
  line-height: 16px;
  color: #fff;
  text-align: center;
  background: var(--lr-primary);
  border-radius: 3px;
}

.cite-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: var(--lr-text-regular);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cite-cat {
  flex: 0 0 auto;
  transform: scale(0.9);
}

.cite-score {
  flex: 0 0 auto;
  font-family: Consolas, Monaco, monospace;
  color: var(--lr-text-secondary);
}

.cite-info {
  flex: 0 0 auto;
  color: var(--lr-text-secondary);
  cursor: help;

  &:hover {
    color: var(--lr-primary);
  }
}

/* ---------- 操作条 ---------- */
.msg-actions {
  display: flex;
  gap: 4px;
  align-items: center;
  margin-top: 6px;
}

.msg-cost {
  margin-left: 8px;
  font-size: 11px;
  color: var(--lr-text-secondary);
}

/* ---------- 错误 ---------- */
.chat-error {
  max-width: 560px;
  margin: 0 auto 16px;
}

/* ---------- 输入区 ---------- */
.chat-input {
  padding: 12px 24px 16px;
  border-top: 1px solid var(--lr-border);
}

.chat-input-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
</style>
