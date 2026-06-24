/**
 * RevMate 入口
 * 装配顺序：Pinia → Router → 全局样式（含 Element Plus 主题覆盖） → 挂载
 */

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'

// 样式顺序：变量 → 全局 → 主题覆盖
import './assets/styles/variables.css'
import './assets/styles/global.css'
import './assets/styles/element-overrides.scss'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus, {
  message: {
    offset: 72
  }
})
app.mount('#app')
