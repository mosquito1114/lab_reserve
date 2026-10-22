import { post } from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/type'

/**
 * 消息通知接口（api.md §8）。
 *
 * 收件箱是「Redis 索引 + DB 正文」：`get_page` 支持类型 / 已读 / 时间筛选，
 * 未读数单独一个轻接口给顶栏铃铛轮询。
 */

/** 消息类型（与后端 MessageType 一致） */
export type MessageTypeCode =
  | 'RESERVATION_AUDIT'
  | 'QUEUE_POSITION'
  | 'CANDIDATE_NOTICE'
  | 'REPAIR'
  | 'CHANGE_AUDIT'
  | 'SYSTEM'

/** 邮件状态 */
export type EmailStateCode = 'SENDING' | 'OK' | 'DEAD'

/** 收件箱筛选条件 */
export interface MessageQuery extends PageQuery {
  type?: string | null
  /** 0 未读 / 1 已读 */
  isRead?: number | null
  beginTime?: string | null
  endTime?: string | null
}

/** 消息 */
export interface MessageItem {
  id: number
  type: MessageTypeCode | string
  typeName: string
  title: string
  content: string
  bizType: string | null
  bizId: number | null
  isRead: number
  createdAt: string
}

/** 邮件投递记录筛选条件 */
export interface DeliveryLogQuery extends PageQuery {
  emailState?: string | null
  receiverKeyword?: string | null
  beginTime?: string | null
  endTime?: string | null
}

/** 邮件投递记录 */
export interface DeliveryLogItem {
  messageId: number
  receiverId: number
  receiverName: string | null
  receiverUsername: string | null
  receiverEmail: string | null
  title: string
  emailState: EmailStateCode | null
  emailLastError: string | null
  createdAt: string
  updatedAt: string
}

/** 消息类型下拉选项（前端展示用，与后端枚举保持一致） */
export const MESSAGE_TYPE_OPTIONS: { value: MessageTypeCode; label: string }[] = [
  { value: 'RESERVATION_AUDIT', label: '预约审核' },
  { value: 'QUEUE_POSITION', label: '排队提醒' },
  { value: 'CANDIDATE_NOTICE', label: '候补通知' },
  { value: 'REPAIR', label: '工单通知' },
  { value: 'CHANGE_AUDIT', label: '调整审核' },
  { value: 'SYSTEM', label: '系统通知' }
]

/** 收件箱分页 */
export function getMessagePage(data: MessageQuery) {
  return post<PageResult<MessageItem>>('/message/get_page', data)
}

/** 消息详情（会自动置已读） */
export function getMessageInfo(id: number) {
  return post<MessageItem>('/message/get_info', { id })
}

/** 未读数（铃铛角标） */
export function getUnreadCount() {
  return post<{ count: number }>('/message/get_unread_count', {}, { silent: true })
}

/** 单条已读 */
export function readMessage(id: number) {
  return post<void>('/message/read', { id }, { silent: true })
}

/** 全部已读 */
export function readAllMessages() {
  return post<void>('/message/read_all')
}

/** 删除单条 */
export function deleteMessage(id: number) {
  return post<void>('/message/delete', { id })
}

/** 一键清空 */
export function clearMessages() {
  return post<void>('/message/clear')
}

/** 邮件投递记录（仅系统管理员） */
export function getDeliveryLogPage(data: DeliveryLogQuery) {
  return post<PageResult<DeliveryLogItem>>('/message/get_delivery_log_page', data)
}

/** 发送系统通知（管理员，需 message:send） */
export function sendSystemMessage(data: { receiverIds: number[]; title: string; content: string }) {
  return post<number>('/message/send', data)
}

/** 发送测试邮件（联调 SMTP） */
export function sendTestEmail(to: string) {
  return post<void>('/message/send_test_email', { to })
}
