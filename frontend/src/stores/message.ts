import { ref } from 'vue'
import { defineStore } from 'pinia'
import * as messageApi from '@/api/message'

/**
 * 消息角标状态。
 *
 * 顶栏铃铛与消息中心共用一个未读数：
 *   - 登录后由 MainLayout 启动 30s 轮询
 *   - 消息中心里标记已读 / 全部已读 / 删除 / 清空之后调用 refresh()
 * 这样角标不用等下一次轮询才变。
 */
export const useMessageStore = defineStore('message', () => {
  const unread = ref(0)
  /** 是否已成功拉过一次（避免首帧闪 0） */
  const loaded = ref(false)

  async function fetchUnread(): Promise<void> {
    try {
      const result = await messageApi.getUnreadCount()
      unread.value = result.count ?? 0
      loaded.value = true
    } catch {
      // 静默失败：角标不是关键路径，401 会由 request 拦截器统一处理
    }
  }

  /** 本地减 1（点开一条消息后立刻生效，再异步校准） */
  function decrease(step = 1): void {
    unread.value = Math.max(0, unread.value - step)
  }

  function reset(): void {
    unread.value = 0
    loaded.value = false
  }

  return { unread, loaded, fetchUnread, decrease, reset }
})
