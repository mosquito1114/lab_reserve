import { post } from '@/utils/request'

/** 单个权限点（勾选树叶子节点） */
export interface PermissionItem {
  id: number
  /** 权限码，如 reservation:audit */
  code: string
  name: string
}

/** 权限分组（按 tb_permission.module 分组） */
export interface PermissionGroup {
  module: string
  moduleName: string
  items: PermissionItem[]
}

/** 权限点分组列表（供权限勾选树渲染） */
export function getPermissionList() {
  return post<PermissionGroup[]>('/permission/get_list')
}
