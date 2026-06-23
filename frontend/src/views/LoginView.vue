<script setup lang="ts">
/**
 * 登录页
 * - 使用后端 /api/auth/login 与 /api/auth/register
 */
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { PhBookOpen, PhLockKey, PhSparkle, PhUser } from '@/components/icons'
import { login, register } from '@/api/auth'
import { useUserStore } from '@/stores/user'

interface AuthForm {
  username: string
  password: string
}

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const isRegisterMode = ref(false)
const loading = ref(false)
const form = reactive<AuthForm>({
  username: '',
  password: ''
})

const rules: FormRules<AuthForm> = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ]
}

function toggleMode() {
  isRegisterMode.value = !isRegisterMode.value
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    if (isRegisterMode.value) {
      await register(form)
      ElMessage.success('注册成功，已为你登录')
    }
    const response = await login(form)
    userStore.setToken(response.token, form.username.trim())
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/home'
    router.push(redirect)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <section class="brand-panel">
      <div class="brand-glow" />
      <div class="brand-content">
        <div class="brand-badge">
          <PhBookOpen :size="34" weight="duotone" />
        </div>
        <p class="eyebrow">RevMate · 智能学习助手</p>
        <h1 class="brand-title">你的复习资料，随时问</h1>
        <p class="brand-desc">
          上传 PDF、Word、TXT 或图片笔记，AI 会基于你的资料回答问题，并标出可靠来源。
        </p>
        <div class="feature-row">
          <span><PhSparkle :size="16" weight="fill" /> PDF 解析</span>
          <span><PhSparkle :size="16" weight="fill" /> 来源溯源</span>
          <span><PhSparkle :size="16" weight="fill" /> AI 出题</span>
        </div>
      </div>
    </section>

    <section class="form-panel">
      <div class="form-card">
        <div class="form-header">
          <h2>{{ isRegisterMode ? '创建账号' : '欢迎回来' }}</h2>
          <p>{{ isRegisterMode ? '注册后即可开始管理你的复习资料' : '登录后即可访问你的复习资料库' }}</p>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
          <el-form-item label="用户名" prop="username">
            <el-input v-model.trim="form.username" size="large" placeholder="输入用户名">
              <template #prefix>
                <PhUser :size="18" />
              </template>
            </el-input>
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model.trim="form.password" size="large" type="password" show-password placeholder="输入密码">
              <template #prefix>
                <PhLockKey :size="18" />
              </template>
            </el-input>
          </el-form-item>
          <el-button class="submit-btn" type="primary" size="large" :loading="loading" @click="submit">
            {{ isRegisterMode ? '注册并登录' : '登录' }}
          </el-button>
        </el-form>

        <div class="switch-line">
          <span>{{ isRegisterMode ? '已有账号？' : '还没有账号？' }}</span>
          <button type="button" @click="toggleMode">{{ isRegisterMode ? '立即登录' : '立即注册' }}</button>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 55% 45%;
  background: var(--color-card-bg);
}

.brand-panel {
  position: relative;
  overflow: hidden;
  background: var(--gradient-login-brand);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-3xl);
}

.brand-glow {
  position: absolute;
  width: 360px;
  height: 360px;
  border-radius: var(--radius-full);
  background: rgba(255, 255, 255, 0.14);
  filter: blur(18px);
  right: -80px;
  bottom: -100px;
}

.brand-content {
  position: relative;
  max-width: 520px;
}

.brand-badge {
  width: 72px;
  height: 72px;
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.18);
  display: grid;
  place-items: center;
  margin-bottom: var(--space-xl);
  box-shadow: var(--shadow-lg);
}

.eyebrow {
  font-size: var(--font-size-small);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.78);
  margin-bottom: var(--space-md);
}

.brand-title {
  color: #fff;
  font-size: 36px;
  font-weight: 600;
  margin-bottom: var(--space-lg);
}

.brand-desc {
  color: rgba(255, 255, 255, 0.86);
  font-size: 15px;
  line-height: 1.8;
  max-width: 440px;
}

.feature-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-md);
  margin-top: var(--space-3xl);
}

.feature-row span {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  padding: 8px 12px;
  border-radius: var(--radius-full);
  background: rgba(255, 255, 255, 0.16);
  color: rgba(255, 255, 255, 0.92);
  font-size: var(--font-size-small);
}

.form-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-3xl);
  background: var(--color-card-bg);
}

.form-card {
  width: min(100%, 400px);
}

.form-header {
  margin-bottom: var(--space-2xl);
}

.form-header h2 {
  font-size: var(--font-size-h1);
  margin-bottom: var(--space-sm);
}

.form-header p {
  color: var(--color-text-assist);
  font-size: var(--font-size-body);
}

:deep(.el-input__wrapper) {
  min-height: 44px;
  background: var(--color-page-bg);
  border-radius: var(--radius-md);
  box-shadow: none;
  padding: 0 var(--space-md);
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--color-primary) inset;
}

.submit-btn {
  width: 100%;
  margin-top: var(--space-sm);
  box-shadow: var(--shadow-button-primary);
  transition: transform var(--duration-fast) var(--ease-default);
}

.submit-btn:active {
  transform: scale(0.98);
}

.switch-line {
  margin-top: var(--space-xl);
  text-align: center;
  color: var(--color-text-assist);
  font-size: var(--font-size-body);
}

.switch-line button {
  margin-left: var(--space-xs);
  border: 0;
  background: transparent;
  color: var(--color-primary);
  cursor: pointer;
  font: inherit;
  font-weight: 500;
}

@media (max-width: 767px) {
  .login-page {
    grid-template-columns: 1fr;
    background: var(--gradient-login-brand);
    padding: var(--space-xl);
  }

  .brand-panel {
    min-height: auto;
    padding: var(--space-xl) 0;
    background: transparent;
  }

  .brand-title {
    font-size: 26px;
  }

  .brand-desc,
  .feature-row {
    display: none;
  }

  .form-panel {
    align-items: flex-start;
    padding: 0;
    background: transparent;
  }

  .form-card {
    padding: var(--space-xl);
    border-radius: var(--radius-lg);
    background: var(--color-card-bg);
    box-shadow: var(--shadow-lg);
  }
}
</style>
