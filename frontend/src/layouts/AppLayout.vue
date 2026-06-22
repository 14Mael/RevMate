<script setup lang="ts">
/**
 * AppLayout
 * - 桌面端：顶部导航栏（品牌 + 菜单 + 用户区） + 主内容
 * - 移动端：占位（任务 9 响应式适配）
 *
 * 设计依据：frontend-design-spec.md §8.1
 */
import { computed } from 'vue'
import { useRouter, useRoute, RouterView, RouterLink } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { PhBookOpen, PhSparkle, PhCheckSquare, PhSignOut, PhClockCounterClockwise } from '@/components/icons'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

interface NavItem {
  path: string
  label: string
  icon: 'PhSparkle' | 'PhBookOpen' | 'PhCheckSquare' | 'PhClockCounterClockwise'
}

const navItems: NavItem[] = [
  { path: '/home', label: '智能问答', icon: 'PhSparkle' },
  { path: '/materials', label: '我的资料', icon: 'PhBookOpen' },
  { path: '/wrong-questions', label: '错题本', icon: 'PhClockCounterClockwise' },
  { path: '/quiz', label: 'AI 出题', icon: 'PhCheckSquare' }
]

const iconMap = {
  PhSparkle,
  PhBookOpen,
  PhCheckSquare,
  PhClockCounterClockwise
} as const

const activePath = computed(() => route.path)

function goHome() {
  router.push('/home')
}

function logout() {
  userStore.clear()
  router.push('/login')
}
</script>

<template>
  <div class="app-layout">
    <!-- 顶部导航栏 -->
    <header class="topnav">
      <!-- 品牌 -->
      <div class="brand" @click="goHome">
        <div class="brand-mark">
          <PhBookOpen :size="20" weight="duotone" />
        </div>
        <div class="brand-text">
          <div class="brand-name">RevMate</div>
        </div>
      </div>

      <!-- 中部菜单 -->
      <nav class="nav">
        <RouterLink
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ 'nav-item--active': activePath.startsWith(item.path) }"
        >
          <component :is="iconMap[item.icon]" :size="18" weight="duotone" class="nav-icon" />
          <span class="nav-label">{{ item.label }}</span>
        </RouterLink>
      </nav>

      <!-- 右侧用户区 -->
      <div class="user-area">
        <div class="user-info">
          <div class="user-avatar">{{ (userStore.username || '?').charAt(0).toUpperCase() }}</div>
          <span class="user-name">{{ userStore.username || '未登录' }}</span>
        </div>
        <button class="logout-btn" @click="logout" title="退出登录">
          <PhSignOut :size="16" />
          <span>退出</span>
        </button>
      </div>
    </header>

    <!-- 主区域 -->
    <main class="content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.app-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 100%;
  background-color: var(--color-page-bg);
}

/* ---------- 顶部导航栏 ---------- */
.topnav {
  height: var(--header-height);
  display: flex;
  align-items: center;
  gap: var(--space-xl);
  padding: 0 var(--space-2xl);
  background: var(--color-card-bg);
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
  position: relative;
  z-index: 10;
}

/* 品牌 */
.brand {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  cursor: pointer;
  user-select: none;
  padding: 4px 8px;
  border-radius: var(--radius-md);
  transition: background var(--duration-fast);
}
.brand:hover {
  background: var(--color-primary-light);
}

.brand-mark {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-md);
  background: var(--gradient-login-brand);
  color: #fff;
  display: grid;
  place-items: center;
  box-shadow: var(--shadow-button-primary);
}

.brand-name {
  font-size: var(--font-size-h3);
  font-weight: 700;
  color: var(--color-text-title);
  line-height: 1.2;
  letter-spacing: -0.01em;
}

/* 菜单 */
.nav {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: 1;
  justify-content: center;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: var(--radius-md);
  color: var(--color-text-body);
  font-size: var(--font-size-body);
  font-weight: 500;
  text-decoration: none;
  transition: all var(--duration-fast) var(--ease-default);
  position: relative;
}

.nav-item:hover {
  background: var(--color-primary-light);
  color: var(--color-primary);
}

.nav-item--active {
  color: var(--color-primary);
  background: var(--color-primary-light);
  font-weight: 600;
}

.nav-item--active::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 50%;
  transform: translateX(-50%);
  width: 24px;
  height: 2px;
  border-radius: 1px;
  background: var(--color-primary);
}

.nav-icon {
  flex-shrink: 0;
}

/* 用户区 */
.user-area {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.user-info {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 4px 12px 4px 4px;
  border-radius: var(--radius-full);
  background: var(--color-page-bg);
}

.user-avatar {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-full);
  background: var(--gradient-login-brand);
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 600;
  font-size: var(--font-size-small);
}

.user-name {
  font-size: var(--font-size-body);
  color: var(--color-text-body);
  font-weight: 500;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.logout-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-assist);
  font-size: var(--font-size-small);
  cursor: pointer;
  transition: all var(--duration-fast);
}

.logout-btn:hover {
  color: var(--color-danger);
  border-color: var(--color-danger);
  background: color-mix(in srgb, var(--color-danger) 6%, transparent);
}

/* ---------- 主内容 ---------- */
.content {
  flex: 1;
  overflow: auto;
  padding: var(--space-2xl);
}

/* ---------- 移动端占位 ---------- */
@media (max-width: 1023px) {
  .topnav {
    padding: 0 var(--space-md);
    gap: var(--space-sm);
  }
  .user-name {
    display: none;
  }
  .logout-btn span {
    display: none;
  }
  .nav {
    gap: 0;
  }
  .nav-label {
    display: none;
  }
  .nav-item {
    padding: 8px 12px;
  }
}
</style>
