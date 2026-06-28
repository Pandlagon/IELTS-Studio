import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'
import { adminPermissionsApi } from '@/api/adminPermissions'
import { ElMessage } from 'element-plus'

/**
 * 认证状态管理 Store（Pinia）
 *
 * 负责：
 * - 用户登录态（token + user）的全局维护
 * - 登录/注册/退出的业务逻辑
 * - 页面刷新后从 localStorage 恢复登录态
 * - Phase 8C：ADMIN 用户的精细权限加载与查询（前端只做 UX 隐藏，后端兜底）
 *
 * 使用示例（在组件中）：
 * ```js
 * const authStore = useAuthStore()
 * if (authStore.isLoggedIn) { ... }
 * await authStore.login({ username, password })
 * if (authStore.hasAdminPermission('ADMIN_USERS_VIEW')) { ... }
 * ```
 */
export const useAuthStore = defineStore('auth', () => {

  // ─── 状态 ──────────────────────────────────────────────────────────────────

  /** JWT Token，持久化到 localStorage */
  const token = ref(localStorage.getItem('ielts_token') || '')

  /** 当前用户信息对象，持久化到 localStorage */
  const user = ref(JSON.parse(localStorage.getItem('ielts_user') || 'null'))

  /** 当前 ADMIN 的有效权限列表（枚举名数组）；非 ADMIN 或未加载时为空数组 */
  const adminPermissions = ref([])

  /** 是否已加载过 ADMIN 权限（避免重复请求；logout 时重置为 false） */
  const adminPermissionsLoaded = ref(false)

  // ─── 计算属性 ──────────────────────────────────────────────────────────────

  /** 是否已登录（token 和 user 均有值） */
  const isLoggedIn = computed(() => !!token.value && !!user.value)

  /** 当前用户名，未登录时显示"游客" */
  const username = computed(() => user.value?.username || '游客')

  /** 当前用户头像 URL */
  const avatar = computed(() => user.value?.avatar || '')

  /** 是否为管理员（role === 'ADMIN'），用于前端隐藏 admin 入口；后端 /admin/** 仍会兜底鉴权 */
  const isAdmin = computed(() => user.value?.role === 'ADMIN')

  // ─── 方法 ──────────────────────────────────────────────────────────────────

  /**
   * 从 localStorage 恢复登录态（在 App.vue 挂载时调用）
   */
  function initAuth() {
    const storedToken = localStorage.getItem('ielts_token')
    const storedUser = localStorage.getItem('ielts_user')
    if (storedToken && storedUser) {
      token.value = storedToken
      user.value = JSON.parse(storedUser)
    }
  }

  /**
   * 用户登录
   * @param {Object} credentials - { username, password }
   * @returns {boolean} 登录是否成功
   */
  async function login(credentials) {
    try {
      const res = await authApi.login(credentials)
      token.value = res.token
      user.value = res.user
      localStorage.setItem('ielts_token', res.token)
      localStorage.setItem('ielts_user', JSON.stringify(res.user))
      ElMessage.success('登录成功，欢迎回来！')
      // 登录后不主动加载 ADMIN 权限，由需要权限的页面/组件懒加载
      return true
    } catch (err) {
      ElMessage.error(err.message || '登录失败，请检查账号密码')
      return false
    }
  }

  /**
   * 用户注册
   * @param {Object} data - { username, email, password }
   * @returns {boolean} 注册是否成功
   */
  async function register(data) {
    try {
      const res = await authApi.register(data)
      token.value = res.token
      user.value = res.user
      localStorage.setItem('ielts_token', res.token)
      localStorage.setItem('ielts_user', JSON.stringify(res.user))
      ElMessage.success('注册成功！')
      return true
    } catch (err) {
      ElMessage.error(err.message || '注册失败，请稍后重试')
      return false
    }
  }

  /**
   * 退出登录：清除本地凭证和用户信息
   */
  function logout() {
    token.value = ''
    user.value = null
    adminPermissions.value = []
    adminPermissionsLoaded.value = false
    localStorage.removeItem('ielts_token')
    localStorage.removeItem('ielts_user')
    ElMessage.success('已退出登录')
  }

  /**
   * 更新本地缓存的用户信息（修改用户名/头像后调用）
   * @param {Object} data - 需要更新的用户信息字段
   */
  function updateUser(data) {
    user.value = { ...user.value, ...data }
    localStorage.setItem('ielts_user', JSON.stringify(user.value))
  }

  /**
   * 懒加载当前 ADMIN 的有效权限（Phase 8C）。
   *
   * <p>仅在 isAdmin=true 且未加载过时调用后端 GET /admin/permissions/me。
   * 兼容模式（表为空）下后端返回全部权限，前端无需自己推断。
   * 失败时清空权限数组，不抛异常（前端隐藏菜单只是 UX，后端仍会 403 兜底）。</p>
   *
   * @returns {Promise<boolean>} 是否成功加载（非 ADMIN 直接返回 false）
   */
  async function loadAdminPermissions() {
    if (!isAdmin.value) {
      adminPermissions.value = []
      adminPermissionsLoaded.value = true
      return false
    }
    try {
      const dto = await adminPermissionsApi.getMyPermissions()
      adminPermissions.value = Array.isArray(dto?.permissions) ? dto.permissions : []
      adminPermissionsLoaded.value = true
      return true
    } catch (err) {
      // 加载失败时清空权限并标记为已加载（避免无限重试）
      adminPermissions.value = []
      adminPermissionsLoaded.value = true
      return false
    }
  }

  /**
   * 判断当前 ADMIN 是否拥有指定权限（Phase 8C）。
   *
   * <p><b>注意：</b>此方法仅用于前端隐藏/显示菜单与按钮（UX），
   * 后端 /admin/** 接口仍会做精细权限校验兜底，不信任前端判断。</p>
   *
   * @param {string} permission - 权限枚举名（如 'ADMIN_USERS_VIEW'）
   * @returns {boolean} true=有权限；false=无权限或非 ADMIN
   */
  function hasAdminPermission(permission) {
    if (!isAdmin.value) return false
    return adminPermissions.value.includes(permission)
  }

  return {
    token,
    user,
    adminPermissions,
    adminPermissionsLoaded,
    isLoggedIn,
    username,
    avatar,
    isAdmin,
    initAuth,
    login,
    register,
    logout,
    updateUser,
    loadAdminPermissions,
    hasAdminPermission,
  }
})
