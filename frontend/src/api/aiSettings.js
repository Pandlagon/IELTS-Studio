import request from './index'

/**
 * 用户 AI 设置接口封装。
 *
 * - 复用 api/index.js 导出的 axios 实例（自动附 JWT、统一处理 401/403/5xx）。
 * - baseURL 已含 /api，因此路径不再重复拼 /api。
 * - 响应拦截器已取 response.data（即后端 Result 对象），这里再取 res.data 拿业务数据，
 *   与现有 auth.js / checkin.js / translate.js 风格保持一致。
 *
 * 安全：本模块只搬运数据，不会把 apiKey 写入日志或本地存储。
 */
export const aiSettingsApi = {
  /** 获取当前用户 AI 设置（脱敏，仅 maskedApiKey + hasApiKey） */
  async getSettings() {
    const res = await request.get('/users/me/ai-settings')
    return res.data
  },

  /** 更新当前用户 AI 设置，返回更新后的脱敏设置 */
  async updateSettings(data) {
    const res = await request.put('/users/me/ai-settings', data)
    return res.data
  },

  /** 列出可用 Provider 预设：{ text: [...], vision: [...] } */
  async getProviders() {
    const res = await request.get('/users/me/ai-settings/providers')
    return res.data
  },
}
