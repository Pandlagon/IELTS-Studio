<template>
  <div id="app-root">
    <router-view />
    <footer class="footer">
      <div class="footer-inner">
        <div class="footer-brand">
          <span class="logo-icon">✦</span>
          <span class="logo-text">IELTS Studio</span>
        </div>
        <p class="footer-copy">© 2026 IELTS Studio. 专注雅思备考。</p>
        <a
          href="https://beian.miit.gov.cn/"
          target="_blank"
          rel="noopener noreferrer"
          class="beian-link"
        >浙 ICP 备 2026023842 号</a>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'

const authStore = useAuthStore()
const themeStore = useThemeStore()

onMounted(() => {
  authStore.initAuth()
  themeStore.init()
})
</script>

<style>
#app-root {
  min-height: 100vh;
  background-color: var(--bg-primary);
  display: flex;
  flex-direction: column;
}

.footer {
  margin-top: auto;
  border-top: 1px solid var(--border-light);
  padding: 20px 0;
  background-color: var(--bg-primary);
}

.footer-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  text-align: center;
}

.footer-brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-bottom: 6px;
}

.logo-icon {
  font-size: 14px;
  color: var(--color-primary);
}

.logo-text {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.footer-copy {
  font-size: 12px;
  color: var(--text-secondary);
  margin: 0 0 4px 0;
}

.beian-link {
  font-size: 12px;
  color: var(--text-secondary);
  text-decoration: none;
  transition: color 0.2s;
}

.beian-link:hover {
  color: var(--color-primary);
  text-decoration: underline;
}

@media (max-width: 768px) {
  .footer {
    padding: 16px 0;
  }
}
</style>
