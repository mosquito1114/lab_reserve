import { post, postBlob } from '@/utils/request'

/**
 * 统计报表接口（api.md §9）。
 *
 * 口径全部在后端，前端只负责画图与导出；导出与页面走同一套聚合，
 * 保证「页面数字 = Excel 数字」。
 */

export interface StatisticsQuery {
  beginDate?: string | null
  endDate?: string | null
  college?: string | null
  experimentTypeId?: number | null
  labId?: number | null
  groupBy?: string | null
  type?: string | null
}

export interface StatisticsOverview {
  reservationTotal: number
  pendingAudit: number
  todayReservation: number
  queueTotal: number
  repairPending: number
  labTotal: number
  equipmentTotal: number
  unreadMessage: number
}

export interface LabOccupancy {
  labId: number
  labNo: string
  labName: string
  openSlots: number
  usedSlots: number
  occupancyRate: number
}

export interface EquipmentOccupancy {
  equipmentId: number
  resourceNo: string
  name: string
  stock: number
  usedSlots: number
  totalSlots: number
  occupancyRate: number
}

export interface Occupancy {
  overall: number
  byLab: LabOccupancy[]
  byEquipment: EquipmentOccupancy[]
}

export interface ExperimentTypeRatioItem {
  experimentTypeId: number
  code: string
  name: string
  count: number
  ratio: number
}

export interface ExperimentTypeRatio {
  total: number
  items: ExperimentTypeRatioItem[]
}

export interface SuccessRate {
  total: number
  approved: number
  rejected: number
  canceled: number
  fromQueue: number
  successRate: number
  rejectRate: number
  cancelRate: number
}

export interface ResourceRatioItem {
  resourceType: number
  resourceId: number
  resourceNo: string | null
  resourceName: string | null
  count: number
  ratio: number
}

export interface ResourceRatio {
  items: ResourceRatioItem[]
}

export interface TrendItem {
  period: string
  total: number
  approved: number
  rejected: number
  canceled: number
}

export interface ReservationTrend {
  groupBy: string
  items: TrendItem[]
}

export const EXPORT_TYPES = [
  { value: 'reservation', label: '预约明细' },
  { value: 'occupancy', label: '资源占用率' },
  { value: 'experiment-type', label: '实验类型占比' },
  { value: 'success-rate', label: '预约成功率' },
  { value: 'resource-ratio', label: '资源预约占比' }
] as const

export function getOverview() {
  return post<StatisticsOverview>('/statistics/get_overview')
}

export function getOccupancy(data: StatisticsQuery = {}) {
  return post<Occupancy>('/statistics/get_occupancy', data)
}

export function getExperimentTypeRatio(data: StatisticsQuery = {}) {
  return post<ExperimentTypeRatio>('/statistics/get_experiment_type_ratio', data)
}

export function getSuccessRate(data: StatisticsQuery = {}) {
  return post<SuccessRate>('/statistics/get_success_rate', data)
}

export function getResourceRatio(data: StatisticsQuery = {}) {
  return post<ResourceRatio>('/statistics/get_resource_ratio', data)
}

export function getReservationTrend(data: StatisticsQuery = {}) {
  return post<ReservationTrend>('/statistics/get_reservation_trend', data)
}

export function exportStatistics(data: StatisticsQuery) {
  return postBlob('/statistics/export', data)
}
