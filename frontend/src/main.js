import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import { ElMessage } from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './assets/main.css'

// ─── ElMessage 偏移量修正 ──────────────────────────────────────────────────
// 顶部导航栏高度为 64px，消息提示默认从顶部 0px 出现会被遮挡
// 统一设置偏移量为 72px，使提示出现在导航栏下方
const NAV_OFFSET = 72
;['success', 'error', 'warning', 'info'].forEach(type => {
  const original = ElMessage[type].bind(ElMessage)
  ElMessage[type] = (options) => {
    if (typeof options === 'string') return original({ message: options, offset: NAV_OFFSET })
    return original({ offset: NAV_OFFSET, ...options })
  }
})

// ─── 应用初始化 ────────────────────────────────────────────────────────────
const app = createApp(App)
const pinia = createPinia()

app.use(pinia)      // 状态管理
app.use(router)     // 路由
app.use(ElementPlus) // UI 组件库

// 全局注册所有 Element Plus 图标组件（支持在模板中直接使用 <Edit />、<Delete /> 等）
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.mount('#app')
