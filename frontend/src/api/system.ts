import { post } from '@/utils/request'

/**
 * 系统接口（api.md §0.7）。
 *
 * - `health`：免登录，只返回「活着 / 降级」与版本信息（docker 健康检查用）
 * - `monitor`：需要 `system:monitor:view`（默认只有系统管理员），返回 DB / Redis / 运行时长等运维信息
 */

/** 健康检查结果（公开） */
export interface SystemHealth {
  status: 'UP' | 'DEGRADED'
  application: string
  version: string
  serverTime: string
}

/** 系统运行状态（仅系统管理员） */
export interface SystemMonitor extends SystemHealth {
  profiles: string
  /** UP 或 DOWN: 原因 */
  database: string
  /** UP 或 DOWN: 原因 */
  redis: string
  uptimeSeconds: number
}

/** 健康检查（免登录；出错时不自动弹提示） */
export function getHealth() {
  return post<SystemHealth>('/system/health', {}, { silent: true })
}

/** 系统运行状态（仅 `system:monitor:view`；出错时不自动弹提示，由页面自行展示） */
export function getMonitor() {
  return post<SystemMonitor>('/system/monitor', {}, { silent: true })
}
