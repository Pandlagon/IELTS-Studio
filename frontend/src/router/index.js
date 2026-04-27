import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/**
 * 路由配置
 *
 * meta 字段说明：
 * - requiresAuth: true  — 需要登录才能访问，未登录跳转到 /login
 * - guestOnly: true     — 仅游客可访问（如登录/注册页），已登录跳转到首页
 */
const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/HomeView.vue'),
    meta: { layout: 'default' },
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { guestOnly: true },   // 已登录用户不需要再看登录页
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { guestOnly: true },
  },
  {
    path: '/words',
    name: 'Words',
    component: () => import('@/views/WordsView.vue'),
  },
  {
    path: '/cloze',
    name: 'Cloze',
    component: () => import('@/views/ClozeView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/exams',
    name: 'Exams',
    component: () => import('@/views/ExamsView.vue'),
  },
  {
    path: '/exam/:id',
    name: 'Exam',
    component: () => import('@/views/ExamView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/exam/:id/result',
    name: 'Result',
    component: () => import('@/views/ResultView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/collection/:id',
    name: 'CollectionExam',
    component: () => import('@/views/CollectionExamView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/ProfileView.vue'),
    meta: { requiresAuth: true },
  },
  // 未匹配的路径统一重定向到首页
  {
    path: '/:pathMatch(.*)*',
    redirect: '/',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  // 路由切换后滚动到顶部（浏览器前进/后退时恢复之前的滚动位置）
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    return { top: 0 }
  },
})

/**
 * 全局路由守卫
 *
 * 登录验证逻辑：
 * 1. requiresAuth 页面：未登录则跳转到 /login，并携带 redirect 参数方便登录后回跳
 * 2. guestOnly 页面：已登录则跳转到首页
 * 3. 其他页面：直接放行
 */
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()

  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    // 未登录，重定向到登录页，并记录目标路径
    next({ name: 'Login', query: { redirect: to.fullPath } })
  } else if (to.meta.guestOnly && authStore.isLoggedIn) {
    // 已登录用户不需要访问登录/注册页
    next({ name: 'Home' })
  } else {
    next()
  }
})

export default router
