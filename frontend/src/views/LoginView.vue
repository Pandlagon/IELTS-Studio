<template>
  <div class="auth-page">
    <div class="auth-bg"></div>
    <div class="auth-container">
      <!-- Logo -->
      <router-link to="/" class="auth-logo">
        <span class="logo-icon">✦</span>
        <span class="logo-text">IELTS Studio</span>
      </router-link>

      <div class="auth-card card">
        <div class="auth-header">
          <h1 class="auth-title">欢迎回来</h1>
          <p class="auth-sub">登录您的 IELTS Studio 账号</p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @keyup.enter="handleSubmit"
        >
          <el-form-item label="账号" prop="username">
            <el-input
              v-model="form.username"
              placeholder="用户名或邮箱"
              size="large"
              prefix-icon="User"
              :disabled="loading"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              show-password
              size="large"
              prefix-icon="Lock"
              :disabled="loading"
            />
          </el-form-item>

          <div class="form-extra">
            <el-checkbox v-model="rememberMe" label="记住我" size="small" />
            <a href="#" class="forget-link">忘记密码？</a>
          </div>

          <el-button
            type="primary"
            size="large"
            class="submit-btn"
            :loading="loading"
            @click="handleSubmit"
          >
            {{ loading ? '登录中...' : '登录' }}
          </el-button>
        </el-form>

        <div class="divider">或</div>

        <div class="demo-hint">
          <p>没有账号？<router-link to="/register" class="auth-link">立即免费注册</router-link></p>
        </div>
      </div>

      <p class="back-home">
        <router-link to="/" class="back-link">
          ← 返回首页
        </router-link>
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const formRef = ref()
const loading = ref(false)
const rememberMe = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleSubmit() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    const ok = await authStore.login({ username: form.username, password: form.password })
    loading.value = false
    if (ok) {
      const redirect = route.query.redirect || '/'
      router.push(redirect)
    }
  })
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-primary);
  position: relative;
  overflow: hidden;
}

.auth-bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 60% 60% at 80% 20%, rgba(82, 183, 136, 0.08) 0%, transparent 70%),
    radial-gradient(ellipse 40% 40% at 20% 80%, rgba(27, 67, 50, 0.06) 0%, transparent 70%);
  pointer-events: none;
}

.auth-container {
  width: 100%;
  max-width: 420px;
  padding: 24px 20px;
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

.auth-logo {
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
}

.logo-icon {
  font-size: 18px;
  color: var(--color-primary);
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.auth-card {
  width: 100%;
  padding: 36px 32px;
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-xl);
}

.auth-header {
  text-align: center;
  margin-bottom: 28px;
}

.auth-title {
  font-size: 24px;
  font-weight: 800;
  color: var(--text-primary);
  margin-bottom: 6px;
}

.auth-sub {
  font-size: 14px;
  color: var(--text-muted);
}

.form-extra {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  margin-top: -6px;
}

.forget-link {
  font-size: 13px;
  color: var(--color-primary);
  text-decoration: none;
  font-weight: 500;
}

.forget-link:hover { text-decoration: underline; }

.submit-btn {
  width: 100%;
  border-radius: var(--radius-full) !important;
  font-size: 15px !important;
  font-weight: 600 !important;
  height: 46px !important;
}

.divider {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--text-muted);
  font-size: 12px;
  margin: 20px 0 16px;
}
.divider::before, .divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--border-color);
}

.demo-hint {
  text-align: center;
  font-size: 14px;
  color: var(--text-muted);
}

.auth-link {
  color: var(--color-primary);
  font-weight: 600;
  text-decoration: none;
}
.auth-link:hover { text-decoration: underline; }

.back-home {
  font-size: 14px;
}

.back-link {
  color: var(--text-muted);
  text-decoration: none;
  transition: color 0.15s;
}
.back-link:hover { color: var(--color-primary); }

:deep(.el-form-item__label) {
  font-size: 13px !important;
  font-weight: 500 !important;
  color: var(--text-secondary) !important;
  padding-bottom: 4px !important;
}

:deep(.el-input__wrapper) {
  border-radius: var(--radius-md) !important;
}
</style>
