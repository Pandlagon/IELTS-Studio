<template>
  <div class="auth-page">
    <div class="auth-bg"></div>
    <div class="auth-container">
      <router-link to="/" class="auth-logo">
        <span class="logo-icon">✦</span>
        <span class="logo-text">IELTS Studio</span>
      </router-link>

      <div class="auth-card card">
        <div class="auth-header">
          <h1 class="auth-title">创建账号</h1>
          <p class="auth-sub">开始您的高效备考之旅</p>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="form.username"
              placeholder="3-20位字母或数字"
              size="large"
              prefix-icon="User"
              :disabled="loading"
            />
          </el-form-item>

          <el-form-item label="邮箱" prop="email">
            <el-input
              v-model="form.email"
              placeholder="your@email.com"
              size="large"
              prefix-icon="Message"
              :disabled="loading"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="至少8位，包含字母和数字"
              show-password
              size="large"
              prefix-icon="Lock"
              :disabled="loading"
            />
          </el-form-item>

          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="再次输入密码"
              show-password
              size="large"
              prefix-icon="Lock"
              :disabled="loading"
            />
          </el-form-item>

          <div class="terms-row">
            <el-checkbox v-model="agreed" size="small">
              我已阅读并同意
              <a href="#" class="terms-link">《用户服务协议》</a>
              和
              <a href="#" class="terms-link">《隐私政策》</a>
            </el-checkbox>
          </div>

          <el-button
            type="primary"
            size="large"
            class="submit-btn"
            :loading="loading"
            :disabled="!agreed"
            @click="handleSubmit"
          >
            {{ loading ? '注册中...' : '免费注册' }}
          </el-button>
        </el-form>

        <div class="divider">或</div>

        <div class="demo-hint">
          <p>已有账号？<router-link to="/login" class="auth-link">立即登录</router-link></p>
        </div>
      </div>

      <p class="back-home">
        <router-link to="/" class="back-link">← 返回首页</router-link>
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const formRef = ref()
const loading = ref(false)
const agreed = ref(false)

const form = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度3-20位', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '只允许字母、数字和下划线', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, message: '密码至少8位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== form.password) callback(new Error('两次密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

async function handleSubmit() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    const ok = await authStore.register({
      username: form.username,
      email: form.email,
      password: form.password,
    })
    loading.value = false
    if (ok) router.push('/')
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
  max-width: 440px;
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

.logo-icon { font-size: 18px; color: var(--color-primary); }
.logo-text { font-size: 18px; font-weight: 700; color: var(--text-primary); }

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

.terms-row {
  margin-bottom: 20px;
  margin-top: -4px;
}

.terms-link {
  color: var(--color-primary);
  font-weight: 500;
}

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

.auth-link { color: var(--color-primary); font-weight: 600; text-decoration: none; }
.auth-link:hover { text-decoration: underline; }

.back-home { font-size: 14px; }
.back-link { color: var(--text-muted); text-decoration: none; transition: color 0.15s; }
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
