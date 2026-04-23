import request from './index'

export const checkinApi = {
  async getStats() {
    const res = await request.get('/checkins/stats')
    return res.data
  },

  async getRecent(days = 30) {
    const res = await request.get('/checkins/recent', { params: { days } })
    return res.data
  },

  async checkinToday() {
    const res = await request.post('/checkins/today')
    return res.data
  },
}
