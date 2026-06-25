import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

/**
 * Vitest 配置：与 vite.config.ts 共享 alias，但不需要 Element Plus 自动注册
 * （测试聚焦纯逻辑，不挂载 Element Plus 组件）。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  test: {
    environment: 'happy-dom',
    globals: false,
    include: ['src/**/__tests__/**/*.spec.ts'],
    css: false
  }
})
