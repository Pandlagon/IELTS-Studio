import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'
import { ElMessage } from 'element-plus'

/**
 * 认证状态管理 Store（Pinia）
 *
 * 负责：
 * - 用户登录态（token + user）的全局维护
 * - 登录/注册/退出的业务逻辑
 * - 页面刷新后从 localStorage 恢复登录态
 *
 * 使用示例（在组件中）：
 * ```js
 * const authStore = useAuthStore()
 * if (authStore.isLoggedIn) { ... }
 * await authStore.login({ username, password })
 * ```
 */
export const useAuthStore = defineStore('auth', () => {

  // ─── 状态 ──────────────────────────────────────────────────────────────────

  /** JWT Token，持久化到 localStorage */
  const token = ref(localStorage.getItem('ielts_token') || '')

  /** 当前用户信息对象，持久化到 localStorage */
  const user = ref(JSON.parse(localStorage.getItem('ielts_user') || 'null'))

  // ─── 计算属性 ──────────────────────────────────────────────────────────────

  /** 是否已登录（token 和 user 均有值） */
  const isLoggedIn = computed(() => !!token.value && !!user.value)

  /** 当前用户名，未登录时显示"游客" */
  const username = computed(() => user.value?.username || '游客')

  /** 当前用户头像 URL */
  const avatar = computed(() => user.value?.avatar || '')

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

  return { token, user, isLoggedIn, username, avatar, initAuth, login, register, logout, updateUser }
})
