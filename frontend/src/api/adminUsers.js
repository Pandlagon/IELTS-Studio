import request from './index'

/**
 * 管理端用户管理接口封装（Phase 8A）。
 *
 * - 复用 api/index.js 导出的 axios 实例（自动附 JWT、统一处理 401/403/5xx）。
 * - baseURL 已含 /api，路径不再重复拼 /api。
 * - 响应拦截器已取 response.data（即后端 Result 对象），这里再取 res.data 拿业务数据。
 *
 * 安全：不把密码存 localStorage/sessionStorage，不 console.log 用户列表或密码 payload。
 * 权限：后端 /admin/** 仅 ADMIN 可访问；非 ADMIN 调用会返回 403。
 */
export const adminUsersApi = {
  /** 分页查询用户列表（支持 keyword / role / status 筛选） */
  async listUsers(params) {
    const res = await request.get('/admin/users', { params })
    return res.data
  },

  /** 新增用户（管理员创建，BCrypt 加密，不返回 password） */
  async createUser(payload) {
    const res = await request.post('/admin/users', payload)
    return res.data
  },

  /** 查询单个用户详情 */
  async getUser(id) {
    const res = await request.get(`/admin/users/${id}`)
    return res.data
  },

  /** 修改用户角色（USER / ADMIN） */
  async updateRole(id, role) {
    const res = await request.put(`/admin/users/${id}/role`, { role })
    return res.data
  },

  /** 禁用用户（deleted = 1） */
  async disableUser(id) {
    const res = await request.put(`/admin/users/${id}/disable`)
    return res.data
  },

  /** 启用用户（deleted = 0） */
  async enableUser(id) {
    const res = await request.put(`/admin/users/${id}/enable`)
    return res.data
  },

  /** 重置用户密码（使用 BCrypt 加密存储，不返回 password） */
  async resetPassword(id, newPassword) {
    const res = await request.post(`/admin/users/${id}/reset-password`, { newPassword })
    return res.data
  },
}
