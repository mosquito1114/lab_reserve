import { post } from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/type'

/**
 * 排队与候补接口（api.md §6）。
 *
 * 队列本体在 Redis ZSet（member = userId，score = 入队时间），
 * 顺位一律实时算，所以页面拿到的 `position` 每次都是最新的。
 */

export interface QueueCreatePayload {
  resourceType: number
  labId?: number | null
  equipmentId?: number | null
  reserveDate: string
  slotId: number
}

export interface QueueQuery extends PageQuery {
  resourceType?: number | null
  labId?: number | null
  equipmentId?: number | null
  reserveDate?: string | null
  slotId?: number | null
  status?: string | null
}

export interface QueueItem {
  id: number
  queueNo: string
  resourceType: number
  labId: number | null
  equipmentId: number | null
  resourceName: string | null
  reserveDate: string
  slotId: number
  slotName: string
  startTime: string
  endTime: string
  applicantId: number
  applicantName: string | null
  status: string
  statusName: string
  position: number | null
  totalCount: number | null
  aheadCount: number | null
  deadline: string | null
  remainSeconds: number | null
  joinTime: string
  createdAt: string
}

export interface QueuePositionResult {
  queued: boolean
  queueId: number | null
  queueNo: string | null
  position: number | null
  totalCount: number | null
  status: string | null
  remainSeconds: number | null
}

export interface QueueNoticeItem {
  id: number
  queueId: number
  seq: number
  noticeTime: string
  deadline: string
  channelName: string
  action: number
  actionName: string
  actionTime: string | null
}

export interface QueueBanItem {
  id: number
  resourceType: number
  resourceName: string
  reason: string
  startTime: string
  endTime: string
  active: boolean
}

export interface QueueConfirmResult {
  reservationId: number
  reserveNo: string
  status: string
  message: string
}

export const QUEUE_STATUS_OPTIONS = [
  { value: 'QUEUING', label: '排队中', tag: 'info' },
  { value: 'NOTIFIED', label: '待确认', tag: 'warning' },
  { value: 'CONFIRMED', label: '已确认', tag: 'success' },
  { value: 'ABANDONED', label: '已放弃', tag: 'danger' },
  { value: 'TIMEOUT', label: '已超时', tag: 'danger' },
  { value: 'CANCELED', label: '已取消', tag: 'info' }
] as const

export function queueStatusTag(status: string): 'success' | 'warning' | 'danger' | 'info' {
  return (QUEUE_STATUS_OPTIONS.find((item) => item.value === status)?.tag ?? 'info') as
    | 'success'
    | 'warning'
    | 'danger'
    | 'info'
}

export function createQueue(data: QueueCreatePayload) {
  return post<QueueItem>('/queue/create', data)
}

export function getMyQueuePage(data: QueueQuery) {
  return post<PageResult<QueueItem>>('/queue/get_my_page', data)
}

export function getQueuePage(data: QueueQuery) {
  return post<PageResult<QueueItem>>('/queue/get_page', data)
}

export function getQueueInfo(id: number) {
  return post<QueueItem>('/queue/get_info', { id })
}

export function getMyQueuePosition(data: Omit<QueueCreatePayload, never>) {
  return post<QueuePositionResult>('/queue/get_my_position', data)
}

export function cancelQueue(id: number) {
  return post<void>('/queue/cancel', { id })
}

export function confirmQueue(id: number) {
  return post<QueueConfirmResult>('/queue/confirm', { id })
}

export function abandonQueue(data: { id: number; reason?: string | null }) {
  return post<void>('/queue/abandon', data)
}

export function getQueueNoticeList(queueId: number) {
  return post<QueueNoticeItem[]>('/queue/get_notice_list', { id: queueId })
}

export function getMyQueueBanList(activeOnly = false) {
  return post<QueueBanItem[]>('/queue_ban/get_my_list', { activeOnly })
}
