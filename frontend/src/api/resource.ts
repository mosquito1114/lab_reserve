import { post } from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/type'

/**
 * 资源管理接口（api.md §3）。
 *
 * - 字典：实验类型 / 时段（`dict:list` 查看，`dict:manage` 维护）
 * - 实验室 / 设备：列表与可用性登录即可看（`resource:*:list`），写操作要各自权限
 * - 资源更新日志：`resource:log:list`
 */

// ============================================================
// 字典
// ============================================================

export interface ExperimentTypeItem {
  id: number
  code: string
  name: string
  sort: number
  status: number
}

export interface ExperimentTypePayload {
  id?: number | null
  code: string
  name: string
  sort?: number | null
  status?: number | null
}

export interface TimeSlotItem {
  id: number
  code: string
  name: string
  startTime: string
  endTime: string
  sort: number
  status: number
}

export interface TimeSlotPayload {
  id?: number | null
  code: string
  name: string
  startTime: string
  endTime: string
  sort?: number | null
  status?: number | null
}

export function listExperimentTypes(data: { status?: number | null } = {}) {
  return post<ExperimentTypeItem[]>('/experiment_type/get_list', data)
}

export function createExperimentType(data: ExperimentTypePayload) {
  return post<number>('/experiment_type/create', data)
}

export function updateExperimentType(data: ExperimentTypePayload) {
  return post<void>('/experiment_type/update', data)
}

export function deleteExperimentType(id: number) {
  return post<void>('/experiment_type/delete', { id })
}

export function listTimeSlots(data: { status?: number | null } = {}) {
  return post<TimeSlotItem[]>('/time_slot/get_list', data)
}

export function createTimeSlot(data: TimeSlotPayload) {
  return post<number>('/time_slot/create', data)
}

export function updateTimeSlot(data: TimeSlotPayload) {
  return post<void>('/time_slot/update', data)
}

export function deleteTimeSlot(id: number) {
  return post<void>('/time_slot/delete', { id })
}

// ============================================================
// 实验室
// ============================================================

export interface LaboratoryQuery extends PageQuery {
  keyword?: string | null
  location?: string | null
  experimentTypeId?: number | null
  minCapacity?: number | null
  status?: number | null
}

export interface LaboratoryItem {
  id: number
  labNo: string
  name: string
  location: string
  openTimeStart: string
  openTimeEnd: string
  openWeekdays: string
  openWeekdaysText: string
  capacity: number
  experimentTypeId: number
  experimentTypeName: string | null
  status: number
  description: string | null
  createdAt: string
  updatedAt: string
}

export interface LaboratoryDetail extends LaboratoryItem {
  equipments: EquipmentItem[]
}

export interface LaboratoryPayload {
  id?: number | null
  labNo: string
  name: string
  location: string
  openTimeStart: string
  openTimeEnd: string
  openWeekdays: string
  capacity: number
  experimentTypeId: number
  status?: number | null
  description?: string | null
  updateReason: string
}

export interface LaboratoryOption {
  id: number
  labNo: string
  name: string
  capacity: number
  experimentTypeId: number
  status: number
}

export interface SlotAvailability {
  slotId: number
  slotName: string
  startTime: string
  endTime: string
  available: boolean
  reason: string | null
  queueCount: number
  myQueuePosition: number | null
}

export interface LaboratoryAvailability {
  labId: number
  labNo: string
  name: string
  date: string
  openWeekday: boolean
  openTimeText: string
  slots: SlotAvailability[]
}

export function getLaboratoryPage(data: LaboratoryQuery) {
  return post<PageResult<LaboratoryItem>>('/laboratory/get_page', data)
}

export function getLaboratoryInfo(id: number) {
  return post<LaboratoryDetail>('/laboratory/get_info', { id })
}

export function createLaboratory(data: LaboratoryPayload) {
  return post<number>('/laboratory/create', data)
}

export function updateLaboratory(data: LaboratoryPayload) {
  return post<void>('/laboratory/update', data)
}

export function updateLaboratoryStatus(data: { id: number; status: number; updateReason: string }) {
  return post<void>('/laboratory/update_status', data)
}

export function listLaboratoryOptions(data: { status?: number | null; experimentTypeId?: number | null } = {}) {
  return post<LaboratoryOption[]>('/laboratory/list_option', data)
}

export function getLaboratoryAvailability(data: { id: number; date: string }) {
  return post<LaboratoryAvailability>('/laboratory/get_availability', data)
}

// ============================================================
// 设备
// ============================================================

export interface EquipmentQuery extends PageQuery {
  resourceNo?: string | null
  keyword?: string | null
  labId?: number | null
  model?: string | null
  status?: number | null
}

export interface EquipmentItem {
  id: number
  resourceNo: string
  name: string
  model: string | null
  stock: number
  labId: number
  labNo: string | null
  labName: string | null
  status: number
  statusName: string | null
  nextCalibrationDate: string | null
  remark: string | null
  createdAt: string
  updatedAt: string
}

export interface EquipmentPayload {
  id?: number | null
  resourceNo: string
  name: string
  model?: string | null
  stock: number
  labId: number
  nextCalibrationDate?: string | null
  remark?: string | null
  updateReason: string
}

export interface EquipmentAvailabilitySlot {
  slotId: number
  slotName: string
  startTime: string
  endTime: string
  usedCount: number
  remainCount: number
  available: boolean
  queueCount: number
  myQueuePosition: number | null
}

export interface EquipmentAvailability {
  equipmentId: number
  resourceNo: string
  name: string
  stock: number
  date: string
  slots: EquipmentAvailabilitySlot[]
}

export const EQUIPMENT_STATUS_OPTIONS = [
  { value: 1, label: '正常', tag: 'success' },
  { value: 2, label: '维修中', tag: 'warning' },
  { value: 3, label: '报废', tag: 'info' },
  { value: 0, label: '停用', tag: 'danger' }
] as const

export function getEquipmentPage(data: EquipmentQuery) {
  return post<PageResult<EquipmentItem>>('/equipment/get_page', data)
}

export function getEquipmentInfo(id: number) {
  return post<EquipmentItem>('/equipment/get_info', { id })
}

export function createEquipment(data: EquipmentPayload) {
  return post<number>('/equipment/create', data)
}

export function updateEquipment(data: EquipmentPayload) {
  return post<void>('/equipment/update', data)
}

export function updateEquipmentStatus(data: { id: number; status: number; updateReason: string }) {
  return post<void>('/equipment/update_status', data)
}

export function listEquipmentOptions(data: { labId?: number | null; onlyAvailable?: boolean } = {}) {
  return post<EquipmentItem[]>('/equipment/list_option', data)
}

export function getEquipmentAvailability(data: { id: number; date: string; slotIds?: number[] }) {
  return post<EquipmentAvailability>('/equipment/get_availability', data)
}

// ============================================================
// 资源更新日志
// ============================================================

export interface ResourceLogQuery extends PageQuery {
  resourceType?: number | null
  resourceId?: number | null
  operation?: number | null
  operatorId?: number | null
  beginTime?: string | null
  endTime?: string | null
}

export interface ResourceLogItem {
  id: number
  resourceType: number
  resourceTypeName: string
  resourceId: number
  resourceNo: string | null
  resourceName: string | null
  operation: number
  operationName: string
  beforeJson: string | null
  afterJson: string | null
  updateReason: string
  operatorId: number
  operatorName: string | null
  createdAt: string
}

export function getResourceLogPage(data: ResourceLogQuery) {
  return post<PageResult<ResourceLogItem>>('/resource_log/get_page', data)
}
