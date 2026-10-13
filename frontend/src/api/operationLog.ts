import { post, postBlob } from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/type'

/**
 * 操作日志接口（api.md §10.3）。
 *
 * 审计语义：只读。日志由后端 `@OpLog` 切面自动写入，前端不提供增删改。
 */

/** 操作日志筛选条件 */
export interface OperationLogQuery extends PageQuery {
  /** LOGIN / LOGOUT / CREATE / UPDATE / DELETE / AUDIT / EXPORT / ASSIGN / ACCEPT / QUERY_PHONE */
  operationType?: string | null
  /** auth / user / resource / reservation / queue / repair / message / statistics / system */
  module?: string | null
  operatorId?: number | null
  /** 操作人姓名或账号，模糊匹配 */
  operatorKeyword?: string | null
  beginTime?: string | null
  endTime?: string | null
  ip?: string | null
  /** 1 成功 / 0 失败 */
  result?: number | null
}

/** 操作日志列表项 */
export interface OperationLogItem {
  id: number
  operatorId: number | null
  operatorName: string | null
  roleCode: string | null
  module: string
  moduleName: string | null
  operationType: string
  operationTypeName: string | null
  content: string
  requestMethod: string | null
  requestUrl: string | null
  requestParam: string | null
  ip: string | null
  userAgent: string | null
  result: number
  errorMsg: string | null
  costMs: number | null
  createdAt: string
}

/** 分页查询操作日志 */
export function getOperationLogPage(data: OperationLogQuery) {
  return post<PageResult<OperationLogItem>>('/operation_log/get_page', data)
}

/** 日志详情 */
export function getOperationLogInfo(id: number) {
  return post<OperationLogItem>('/operation_log/get_info', { id })
}

/** 导出 Excel（返回文件流，由调用方触发下载） */
export function exportOperationLog(data: OperationLogQuery) {
  return postBlob('/operation_log/export', data)
}
