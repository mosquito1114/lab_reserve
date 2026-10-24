import { post } from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/type'

/**
 * 预约管理接口（api.md §4、§5）。
 *
 * 关键点：
 * - `check` 是只读预检，返回 `conflict.code` 决定前端提示（`SLOT_OCCUPIED` / `STOCK_NOT_ENOUGH` 可排队，迭代 6 开放）
 * - `create` 加锁提交，冲突返回 409 + `data.conflictCode`
 * - 调整申请「通过前不改业务字段」，详情里用 `changePending` 展示
 */

export type ReservationStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELED'
  | 'CHANGE_PENDING'
  | 'EXPIRED'

export interface ReservationEquipmentPayload {
  equipmentId: number
  equipmentCount: number
}

export interface ReservationSubmitPayload {
  resourceType: number
  labId?: number | null
  equipmentId?: number | null
  equipments?: ReservationEquipmentPayload[]
  reserveDate: string
  slotId: number
  usePeople: number
  experimentTypeId: number
  classIds?: number[]
  courseName?: string | null
  purpose: string
}

export interface ReservationConflict {
  code: string
  message: string
  queueCount: number
  myQueuePosition: number | null
}

export interface ReservationCheckResult {
  available: boolean
  conflict: ReservationConflict | null
  canQueue: boolean
  banInfo: { startTime: string; endTime: string; reason: string } | null
}

export interface ReservationQuery extends PageQuery {
  status?: string | null
  resourceType?: number | null
  labId?: number | null
  equipmentId?: number | null
  reserveDateBegin?: string | null
  reserveDateEnd?: string | null
  experimentTypeId?: number | null
  college?: string | null
  applicantKeyword?: string | null
}

export interface ReservationItem {
  id: number
  reserveNo: string
  status: ReservationStatus
  statusName: string
  applicantId: number
  applicantName: string | null
  applicantCollege: string | null
  resourceType: number
  resourceName: string | null
  labId: number | null
  labNo: string | null
  labLocation: string | null
  equipmentId: number | null
  equipmentNo: string | null
  equipmentCount: number | null
  reserveDate: string
  slotId: number
  slotName: string
  startTime: string
  endTime: string
  usePeople: number
  experimentTypeId: number
  experimentTypeName: string | null
  courseName: string | null
  purpose: string
  createdAt: string
}

export interface ReservationEquipmentItem {
  equipmentId: number
  resourceNo: string | null
  name: string | null
  model: string | null
  equipmentCount: number
}

export interface ReservationChangeItem {
  id: number
  reservationId: number
  reserveNo: string | null
  applicantId: number
  applicantName: string | null
  changeType: number
  changeTypeName: string
  reason: string
  status: number
  statusName: string
  beforeJson: string
  afterJson: string
  summary: string
  auditUserId: number | null
  auditUserName: string | null
  auditTime: string | null
  auditOpinion: string | null
  createdAt: string
}

export interface ReservationDetail extends ReservationItem {
  equipments: ReservationEquipmentItem[] | null
  auditUserId: number | null
  auditUserName: string | null
  auditTime: string | null
  rejectReason: string | null
  fromQueue: number
  changePending: ReservationChangeItem | null
}

export interface TimetableItem {
  reservationId: number
  reserveNo: string
  labId: number
  labNo: string
  labName: string
  labLocation: string
  reserveDate: string
  slotName: string
  startTime: string
  endTime: string
  courseName: string | null
  teacherName: string | null
  status: string
}

export const RESERVATION_STATUS_OPTIONS = [
  { value: 'PENDING', label: '待审核', tag: 'warning' },
  { value: 'APPROVED', label: '已通过', tag: 'success' },
  { value: 'CHANGE_PENDING', label: '调整审批中', tag: 'primary' },
  { value: 'REJECTED', label: '已驳回', tag: 'danger' },
  { value: 'CANCELED', label: '已取消', tag: 'info' },
  { value: 'EXPIRED', label: '已过期', tag: 'info' }
] as const

/** 冲突码 → 中文说明（预检弹窗用） */
export const CONFLICT_LABELS: Record<string, string> = {
  SLOT_OCCUPIED: '该时段已被预约',
  STOCK_NOT_ENOUGH: '设备库存不足',
  EQUIPMENT_UNAVAILABLE: '设备当前不可借',
  LAB_DISABLED: '实验室已停用',
  CAPACITY_EXCEEDED: '使用人数超过容纳人数',
  NOT_IN_OPEN_TIME: '不在开放时间 / 开放日',
  QUEUE_BANNED: '处于排队禁入期',
  ALREADY_RESERVED: '你在这个时段已有预约',
  EXPERIMENT_TYPE_NOT_SUPPORTED: '该实验室不开展所选实验类型',
  LAB_RESERVE_TEACHER_ONLY: '实验室只能由教师为班级排课',
  CLASS_REQUIRED: '预约实验室必须选择上课班级'
}

export function checkReservation(data: ReservationSubmitPayload) {
  return post<ReservationCheckResult>('/reservation/check', data)
}

export function createReservation(data: ReservationSubmitPayload) {
  return post<ReservationDetail>('/reservation/create', data)
}

export function getReservationPage(data: ReservationQuery) {
  return post<PageResult<ReservationItem>>('/reservation/get_page', data)
}

export function getMyReservationPage(data: ReservationQuery) {
  return post<PageResult<ReservationItem>>('/reservation/get_my_page', data)
}

export function getPendingReservationPage(data: ReservationQuery) {
  return post<PageResult<ReservationItem>>('/reservation/get_pending_page', data)
}

export function getReservationInfo(id: number) {
  return post<ReservationDetail>('/reservation/get_info', { id })
}

export function auditReservation(data: { id: number; result: 'APPROVED' | 'REJECTED'; opinion?: string | null }) {
  return post<void>('/reservation/audit', data)
}

export function cancelReservation(data: { id: number; reason?: string | null }) {
  return post<void>('/reservation/cancel', data)
}

export function releaseReservation(data: { id: number; reason?: string | null }) {
  return post<void>('/reservation/release', data)
}

export function getMyTimetable(data: { beginDate?: string | null; endDate?: string | null; labId?: number | null } = {}) {
  return post<TimetableItem[]>('/reservation/get_my_timetable', data)
}

// ============================================================
// 调整申请
// ============================================================

export interface ReservationChangeCreatePayload {
  reservationId: number
  changeType: number
  reason: string
  newReserveDate?: string | null
  newSlotId?: number | null
  equipments?: ReservationEquipmentPayload[] | null
}

export function createReservationChange(data: ReservationChangeCreatePayload) {
  return post<number>('/reservation_change/create', data)
}

export function listChangesByReservation(reservationId: number) {
  return post<ReservationChangeItem[]>('/reservation_change/get_list_by_reservation', { id: reservationId })
}

export function getReservationChangePage(data: { pageNum?: number; pageSize?: number; status?: number | null; changeType?: number | null }) {
  return post<PageResult<ReservationChangeItem>>('/reservation_change/get_page', data)
}

export function auditReservationChange(data: { id: number; result: number; opinion?: string | null }) {
  return post<void>('/reservation_change/audit', data)
}

export function cancelReservationChange(id: number) {
  return post<void>('/reservation_change/cancel', { id })
}
