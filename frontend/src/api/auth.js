import request from './index'

const NETWORK_ERR = '网络连接失败，请检查网络'

/**
 * 判断当前 Token 是否为本地 Mock Token
 * Mock Token 以 'mock_token_' 开头，仅用于无后端时的开发调试
 */
function isMockToken(token) {
  return token && token.startsWith('mock_token_')
}

// ─── Mock 模式辅助函数（仅供后端不可用时使用）──────────────────────────────────

/**
 * Mock 登录：从 localStorage 的模拟用户列表中匹配账号密码
 */
function mockLogin({ username, password }) {
  const users = JSON.parse(localStorage.getItem('ielts_mock_users') || '[]')
  const user = users.find(u => (u.username === username || u.email === username) && u.password === password)
  if (!user) throw new Error('用户名或密码错误')
  return {
    token: `mock_token_${user.id}_${Date.now()}`,
    user: { id: user.id, username: user.username, email: user.email, avatar: user.avatar || '' },
  }
}

/**
 * Mock 注册：将新用户存入 localStorage 的模拟用户列表
 */
function mockRegister({ username, email, password }) {
  const users = JSON.parse(localStorage.getItem('ielts_mock_users') || '[]')
  if (users.find(u => u.username === username)) throw new Error('用户名已被使用')
  if (users.find(u => u.email === email)) throw new Error('邮箱已被注册')
  const newUser = { id: Date.now(), username, email, password, createdAt: new Date().toISOString() }
  users.push(newUser)
  localStorage.setItem('ielts_mock_users', JSON.stringify(users))
  return {
    token: `mock_token_${newUser.id}_${Date.now()}`,
    user: { id: newUser.id, username: newUser.username, email: newUser.email, avatar: '' },
  }
}

// ─── 真实 API（网络不通时自动降级 Mock）──────────────────────────────────────

export const authApi = {

  /**
   * 用户登录
   * 后端不可达时自动降级为 Mock 登录（仅限开发调试）
   */
  async login(credentials) {
    try {
      const res = await request.post('/auth/login', credentials)
      return res.data
    } catch (e) {
      if (e.message === NETWORK_ERR) return mockLogin(credentials)
      throw e
    }
  },

  /**
   * 用户注册
   * 后端不可达时自动降级为 Mock 注册（仅限开发调试）
   */
  async register(data) {
    try {
      const res = await request.post('/auth/register', data)
      return res.data
    } catch (e) {
      if (e.message === NETWORK_ERR) return mockRegister(data)
      throw e
    }
  },

  /**
   * 退出登录（服务端无状态，接口仅作语义完整性，实际清除由 store 执行）
   */
  async logout() {
    try { await request.post('/auth/logout') } catch {}
    return { success: true }
  },

  /**
   * 获取当前用户个人信息
   * Mock Token 时直接从 localStorage 返回缓存的用户信息
   */
  async getProfile() {
    const token = localStorage.getItem('ielts_token') || ''
    if (isMockToken(token)) {
      const user = JSON.parse(localStorage.getItem('ielts_user') || 'null')
      if (!user) throw new Error('未登录')
      return user
    }
    const res = await request.get('/auth/profile')
    return res.data
  },

  /**
   * 更新用户个人信息（用户名等）
   * Mock Token 时直接返回传入的数据（模拟成功）
   */
  async updateProfile(data) {
    const token = localStorage.getItem('ielts_token') || ''
    if (isMockToken(token)) {
      return data
    }
    const res = await request.put('/users/me', data)
    return res.data
  },
}
