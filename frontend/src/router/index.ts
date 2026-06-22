/**
 * RevMate 路由
 * - 路由表见 .trae/documents/frontend-design-spec.md §7.1
 * - 全局守卫：未登录访问受保护页跳 /login；已登录访问 /login 跳 /home
 */

import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layouts/AppLayout.vue'),
    redirect: '/home',
    children: [
      {
        path: 'home',
        name: 'Home',
        component: () => import('@/views/HomeView.vue'),
        meta: { title: '智能问答' }
      },
      {
        path: 'materials',
        name: 'Materials',
        component: () => import('@/views/MaterialsView.vue'),
        meta: { title: '我的资料' }
      },
      {
        path: 'materials/:id',
        name: 'MaterialDetail',
        component: () => import('@/views/MaterialDetailView.vue'),
        meta: { title: '资料预览' }
      },
      {
        path: 'quiz',
        name: 'Quiz',
        component: () => import('@/views/QuizView.vue'),
        meta: { title: 'AI 出题' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/LoginView.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  const isPublic = to.meta?.public === true
  if (isPublic && userStore.token) {
    return { path: '/home' }
  }
  if (!isPublic && !userStore.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

router.afterEach((to) => {
  const title = (to.meta?.title as string) || 'RevMate'
  document.title = `${title} · RevMate`
})

export default router
