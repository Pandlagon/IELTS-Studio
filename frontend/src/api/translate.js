import request from './index'

export const translateApi = {
  async translate(passage, selectedText) {
    const res = await request.post('/exams/translate', { passage, selectedText })
    // axios interceptor already unwraps response.data → Result{code, message, data}
    if (!res || res.code !== 200) {
      throw new Error(res?.message || '翻译请求失败')
    }
    return res.data
  },
}
