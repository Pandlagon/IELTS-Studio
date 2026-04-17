import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * Axios 实例（全局 HTTP 请求封装）
 *
 * - baseURL 优先读取环境变量 VITE_API_BASE_URL，默认指向 /api
 * - 统一超时时间 15 秒
 * - 请求拦截器：自动附加 JWT Token
 * - 响应拦截器：统一处理 401/403/5xx 错误，提取 data 层
 */
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

// ─── 请求拦截器 ─────────────────────────────────────────────────────────────

request.interceptors.request.use(
  config => {
    // 从本地存储读取 Token，附加到请求头
    const token = localStorage.getItem('ielts_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

// ─── 响应拦截器 ─────────────────────────────────────────────────────────────

request.interceptors.response.use(
  // 成功时直接返回 response.data（即后端 Result 对象）
  response => response.data,

  error => {
    // 网络断开或后端未启动时无 response 对象
    if (!error.response) {
      return Promise.reject(new Error('网络连接失败，请检查网络'))
    }

    const { status, data } = error.response
    const msg = data?.message || '请求失败'

    if (status === 401) {
      // Token 过期或未登录：清除本地凭证并跳转登录页
      localStorage.removeItem('ielts_token')
      localStorage.removeItem('ielts_user')
      window.location.href = '/login'
    } else if (status === 403) {
      ElMessage.error('权限不足')
    } else if (status >= 500) {
      ElMessage.error('服务器异常，请稍后重试')
    }

    return Promise.reject(new Error(msg))
  }
)

export default request
