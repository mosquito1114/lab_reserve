import { post } from '@/utils/request'

/** 系统健康检查结果 */
export interface SystemHealth {
  status: 'UP' | 'DEGRADED'
  application: string
  version: string
  profiles: string
  serverTime: string
  /** UP 或 DOWN: 原因 */
  database: string
  /** UP 或 DOWN: 原因 */
  redis: string
  uptimeSeconds: number
}

/**
 * 健康检查。
 *
 * 由 docker-compose 的 backend 健康检查调用，前端首页也用它显示系统状态。
 * 未登录也可访问，因此出错时不自动弹提示。
 */
export function getHealth() {
  return post<SystemHealth>('/system/health', {}, { silent: true })
}
