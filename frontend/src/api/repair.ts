import request, { post } from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/type'

/**
 * 设备运维接口（api.md §7）。
 *
 * 唯一的多文件接口是 `/file/upload`（multipart），其余都是 POST + JSON。
 */

/** 上传结果 */
export interface FileUploadResult {
  url: string
  objectKey: string
  name: string
  size: number
  contentType: string
}

/** 上传故障截图（`bizType` 默认 REPAIR） */
export async function uploadImage(file: File, bizType = 'REPAIR'): Promise<FileUploadResult> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('bizType', bizType)
  const response = await request.post<{ data: FileUploadResult }>('/file/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
  return response.data.data
}

export type RepairStatus =
  | 'SUBMITTED'
  | 'REJECTED'
  | 'ASSIGNED'
  | 'REPAIRING'
  | 'REPAIRED'
  | 'ACCEPTED'
  | 'REOPENED'

export interface RepairCreatePayload {
  equipmentId: number
  faultDesc: string
  faultScene: string
  faultImages?: string[]
}

export interface RepairQuery extends PageQuery {
  status?: string | null
  priority?: number | null
  equipmentId?: number | null
  equipmentNo?: string | null
  submitterId?: number | null
  assigneeId?: number | null
  onlyMine?: boolean | null
  beginTime?: string | null
  endTime?: string | null
}

export interface RepairOrderItem {
  id: number
  orderNo: string
  submitterId: number
  submitterName: string | null
  equipmentId: number
  equipmentNo: string | null
  equipmentName: string | null
  labNo: string | null
  faultDesc: string
  faultScene: string
  priority: number
  priorityName: string
  status: RepairStatus
  statusName: string
  reviewerId: number | null
  reviewerName: string | null
  reviewTime: string | null
  reviewOpinion: string | null
  assigneeId: number | null
  assigneeName: string | null
  assignTime: string | null
  acceptTime: string | null
  acceptResult: number | null
  acceptOpinion: string | null
  archiveTime: string | null
  createdAt: string
}

export interface RepairRecordItem {
  id: number
  orderId: number
  maintainerId: number
  maintainerName: string | null
  status: number
  statusName: string
  repairDesc: string | null
  partsReplaced: string | null
  createdAt: string
}

export interface RepairOrderDetail extends RepairOrderItem {
  faultImages: string[]
  records: RepairRecordItem[]
}

export const REPAIR_STATUS_OPTIONS = [
  { value: 'SUBMITTED', label: '待复核', tag: 'warning' },
  { value: 'ASSIGNED', label: '已派单', tag: 'primary' },
  { value: 'REPAIRING', label: '维修中', tag: 'warning' },
  { value: 'REPAIRED', label: '待验收', tag: 'primary' },
  { value: 'ACCEPTED', label: '已验收', tag: 'success' },
  { value: 'REOPENED', label: '已重开', tag: 'danger' },
  { value: 'REJECTED', label: '已驳回', tag: 'info' }
] as const

export const REPAIR_PRIORITY_OPTIONS = [
  { value: 1, label: '低', tag: 'info' },
  { value: 2, label: '中', tag: 'primary' },
  { value: 3, label: '高', tag: 'warning' },
  { value: 4, label: '紧急', tag: 'danger' }
] as const

export function repairStatusTag(status: string): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  return (REPAIR_STATUS_OPTIONS.find((item) => item.value === status)?.tag ?? 'info') as
    | 'success'
    | 'warning'
    | 'danger'
    | 'info'
    | 'primary'
}

export function repairPriorityTag(priority: number): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  return (REPAIR_PRIORITY_OPTIONS.find((item) => item.value === priority)?.tag ?? 'info') as
    | 'success'
    | 'warning'
    | 'danger'
    | 'info'
    | 'primary'
}

export function createRepair(data: RepairCreatePayload) {
  return post<RepairOrderItem>('/repair_order/create', data)
}

export function getRepairPage(data: RepairQuery) {
  return post<PageResult<RepairOrderItem>>('/repair_order/get_page', data)
}

export function getMySubmittedRepairs(data: RepairQuery) {
  return post<PageResult<RepairOrderItem>>('/repair_order/get_my_submitted_page', data)
}

export function getMyAssignedRepairs(data: RepairQuery) {
  return post<PageResult<RepairOrderItem>>('/repair_order/get_my_assigned_page', data)
}

export function getRepairInfo(id: number) {
  return post<RepairOrderDetail>('/repair_order/get_info', { id })
}

export function reviewRepair(data: {
  id: number
  result: 'CONFIRMED' | 'REJECTED'
  priority?: number | null
  equipmentStatus?: number | null
  opinion?: string | null
}) {
  return post<void>('/repair_order/review', data)
}

export function assignRepair(data: { id: number; assigneeId: number; opinion?: string | null }) {
  return post<void>('/repair_order/assign', data)
}

export function updateRepairProgress(data: { id: number; repairDesc?: string | null; partsReplaced?: string | null }) {
  return post<void>('/repair_order/update_progress', data)
}

export function completeRepair(data: { id: number; repairDesc: string; partsReplaced?: string | null }) {
  return post<void>('/repair_order/complete', data)
}

export function acceptRepair(data: { id: number; result: 'PASS' | 'FAIL'; opinion?: string | null }) {
  return post<void>('/repair_order/accept', data)
}

export function getRepairRecords(orderId: number) {
  return post<RepairRecordItem[]>('/repair_record/get_list', { id: orderId })
}
