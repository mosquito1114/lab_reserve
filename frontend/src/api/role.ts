import { post } from '@/utils/request'

/**
 * 角色与权限相关接口（api.md §10）。
 *
 * 精简 RBAC：角色固定 5 个，界面上只能改「角色拥有哪些权限」。
 */

/** 角色列表项 */
export interface RoleItem {
  id: number
  /** STUDENT / TEACHER / SYS_ADMIN / LAB_ADMIN / MAINTAINER */
  code: string
  name: string
  description: string | null
  /** 该角色拥有的权限数 */
  permissionCount: number
  /** 该角色下的用户数 */
  userCount: number
}

/** 某个角色已勾选的权限 */
export interface RolePermissionDetail {
  roleId: number
  roleCode: string
  roleName: string
  permissionIds: number[]
  permissions: string[]
}

/** 角色列表（含权限数、用户数） */
export function getRoleList() {
  return post<RoleItem[]>('/role/get_list')
}

/** 某角色的权限清单（用于勾选树回显） */
export function getRolePermissionList(roleId: number) {
  return post<RolePermissionDetail>('/role/get_permission_list', { roleId })
}

/** 保存角色权限（全量覆盖） */
export function updateRolePermission(roleId: number, permissionIds: number[]) {
  return post<void>('/role/update_permission', { roleId, permissionIds })
}
