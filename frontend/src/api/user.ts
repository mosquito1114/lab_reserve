import { post } from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/type'

/**
 * 用户管理接口（api.md §2）。
 *
 * 约定：
 *  - 手机号默认脱敏；`revealPhone=true` 且当前账号有 `user:view-phone` 权限才返回明文，
 *    且后端会写一条 QUERY_PHONE 操作日志
 *  - 角色不允许修改：新增时选角色，编辑时角色只读
 */

export type RoleCode = 'STUDENT' | 'TEACHER' | 'SYS_ADMIN' | 'LAB_ADMIN' | 'MAINTAINER'

/** 用户分页筛选 */
export interface UserQuery extends PageQuery {
  keyword?: string | null
  roleCode?: string | null
  college?: string | null
  classId?: number | null
  studentNo?: string | null
  teacherNo?: string | null
  staffNo?: string | null
  status?: number | null
  revealPhone?: boolean
}

/** 用户列表项（三类角色的字段并存，按 roleCode 显示对应列） */
export interface UserRow {
  id: number
  username: string
  realName: string
  roleCode: RoleCode
  roleName: string
  phone: string | null
  email: string | null
  notifyEmail: number | null
  status: number
  lastLoginAt: string | null
  createdAt: string
  studentNo?: string | null
  classId?: number | null
  className?: string | null
  teacherNo?: string | null
  staffNo?: string | null
  department?: string | null
  maintainerType?: number | null
  college?: string | null
  grade?: number | null
}

export interface StudentInfo {
  studentNo: string
  classId: number
  className?: string
  college?: string
  major?: string
  grade?: number
}

export interface TeacherInfo {
  teacherNo: string
  college: string
  title?: string
}

export interface StaffInfo {
  staffNo: string
  department?: string
  maintainerType?: number | null
  maintainerTypeName?: string
}

/** 用户详情 */
export interface UserDetail {
  id: number
  username: string
  realName: string
  roleCode: RoleCode
  roleName: string
  phone: string | null
  email: string | null
  notifyEmail: number | null
  status: number
  lastLoginAt: string | null
  createdAt: string
  studentInfo?: StudentInfo | null
  teacherInfo?: TeacherInfo | null
  staffInfo?: StaffInfo | null
}

/** 新增用户入参 */
export interface UserCreatePayload {
  username: string
  password: string
  realName: string
  roleCode: RoleCode
  phone?: string
  email?: string
  notifyEmail?: number
  studentInfo?: { studentNo: string; classId: number }
  teacherInfo?: { teacherNo: string; college: string; title?: string }
  staffInfo?: { staffNo: string; department?: string; maintainerType?: number | null }
}

/** 修改用户入参（不含角色/账号/学号工号） */
export interface UserUpdatePayload {
  id: number
  realName?: string
  phone?: string
  email?: string
  notifyEmail?: number
  studentInfo?: { classId: number }
  teacherInfo?: { college: string; title?: string }
  staffInfo?: { department?: string; maintainerType?: number | null }
}

export interface MaintainerOption {
  id: number
  realName: string
  staffNo: string
  maintainerType: number
  maintainerTypeName: string
}

export interface UserOption {
  id: number
  realName: string
  username: string
}

export function getUserPage(data: UserQuery) {
  return post<PageResult<UserRow>>('/user/get_page', data)
}

export function getUserInfo(id: number, revealPhone = false) {
  return post<UserDetail>('/user/get_info', { id, revealPhone })
}

export function createUser(data: UserCreatePayload) {
  return post<number>('/user/create', data)
}

export function updateUser(data: UserUpdatePayload) {
  return post<void>('/user/update', data)
}

export function updateUserStatus(data: { id: number; status: number; reason?: string }) {
  return post<void>('/user/update_status', data)
}

export function updateSelf(data: { phone?: string; email?: string; notifyEmail?: number }) {
  return post<void>('/user/update_self', data)
}

export function getSelfProfile() {
  return post<UserDetail>('/user/get_self_profile')
}

export function listMaintainer(data: { maintainerType?: number | null }) {
  return post<MaintainerOption[]>('/user/list_maintainer', data)
}

export function listUserOption(data: { roleCode?: string | null; keyword?: string | null }) {
  return post<UserOption[]>('/user/list_option', data)
}
