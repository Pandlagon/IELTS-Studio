import request from './index'

/**
 * 管理端 Admin 权限管理 API（Phase 8C）。
 *
 * baseURL 已含 `/api`，路径前缀 `/admin/permissions`。
 * 不写 localStorage / sessionStorage 保存权限以外的敏感信息，不 console.log payload。
 *
 * 注意：前端权限仅用于隐藏/显示菜单与按钮（UX），后端 /admin/** 仍会做精细权限校验兜底。
 */
export const adminPermissionsApi = {
  /** 查询当前 ADMIN 自身的有效权限（供 NavBar 显示/隐藏菜单） */
  async getMyPermissions() {
    const res = await request.get('/admin/permissions/me')
    return res.data
  },

  /** 列出所有合法权限枚举名 */
  async listPermissions() {
    const res = await request.get('/admin/permissions')
    return res.data
  },

  /** 查询某用户的有效权限（含 userId/username/role/explicitMode/permissions） */
  async getUserPermissions(userId) {
    const res = await request.get(`/admin/permissions/users/${userId}`)
    return res.data
  },

  /** 更新某 ADMIN 用户的权限集合（先删后插，需 ADMIN_PERMISSIONS_MANAGE） */
  async updateUserPermissions(userId, permissions) {
    const res = await request.put(`/admin/permissions/users/${userId}`, { permissions })
    return res.data
  },
}
