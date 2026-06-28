import request from './index'

/**
 * 管理端 AI usage 统计接口封装（Phase 6B-2B）。
 *
 * - 复用 api/index.js 导出的 axios 实例（自动附 JWT、统一处理 401/403/5xx）。
 * - baseURL 已含 /api，路径不再重复拼 /api。
 * - 响应拦截器已取 response.data（即后端 Result 对象），这里再取 res.data 拿业务数据。
 *
 * 安全：只读接口，不写入本地存储，不打印 payload。
 * 权限：后端 /admin/** 仅 ADMIN 可访问；非 ADMIN 调用会返回 403。
 */
export const adminAiUsageApi = {
  /** 获取最近 N 天 AI usage 汇总统计（默认 7 天，范围 1~90，后端会 clamp） */
  async getSummary(days = 7) {
    const res = await request.get('/admin/ai-usage/summary', { params: { days } })
    return res.data
  },

  /** 获取最近 N 条 AI usage records（默认 50 条，范围 1~100，后端会 clamp） */
  async getRecent(limit = 50) {
    const res = await request.get('/admin/ai-usage/recent', { params: { limit } })
    return res.data
  },
}
