import request from './index'

/**
 * 管理端操作审计日志 API（Phase 8D）。
 *
 * baseURL 已含 `/api`，路径前缀 `/admin/audit-logs`。
 * 不写 localStorage / sessionStorage，不 console.log payload。
 * 仅提供只读查询接口；审计日志由后端在 Admin 写操作时自动记录，前端无写入入口。
 */
export const adminAuditLogsApi = {
  /**
   * 分页查询审计日志。
   *
   * @param {Object} params - 筛选参数
   * @param {number} [params.page=1] - 页码
   * @param {number} [params.pageSize=20] - 每页条数（1~100）
   * @param {number} [params.actorUserId] - 操作者 ID
   * @param {number} [params.targetUserId] - 被操作用户 ID
   * @param {string} [params.action] - action 枚举名（如 USER_CREATE）
   * @param {string} [params.resourceType] - 资源类型（USER/QUOTA/PERMISSION）
   * @param {string} [params.status] - 状态（SUCCESS/FAILED）
   * @param {string} [params.dateFrom] - 起始时间 ISO-8601
   * @param {string} [params.dateTo] - 截止时间 ISO-8601
   * @returns {Promise<Object>} 分页 DTO { records, total, page, pageSize, pages }
   */
  async listLogs(params) {
    const res = await request.get('/admin/audit-logs', { params })
    return res.data
  },
}
