/**
 * 轻量富文本渲染（给 LLM 回答用）。
 *
 * <h3>为什么不引 markdown 库</h3>
 * 项目当前没有 markdown 依赖。而 RAG 回答的格式其实很有限：
 * 加粗、换行、`·`/`1.` 列表、`[n]` 引用角标。
 * 为这点需求引 `marked` + `dompurify`（~60KB）并引入 XSS 面不划算。
 *
 * <h3>安全模型（零 XSS）</h3>
 * <ol>
 *   <li><b>先对整段原文做 HTML 转义</b>（`<` `>` `&` `"` `'` → 实体）</li>
 *   <li>再在<b>已转义</b>的文本上做正则替换，插入<b>我们自己生成</b>的标签</li>
 * </ol>
 * 因此模型输出里的任何 `<script>` / `onerror=` 都已被转成文本，
 * 不存在执行风险。切勿把外部 HTML 直接喂进本函数。
 *
 * <h3>支持的语法</h3>
 * <ul>
 *   <li>`**加粗**` → {@code <strong>}</li>
 *   <li>`· xxx` / `- xxx` / `* xxx` → 无序列表</li>
 *   <li>`1. xxx` / `1、xxx` → 有序列表</li>
 *   <li>`[1]` `[2]`（≤ maxCitation）→ 可点击引用角标 {@code <span class="cite-badge" data-cite="1">}</li>
 *   <li>空行 → 段落分隔</li>
 * </ul>
 */

/** HTML 转义（唯一的安全边界，必须先做） */
export function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/** 无序列表行：· xxx / • xxx / - xxx / * xxx */
const UL_RE = /^[·•\-*]\s+(.+)$/
/** 有序列表行：1. xxx / 1、xxx / 1) xxx */
const OL_RE = /^(\d+)[.、)]\s+(.+)$/

/**
 * 行内渲染：加粗 + 引用角标。输入必须是<b>已转义</b>的文本。
 *
 * @param escaped   已转义的行内文本
 * @param maxCitation 引用最大编号（用于把 "数组[0]" 这类误伤排除）
 */
function renderInline(escaped: string, maxCitation: number): string {
  let s = escaped

  // **加粗** → <strong>
  s = s.replace(/\*\*([^*\n]+?)\*\*/g, '<strong>$1</strong>')

  // [n] 引用角标：n 在 1..maxCitation 范围内才替换，
  // 避免把数组下标、代码里的 [0] 也变成角标
  if (maxCitation > 0) {
    s = s.replace(/\[(\d{1,2})\]/g, (raw, numStr: string) => {
      const n = Number(numStr)
      if (n < 1 || n > maxCitation) return raw
      return `<span class="cite-badge" data-cite="${n}" role="button" tabindex="0">${n}</span>`
    })
  }

  return s
}

/**
 * 把 LLM 回答渲染成安全 HTML。
 *
 * @param raw          模型原始输出（未转义）
 * @param maxCitation  引用条目数（用于限定 [n] 合法范围）；默认 0 表示不渲染角标
 */
export function renderRichText(raw: string, maxCitation = 0): string {
  if (!raw) return ''

  // ① 安全边界：先整段转义
  const escaped = escapeHtml(raw)

  const lines = escaped.split('\n')
  const out: string[] = []
  let listType: 'ul' | 'ol' | null = null

  const closeList = () => {
    if (listType) {
      out.push(`</${listType}>`)
      listType = null
    }
  }

  for (const rawLine of lines) {
    const line = rawLine.trim()

    // 空行 → 结束当前列表，作为段落分隔
    if (!line) {
      closeList()
      continue
    }

    const ulMatch = line.match(UL_RE)
    if (ulMatch) {
      if (listType !== 'ul') {
        closeList()
        out.push('<ul class="md-list">')
        listType = 'ul'
      }
      out.push(`<li>${renderInline(ulMatch[1], maxCitation)}</li>`)
      continue
    }

    const olMatch = line.match(OL_RE)
    if (olMatch) {
      if (listType !== 'ol') {
        closeList()
        out.push('<ol class="md-list">')
        listType = 'ol'
      }
      out.push(`<li>${renderInline(olMatch[2], maxCitation)}</li>`)
      continue
    }

    // 普通段落
    closeList()
    out.push(`<p class="md-p">${renderInline(line, maxCitation)}</p>`)
  }
  closeList()

  return out.join('')
}

/**
 * 从 HTML 容器里取被点击的引用角标编号。
 *
 * <p>用事件委托（在容器上监听一次 click）而不是给每个角标绑事件，
 * 因为流式渲染期间 DOM 会被频繁替换。
 *
 * @returns 角标编号；没点中角标返回 null
 */
export function readCiteIndex(target: EventTarget | null): number | null {
  if (!(target instanceof HTMLElement)) return null
  const el = target.closest('.cite-badge') as HTMLElement | null
  if (!el) return null
  const n = Number(el.dataset.cite)
  return Number.isFinite(n) ? n : null
}

/** 纯文本预览（去掉 markdown 标记，用于会话标题等） */
export function stripMarkdown(raw: string): string {
  return raw
    .replace(/\*\*([^*]+?)\*\*/g, '$1')
    .replace(/\[(\d{1,2})\]/g, '')
    .replace(/^[·•\-*]\s+/gm, '')
    .replace(/^\d+[.、)]\s+/gm, '')
    .replace(/\s+/g, ' ')
    .trim()
}
