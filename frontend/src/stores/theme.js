import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export const useThemeStore = defineStore('theme', () => {
  const themeMode = ref('system')
  const isDark = ref(false)
  let mediaQuery = null

  function applyTheme(dark) {
    if (dark) {
      document.documentElement.classList.add('dark')
    } else {
      document.documentElement.classList.remove('dark')
    }
  }

  function getSystemDark() {
    return window.matchMedia('(prefers-color-scheme: dark)').matches
  }

  function resolveIsDark() {
    if (themeMode.value === 'dark') return true
    if (themeMode.value === 'light') return false
    return getSystemDark()
  }

  function updateResolvedTheme() {
    isDark.value = resolveIsDark()
    applyTheme(isDark.value)
  }

  function setMode(mode) {
    themeMode.value = ['light', 'dark', 'system'].includes(mode) ? mode : 'system'
  }

  function toggle() {
    setMode(isDark.value ? 'light' : 'dark')
  }

  function init() {
    const saved = localStorage.getItem('theme')
    setMode(['light', 'dark', 'system'].includes(saved) ? saved : 'system')

    if (!mediaQuery) {
      mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
      mediaQuery.addEventListener('change', () => {
        if (themeMode.value === 'system') {
          updateResolvedTheme()
        }
      })
    }

    updateResolvedTheme()
  }

  watch(themeMode, (val) => {
    localStorage.setItem('theme', val)
    updateResolvedTheme()
  })

  return { themeMode, isDark, setMode, toggle, init }
})
