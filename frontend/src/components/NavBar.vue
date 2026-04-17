<template>
  <nav class="navbar" :class="{ scrolled: isScrolled }">
    <div class="nav-container">
      <!-- Logo -->
      <router-link to="/" class="nav-logo">
        <span class="logo-icon">✦</span>
        <span class="logo-text">IELTS Studio</span>
      </router-link>

      <!-- Center Nav Links -->
      <div class="nav-links">
        <router-link to="/words" class="nav-link" :class="{ active: $route.path === '/words' }">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
            <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
          </svg>
          背单词
        </router-link>
        <router-link to="/exams" class="nav-link" :class="{ active: $route.path === '/exams' }">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
            <line x1="16" y1="13" x2="8" y2="13"/>
            <line x1="16" y1="17" x2="8" y2="17"/>
            <polyline points="10 9 9 9 8 9"/>
          </svg>
          模拟考试
        </router-link>
      </div>

      <!-- Right Actions -->
      <div class="nav-actions">
        <template v-if="!authStore.isLoggedIn">
          <router-link to="/login" class="nav-btn-ghost">登录</router-link>
          <router-link to="/register" class="nav-btn-primary">注册</router-link>
        </template>
        <template v-else>
          <el-dropdown @command="handleCommand" trigger="click">
            <button class="user-avatar-btn">
              <div class="avatar-circle">{{ authStore.username.charAt(0).toUpperCase() }}</div>
              <span class="username-text">{{ authStore.username }}</span>
              <el-icon class="el-icon--right" style="font-size:12px;color:#9CA3AF"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon> 个人中心
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </div>

      <!-- Mobile Menu Button -->
      <button class="mobile-menu-btn" @click="mobileOpen = !mobileOpen">
        <span></span><span></span><span></span>
      </button>
    </div>

    <!-- Mobile Menu -->
    <transition name="slide-down">
      <div v-if="mobileOpen" class="mobile-menu">
        <router-link to="/words" class="mobile-link" @click="mobileOpen = false">背单词</router-link>
        <router-link to="/exams" class="mobile-link" @click="mobileOpen = false">模拟考试</router-link>
        <div class="mobile-divider"></div>
        <template v-if="!authStore.isLoggedIn">
          <router-link to="/login" class="mobile-link" @click="mobileOpen = false">登录</router-link>
          <router-link to="/register" class="mobile-link mobile-register" @click="mobileOpen = false">免费注册</router-link>
        </template>
        <template v-else>
          <router-link to="/profile" class="mobile-link" @click="mobileOpen = false">个人中心</router-link>
          <button class="mobile-link mobile-logout" @click="handleLogout">退出登录</button>
        </template>
      </div>
    </transition>
  </nav>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const router = useRouter()
const isScrolled = ref(false)
const mobileOpen = ref(false)

function handleScroll() {
  isScrolled.value = window.scrollY > 20
}

function handleCommand(cmd) {
  if (cmd === 'profile') router.push('/profile')
  else if (cmd === 'logout') handleLogout()
}

function handleLogout() {
  authStore.logout()
  mobileOpen.value = false
  router.push('/')
}

onMounted(() => window.addEventListener('scroll', handleScroll))
onUnmounted(() => window.removeEventListener('scroll', handleScroll))
</script>

<style scoped>
.navbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  height: var(--nav-height);
  background: var(--bg-nav);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid transparent;
  transition: all 0.2s ease;
}

.navbar.scrolled {
  border-bottom-color: var(--border-light);
  box-shadow: 0 2px 12px rgba(0,0,0,0.06);
}

.nav-container {
  max-width: var(--container-max);
  margin: 0 auto;
  padding: 0 24px;
  height: 100%;
  display: flex;
  align-items: center;
  gap: 32px;
}

.nav-logo {
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
  flex-shrink: 0;
}

.logo-icon {
  font-size: 16px;
  color: var(--color-primary);
  font-weight: 700;
}

.logo-text {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.3px;
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
  justify-content: center;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 14px;
  border-radius: var(--radius-full);
  font-size: 14px;
  color: var(--text-secondary);
  text-decoration: none;
  transition: all 0.15s ease;
  font-weight: 400;
}

.nav-link:hover {
  background: rgba(27,67,50,0.06);
  color: var(--color-primary);
}

.nav-link.active {
  background: rgba(27,67,50,0.08);
  color: var(--color-primary);
  font-weight: 500;
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.nav-btn-ghost {
  padding: 7px 16px;
  border-radius: var(--radius-full);
  font-size: 14px;
  color: var(--text-secondary);
  text-decoration: none;
  transition: all 0.15s ease;
  font-weight: 400;
  border: none;
  background: transparent;
  cursor: pointer;
}

.nav-btn-ghost:hover {
  color: var(--color-primary);
  background: rgba(27,67,50,0.06);
}

.nav-btn-primary {
  padding: 7px 18px;
  border-radius: var(--radius-full);
  font-size: 14px;
  color: #fff;
  text-decoration: none;
  background-color: var(--color-primary);
  font-weight: 500;
  transition: all 0.15s ease;
  border: none;
  cursor: pointer;
  display: inline-block;
}

.nav-btn-primary:hover {
  background-color: var(--color-primary-hover);
  transform: translateY(-1px);
}

.user-avatar-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 12px 5px 5px;
  border-radius: var(--radius-full);
  border: 1.5px solid var(--border-color);
  background: var(--bg-white);
  cursor: pointer;
  transition: all 0.15s ease;
}

.user-avatar-btn:hover {
  border-color: var(--color-primary);
  box-shadow: 0 2px 8px rgba(27,67,50,0.1);
}

.avatar-circle {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--color-primary);
  color: white;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.username-text {
  font-size: 13px;
  color: var(--text-primary);
  font-weight: 500;
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mobile-menu-btn {
  display: none;
  flex-direction: column;
  gap: 5px;
  padding: 8px;
  border: none;
  background: none;
  cursor: pointer;
  margin-left: auto;
}

.mobile-menu-btn span {
  display: block;
  width: 22px;
  height: 2px;
  background: var(--text-primary);
  border-radius: 2px;
  transition: all 0.2s;
}

.mobile-menu {
  padding: 12px 16px 16px;
  border-top: 1px solid var(--border-light);
  background: var(--bg-nav);
  backdrop-filter: blur(12px);
}

.mobile-link {
  display: block;
  padding: 10px 12px;
  font-size: 15px;
  color: var(--text-secondary);
  text-decoration: none;
  border-radius: var(--radius-md);
  transition: all 0.15s;
  border: none;
  background: none;
  cursor: pointer;
  width: 100%;
  text-align: left;
  font-family: var(--font-sans);
}

.mobile-link:hover { background: rgba(27,67,50,0.06); color: var(--color-primary); }

.mobile-register {
  background: var(--color-primary);
  color: #fff !important;
  margin-top: 8px;
  text-align: center;
}

.mobile-divider { height: 1px; background: var(--border-light); margin: 8px 0; }
.mobile-logout { color: #DC2626 !important; }

.slide-down-enter-active, .slide-down-leave-active {
  transition: all 0.2s ease;
}
.slide-down-enter-from, .slide-down-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

@media (max-width: 768px) {
  .nav-links { display: none; }
  .nav-actions { display: none; }
  .mobile-menu-btn { display: flex; }
}
</style>
