import { post } from '@/utils/request'

/** 角色编码，与后端 tb_role.code 一致 */
export type RoleCode = 'STUDENT' | 'TEACHER' | 'SYS_ADMIN' | 'LAB_ADMIN' | 'MAINTAINER'

export interface LoginParams {
  username: string
  password: string
}

/** 当前登录用户 */
export interface LoginUser {
  id: number
  username: string
  realName: string
  roleCode: RoleCode
  roleName: string
  /** 已脱敏，如 138****1234 */
  phone: string | null
  email: string | null
  /** 是否开启邮件通知 0否 1是 */
  notifyEmail: number
  avatar: string | null
}

export interface LoginResult {
  accessToken: string
  refreshToken: string
  /** accessToken 有效期（秒） */
  expiresIn: number
  user: LoginUser
  permissions: string[]
}

export interface CurrentUserResult {
  user: LoginUser
  permissions: string[]
  /** 前端动态菜单（迭代 1 补全） */
  menus: MenuItem[]
}

export interface MenuItem {
  path: string
  title: string
  icon?: string
  children?: MenuItem[]
}

/** 登录（失败由页面自行提示，所以加 silent） */
export function login(data: LoginParams) {
  return post<LoginResult>('/auth/login', data, { silent: true })
}

/** 刷新 accessToken */
export function refreshAccessToken(data: { refreshToken: string }) {
  return post<{ accessToken: string; expiresIn: number }>('/auth/refresh', data, { silent: true })
}

/** 登出 */
export function logout() {
  return post<void>('/auth/logout')
}

/** 获取当前登录用户信息与权限 */
export function getCurrentUser() {
  return post<CurrentUserResult>('/auth/get_info')
}

/** 修改自己的密码 */
export function updatePassword(data: { oldPassword: string; newPassword: string }) {
  return post<void>('/auth/update_password', data, { silent: true })
}
