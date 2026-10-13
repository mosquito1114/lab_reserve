import axios, { AxiosError, type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { ResultCode, type R } from '@/api/type'

declare module 'axios' {
  export interface AxiosRequestConfig {
    /** 为 true 时不自动弹错误提示，由调用方自行处理（如 409 冲突需要弹自定义对话框） */
    silent?: boolean
  }
}

// ============================================================
// Token 存取
// ============================================================

const TOKEN_KEY = 'lab_reserve:access_token'
const REFRESH_TOKEN_KEY = 'lab_reserve:refresh_token'

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) ?? ''
}

export function getRefreshToken(): string {
  return localStorage.getItem(REFRESH_TOKEN_KEY) ?? ''
}

export function setToken(accessToken: string, refreshToken?: string): void {
  localStorage.setItem(TOKEN_KEY, accessToken)
  if (refreshToken) {
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
  }
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

// ============================================================
// 统一错误对象
// ============================================================

/**
 * 后端返回的业务错误。
 *
 * {@code code} 是业务状态码（与 HTTP 状态码一致），
 * {@code data} 是业务冲突时携带的附加信息 —— 例如预约冲突会带上
 * {@code { conflictCode, queueCount, canQueue }} 供前端弹"是否进入排队"。
 */
export interface ApiError extends Error {
  code: number
  data: unknown
}

function createApiError(code: number, message: string, data: unknown): ApiError {
  const error = new Error(message || '请求失败') as ApiError
  error.name = 'ApiError'
  error.code = code
  error.data = data
  return error
}

/** 判断是否业务冲突（409） */
export function isConflict(error: unknown): error is ApiError {
  return isApiError(error) && error.code === ResultCode.CONFLICT
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof Error && error.name === 'ApiError'
}

// ============================================================
// axios 实例
// ============================================================

const instance: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8'
  }
})

instance.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

instance.interceptors.response.use(
  (response) => {
    // 文件流（Excel 导出）直接透传
    if (response.config.responseType === 'blob') {
      return response
    }
    const body = response.data as R
    if (body && typeof body === 'object' && 'code' in body && body.code !== ResultCode.SUCCESS) {
      return Promise.reject(createApiError(body.code, body.message, body.data))
    }
    return response
  },
  (error: AxiosError<R>) => {
    const response = error.response

    // 后端返回了标准响应体（HTTP 状态码 = 业务码）
    if (response && response.data && typeof response.data === 'object' && 'code' in response.data) {
      const body = response.data
      handleUnauthorized(body.code)
      const apiError = createApiError(body.code, body.message, body.data)
      notifyError(apiError, error.config as AxiosRequestConfig | undefined)
      return Promise.reject(apiError)
    }

    // 网络异常 / 超时 / 网关错误
    let message = '网络异常，请检查网络后重试'
    if (error.code === 'ECONNABORTED') {
      message = '请求超时，请稍后重试'
    } else if (response) {
      message = `请求失败（HTTP ${response.status}）`
    }
    const apiError = createApiError(response?.status ?? 0, message, null)
    notifyError(apiError, error.config as AxiosRequestConfig | undefined)
    return Promise.reject(apiError)
  }
)

/** 401 统一处理：清 token 并跳登录页 */
function handleUnauthorized(code: number): void {
  if (code !== ResultCode.UNAUTHORIZED) {
    return
  }
  clearToken()
  const current = window.location.pathname + window.location.search
  if (!current.startsWith('/login')) {
    window.location.href = `/login?redirect=${encodeURIComponent(current)}`
  }
}

function notifyError(error: ApiError, config?: AxiosRequestConfig): void {
  if (config?.silent) {
    return
  }
  // 401 会跳登录页，不用再弹
  if (error.code === ResultCode.UNAUTHORIZED) {
    return
  }
  ElMessage.error(error.message)
}

// ============================================================
// 对外方法
//
// 本项目所有接口都是 POST + JSON body，所以只暴露 post / postBlob 两个方法，
// 前端不需要记 GET / PUT / DELETE。
// ============================================================

/** POST 请求，成功时直接返回 data */
export async function post<T = unknown>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig
): Promise<T> {
  const response = await instance.post<R<T>>(url, data ?? {}, config)
  return response.data.data
}

/** POST 请求，返回文件流（Excel 导出等） */
export async function postBlob(url: string, data?: unknown): Promise<Blob> {
  const response = await instance.post(url, data ?? {}, {
    responseType: 'blob',
    timeout: 60000
  })
  return response.data as Blob
}

/** POST 请求，返回完整响应体（需要 code / message 时使用） */
export async function postRaw<T = unknown>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig
): Promise<R<T>> {
  const response = await instance.post<R<T>>(url, data ?? {}, config)
  return response.data
}

export default instance
