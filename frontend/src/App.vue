<template>
  <div id="app-root">
    <router-view />
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
}
</style>
