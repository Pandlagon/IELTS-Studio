import request from './index'

/**
 * 用户 AI 用量查询接口封装。
 *
 * - 复用 api/index.js 导出的 axios 实例（自动附 JWT、统一处理 401/403/5xx）。
 * - baseURL 已含 /api，路径不再重复拼 /api。
 * - 响应拦截器已取 response.data（即后端 Result 对象），这里再取 res.data 拿业务数据，
 *   与现有 aiSettings.js 风格保持一致。
 *
 * 安全：只读接口，不写入本地存储，不打印 payload。
 */
export const aiUsageApi = {
  /** 获取当前用户 AI 用量视图（额度 + 最近使用记录） */
  async getAiUsage() {
    const res = await request.get('/users/me/ai-usage')
    return res.data
  },
}
