import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as authApi from '@/api/auth'
import type { LoginParams, LoginUser, MenuItem, RoleCode } from '@/api/auth'
import { clearToken, getToken, setToken } from '@/utils/request'

/**
 * 当前登录用户状态。
 *
 * 职责：
 *   - 持有 accessToken（同步到 localStorage）
 *   - 持有用户信息、权限码集合、菜单
 *   - 提供 hasPermission / hasRole 给菜单与按钮做权限控制
 */
export const useUserStore = defineStore('user', () => {
  const token = ref<string>(getToken())
  const userInfo = ref<LoginUser | null>(null)
  const permissions = ref<string[]>([])
  const menus = ref<MenuItem[]>([])

  const isLogin = computed(() => !!token.value)
  const roleCode = computed<RoleCode | ''>(() => userInfo.value?.roleCode ?? '')
  const realName = computed(() => userInfo.value?.realName ?? '')
  const roleName = computed(() => userInfo.value?.roleName ?? '')

  /** 是否拥有某个权限码 */
  function hasPermission(code: string): boolean {
    return permissions.value.includes(code)
  }

  /** 是否拥有任意一个权限码 */
  function hasAnyPermission(codes: string[]): boolean {
    return codes.some((code) => permissions.value.includes(code))
  }

  /** 是否是某个角色 */
  function hasRole(code: RoleCode): boolean {
    return roleCode.value === code
  }

  /** 是否为管理端角色（实验室管理员 / 系统管理员） */
  const isAdmin = computed(
    () => roleCode.value === 'LAB_ADMIN' || roleCode.value === 'SYS_ADMIN'
  )

  /** 登录 */
  async function login(params: LoginParams): Promise<void> {
    const result = await authApi.login(params)
    token.value = result.accessToken
    setToken(result.accessToken, result.refreshToken)
    userInfo.value = result.user
    permissions.value = result.permissions ?? []
    // 菜单只在 /auth/get_info 里下发（登录响应没有），这里补一次，
    // 否则侧边栏会空着，看起来「所有角色都只有首页」。
    await fetchInfo()
  }

  /** 拉取当前用户信息（刷新页面后调用） */
  async function fetchInfo(): Promise<void> {
    const result = await authApi.getCurrentUser()
    userInfo.value = result.user
    permissions.value = result.permissions ?? []
    menus.value = result.menus ?? []
  }

  /** 本地清空状态 */
  function reset(): void {
    token.value = ''
    userInfo.value = null
    permissions.value = []
    menus.value = []
    clearToken()
  }

  /** 登出（后端失败也要清本地） */
  async function logout(): Promise<void> {
    try {
      if (token.value) {
        await authApi.logout()
      }
    } catch {
      // 忽略：本地状态必须清掉
    } finally {
      reset()
    }
  }

  return {
    token,
    userInfo,
    permissions,
    menus,
    isLogin,
    isAdmin,
    roleCode,
    roleName,
    realName,
    hasPermission,
    hasAnyPermission,
    hasRole,
    login,
    fetchInfo,
    logout,
    reset
  }
})
