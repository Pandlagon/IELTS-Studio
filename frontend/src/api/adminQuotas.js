import request from './index'

/**
 * 管理端 AI 额度管理 API（Phase 8B）。
 *
 * baseURL 已含 `/api`，路径前缀 `/admin/quotas`。
 * 不写 localStorage / sessionStorage，不 console.log quota payload。
 */
export const adminQuotasApi = {
  /** 分页查询用户当前周期 quota 列表（支持 keyword / role / status 筛选） */
  async listQuotas(params) {
    const res = await request.get('/admin/quotas', { params })
    return res.data
  },

  /** 查询单个用户当前周期 quota（无 quota 行返回默认视图，不创建） */
  async getUserQuota(userId) {
    const res = await request.get(`/admin/quotas/users/${userId}`)
    return res.data
  },

  /** 设置当前周期 creditsTotal（无 quota 行时创建） */
  async setTotal(userId, creditsTotal) {
    const res = await request.put(`/admin/quotas/users/${userId}/total`, { creditsTotal })
    return res.data
  },

  /** 给当前周期增加 creditsTotal（无 quota 行时创建 30+credits） */
  async grantCredits(userId, credits) {
    const res = await request.post(`/admin/quotas/users/${userId}/grant`, { credits })
    return res.data
  },

  /** 重置当前周期 creditsUsed=0（无 quota 行时创建默认 30/0） */
  async resetUsed(userId) {
    const res = await request.post(`/admin/quotas/users/${userId}/reset-used`)
    return res.data
  },
}
