import { post } from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/type'

/**
 * 班级接口（api.md §2.9）。
 *
 * 班级是学生学院 / 专业的唯一来源，也是教师排课时选择「上课班级」的数据源。
 */

export interface ClassItem {
  id: number
  className: string
  college: string
  major: string
  grade: number
  studentCount: number
  status: number
}

export interface ClassQuery extends PageQuery {
  keyword?: string | null
  college?: string | null
  grade?: number | null
  status?: number | null
}

export interface ClassSavePayload {
  id?: number | null
  className: string
  college: string
  major: string
  grade: number
  studentCount?: number
  status?: number
}

/** 班级下拉（只返回启用状态） */
export function getClassList(data: { college?: string; grade?: number; keyword?: string } = {}) {
  return post<ClassItem[]>('/class/get_list', data)
}

/** 班级分页（管理员维护用） */
export function getClassPage(data: ClassQuery) {
  return post<PageResult<ClassItem>>('/class/get_page', data)
}

export function createClass(data: ClassSavePayload) {
  return post<number>('/class/create', data)
}

export function updateClass(data: ClassSavePayload) {
  return post<void>('/class/update', data)
}
