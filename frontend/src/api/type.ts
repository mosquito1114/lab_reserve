/**
 * 通用接口类型。
 *
 * 与后端 com.labreserve.common.result 一一对应。
 */

/** 统一响应体 */
export interface R<T = unknown> {
  code: number
  message: string
  data: T
  timestamp: number
}

/** 分页结果 */
export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}

/**
 * 分页请求基类。
 *
 * 本项目所有参数都走 JSON body，分页参数也在 body 里。
 */
export interface PageQuery {
  pageNum?: number
  pageSize?: number
}

/** 业务状态码 */
export const ResultCode = {
  SUCCESS: 200,
  BAD_REQUEST: 400,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  METHOD_NOT_ALLOWED: 405,
  /** 业务冲突：时段被占 / 库存不足 / 重复入队 / 处于禁入期 / 状态不允许 */
  CONFLICT: 409,
  INTERNAL_ERROR: 500
} as const

export const DEFAULT_PAGE_SIZE = 10
export const PAGE_SIZE_MAX = 100
