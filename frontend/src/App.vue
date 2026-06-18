<script setup lang="ts">
import { ref } from 'vue'

type ApiResult<T> = {
  code: number
  message: string
  data?: T
}

type Material = {
  id: number
  subjectId: number
  filename: string
  type: string
  status: string
  createdAt: string
  previewable: boolean
  previewStatus: string
  previewMessage?: string
}

type Subject = {
  id: number
  name: string
  createdAt: string
}

type SourceItem = {
  type: string
  title: string
  snippet: string
  url?: string
  materialId?: number
  page?: string
}

type ChatResponse = {
  answer: string
  sources: SourceItem[]
}

type QuestionItem = {
  stem: string
  options?: string[]
  answer: string
  analysis: string
}

type QuizResponse = {
  questions: QuestionItem[]
}

type Panel = 'auth' | 'subjects' | 'materials' | 'chat' | 'quiz' | 'preview'

const username = ref('test')
const password = ref('123456')
const token = ref(localStorage.getItem('revmate-test-token') ?? '')
const selectedFile = ref<File | null>(null)
const subjectName = ref('Java')
const subjects = ref<Subject[]>([])
const selectedSubjectId = ref<number | ''>('')
const materials = ref<Material[]>([])
const selectedMaterialId = ref<number | ''>('')
const chatQuestion = ref('请根据我的资料总结重点')
const chatResult = ref<ChatResponse | null>(null)
const quizType = ref('single')
const quizCount = ref(5)
const quizResult = ref<QuizResponse | null>(null)
const busy = ref('')
const status = ref('后端地址: http://localhost:8080')
const raw = ref<Record<Panel, string>>({
  auth: '',
  subjects: '',
  materials: '',
  chat: '',
  quiz: '',
  preview: '',
})

function setRaw(panel: Panel, value: unknown) {
  raw.value[panel] = typeof value === 'string' ? value : JSON.stringify(value, null, 2)
}

function authorizedHeaders() {
  const headers = new Headers()
  if (token.value) {
    headers.set('Authorization', `Bearer ${token.value}`)
  }
  return headers
}

async function api<T>(panel: Panel, url: string, init: RequestInit = {}) {
  busy.value = panel
  status.value = `请求中: ${url}`

  try {
    const headers = new Headers(init.headers)
    if (!(init.body instanceof FormData)) {
      headers.set('Content-Type', 'application/json')
    }
    if (token.value) {
      headers.set('Authorization', `Bearer ${token.value}`)
    }

    const response = await fetch(url, { ...init, headers })
    const text = await response.text()
    const payload = text ? JSON.parse(text) : null
    setRaw(panel, payload)

    if (!response.ok) {
      throw new Error(payload?.message ?? `HTTP ${response.status}`)
    }

    const result = payload as ApiResult<T>
    if (result.code !== 0) {
      throw new Error(result.message || `业务错误 code=${result.code}`)
    }

    status.value = `完成: ${url}`
    return result.data as T
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    status.value = `失败: ${message}`
    throw error
  } finally {
    busy.value = ''
  }
}

async function register() {
  await api<void>('auth', '/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username: username.value, password: password.value }),
  })
}

async function login() {
  const data = await api<{ token: string }>('auth', '/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username: username.value, password: password.value }),
  })
  token.value = data.token
  localStorage.setItem('revmate-test-token', data.token)
  await listSubjects()
}

function clearToken() {
  token.value = ''
  localStorage.removeItem('revmate-test-token')
  setRaw('auth', 'Token 已清空')
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  selectedFile.value = input.files?.[0] ?? null
}

async function createSubject() {
  const data = await api<Subject>('subjects', '/api/subjects', {
    method: 'POST',
    body: JSON.stringify({ name: subjectName.value }),
  })
  selectedSubjectId.value = data.id
  await listSubjects()
}

async function listSubjects() {
  const data = await api<Subject[]>('subjects', '/api/subjects')
  subjects.value = data ?? []
  if (!selectedSubjectId.value && subjects.value.length > 0) {
    const firstSubject = subjects.value[0]
    if (firstSubject) {
      selectedSubjectId.value = firstSubject.id
    }
  }
  if (selectedSubjectId.value) {
    await listMaterials()
  }
}

async function deleteSubject(id: number) {
  await api<void>('subjects', `/api/subjects/${id}`, { method: 'DELETE' })
  if (selectedSubjectId.value === id) {
    selectedSubjectId.value = ''
    selectedMaterialId.value = ''
    materials.value = []
  }
  await listSubjects()
}

async function uploadMaterial() {
  if (!selectedSubjectId.value) {
    status.value = '请先选择学科'
    return
  }
  if (!selectedFile.value) {
    status.value = '请先选择文件'
    return
  }

  const form = new FormData()
  form.append('file', selectedFile.value)
  await api<Material>('materials', `/api/materials?subjectId=${selectedSubjectId.value}`, {
    method: 'POST',
    body: form,
  })
  await listMaterials()
}

async function listMaterials() {
  if (!selectedSubjectId.value) {
    status.value = '请先选择学科'
    materials.value = []
    selectedMaterialId.value = ''
    return
  }

  const data = await api<Material[]>('materials', `/api/materials?subjectId=${selectedSubjectId.value}`)
  materials.value = data ?? []
  if (!selectedMaterialId.value && materials.value.length > 0) {
    const firstMaterial = materials.value[0]
    if (firstMaterial) {
      selectedMaterialId.value = firstMaterial.id
    }
  }
}

async function deleteMaterial(id: number) {
  await api<void>('materials', `/api/materials/${id}`, { method: 'DELETE' })
  if (selectedMaterialId.value === id) {
    selectedMaterialId.value = ''
  }
  await listMaterials()
}

async function openPreview(material: Material) {
  busy.value = 'preview'
  status.value = `打开预览: ${material.filename}`

  try {
    const response = await fetch(`/api/materials/${material.id}/preview`, {
      headers: authorizedHeaders(),
    })

    if (!response.ok) {
      const text = await response.text()
      setRaw('preview', text)
      throw new Error(`HTTP ${response.status}`)
    }

    const blob = await response.blob()
    const url = URL.createObjectURL(blob)
    window.open(url, '_blank', 'noopener,noreferrer')
    setRaw('preview', {
      materialId: material.id,
      filename: material.filename,
      contentType: blob.type,
      bytes: blob.size,
    })
    status.value = `预览已打开: ${material.filename}`
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    status.value = `预览失败: ${message}`
  } finally {
    busy.value = ''
  }
}

async function askChat() {
  if (!selectedSubjectId.value) {
    status.value = '请先选择学科'
    return
  }

  chatResult.value = await api<ChatResponse>('chat', '/api/chat', {
    method: 'POST',
    body: JSON.stringify({ subjectId: selectedSubjectId.value, question: chatQuestion.value }),
  })
}

async function generateQuiz() {
  if (!selectedSubjectId.value) {
    status.value = '请先选择学科'
    return
  }

  const body: { subjectId: number; materialId?: number; type: string; count: number } = {
    subjectId: selectedSubjectId.value,
    type: quizType.value,
    count: quizCount.value,
  }
  if (selectedMaterialId.value) {
    body.materialId = selectedMaterialId.value
  }

  quizResult.value = await api<QuizResponse>('quiz', '/api/quiz', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}
</script>

<template>
  <main class="shell">
    <header class="topbar">
      <div>
        <h1>RevMate 功能测试台</h1>
        <p>临时前端，用来跑通登录、资料、预览、问答和出题接口。</p>
      </div>
      <div class="status" :class="{ active: busy }">{{ status }}</div>
    </header>

    <section class="panel auth-panel">
      <div class="panel-head">
        <h2>1. 登录 / 注册</h2>
        <button type="button" @click="clearToken">清空 Token</button>
      </div>
      <div class="grid form-grid">
        <label>
          用户名
          <input v-model="username" autocomplete="username" />
        </label>
        <label>
          密码
          <input v-model="password" type="password" autocomplete="current-password" />
        </label>
        <button type="button" :disabled="busy === 'auth'" @click="register">注册</button>
        <button type="button" :disabled="busy === 'auth'" @click="login">登录并保存 Token</button>
      </div>
      <label>
        当前 Token
        <textarea v-model="token" rows="3" spellcheck="false"></textarea>
      </label>
      <pre>{{ raw.auth }}</pre>
    </section>

    <section class="panel">
      <div class="panel-head">
        <h2>2. 学科管理</h2>
        <button type="button" :disabled="busy === 'subjects'" @click="listSubjects">
          刷新学科
        </button>
      </div>

      <div class="grid subject-grid">
        <label>
          新学科名称
          <input v-model="subjectName" />
        </label>
        <button type="button" :disabled="busy === 'subjects'" @click="createSubject">
          创建学科
        </button>
        <label>
          当前学科
          <select v-model="selectedSubjectId" @change="listMaterials">
            <option value="">请选择学科</option>
            <option v-for="subject in subjects" :key="subject.id" :value="subject.id">
              #{{ subject.id }} {{ subject.name }}
            </option>
          </select>
        </label>
      </div>

      <div class="subject-list">
        <button
          v-for="subject in subjects"
          :key="subject.id"
          type="button"
          :class="{ secondary: selectedSubjectId !== subject.id }"
          @click="selectedSubjectId = subject.id; listMaterials()"
        >
          {{ subject.name }}
        </button>
        <button
          v-if="selectedSubjectId"
          type="button"
          class="danger"
          @click="deleteSubject(Number(selectedSubjectId))"
        >
          删除当前学科
        </button>
      </div>

      <pre>{{ raw.subjects }}</pre>
    </section>

    <section class="panel">
      <div class="panel-head">
        <h2>3. 资料管理 / 预览</h2>
        <button type="button" :disabled="busy === 'materials'" @click="listMaterials">
          刷新资料列表
        </button>
      </div>

      <div class="upload-row">
        <input
          type="file"
          accept=".txt,.pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,image/*,audio/*"
          @change="onFileChange"
        />
        <button type="button" :disabled="busy === 'materials'" @click="uploadMaterial">
          上传资料
        </button>
      </div>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>文件名</th>
              <th>类型</th>
              <th>处理状态</th>
              <th>预览状态</th>
              <th>预览信息</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in materials" :key="item.id">
              <td>{{ item.id }}</td>
              <td>{{ item.filename }}</td>
              <td>{{ item.type }}</td>
              <td>
                <span class="badge">{{ item.status }}</span>
              </td>
              <td>
                <span class="badge" :class="item.previewStatus.toLowerCase()">
                  {{ item.previewStatus }}
                </span>
              </td>
              <td>{{ item.previewMessage || '-' }}</td>
              <td class="actions">
                <button type="button" @click="selectedMaterialId = item.id">选中</button>
                <button
                  type="button"
                  :disabled="!item.previewable && item.previewStatus !== 'READY'"
                  @click="openPreview(item)"
                >
                  预览
                </button>
                <button type="button" class="danger" @click="deleteMaterial(item.id)">删除</button>
              </td>
            </tr>
            <tr v-if="materials.length === 0">
              <td colspan="7" class="empty">暂无资料，先登录并选择学科后上传文件。</td>
            </tr>
          </tbody>
        </table>
      </div>
      <pre>{{ raw.materials }}</pre>
      <pre>{{ raw.preview }}</pre>
    </section>

    <section class="panel two-column">
      <div>
        <h2>4. RAG 问答</h2>
        <label>
          问题
          <textarea v-model="chatQuestion" rows="4"></textarea>
        </label>
        <button type="button" :disabled="busy === 'chat'" @click="askChat">发送问题</button>

        <div v-if="chatResult" class="result">
          <h3>回答</h3>
          <p>{{ chatResult.answer }}</p>
          <h3>来源</h3>
          <ul>
            <li v-for="source in chatResult.sources" :key="`${source.type}-${source.title}`">
              <strong>{{ source.title }}</strong>
              <span>{{ source.snippet }}</span>
            </li>
          </ul>
        </div>
        <pre>{{ raw.chat }}</pre>
      </div>

      <div>
        <h2>5. AI 出题</h2>
        <label>
          出题范围
          <select v-model="selectedMaterialId">
            <option value="">整个当前学科</option>
            <option v-for="item in materials" :key="item.id" :value="item.id">
              #{{ item.id }} {{ item.filename }}
            </option>
          </select>
        </label>
        <div class="grid quiz-grid">
          <label>
            题型
            <select v-model="quizType">
              <option value="single">single 单选</option>
              <option value="fill">fill 填空</option>
              <option value="qa">qa 简答</option>
            </select>
          </label>
          <label>
            数量
            <input v-model.number="quizCount" type="number" min="1" max="20" />
          </label>
        </div>
        <button type="button" :disabled="busy === 'quiz'" @click="generateQuiz">生成题目</button>

        <div v-if="quizResult" class="result">
          <article v-for="(question, index) in quizResult.questions" :key="index" class="question">
            <h3>{{ index + 1 }}. {{ question.stem }}</h3>
            <ol v-if="question.options?.length">
              <li v-for="option in question.options" :key="option">{{ option }}</li>
            </ol>
            <p><strong>答案：</strong>{{ question.answer }}</p>
            <p><strong>解析：</strong>{{ question.analysis }}</p>
          </article>
        </div>
        <pre>{{ raw.quiz }}</pre>
      </div>
    </section>
  </main>
</template>

<style scoped>
* {
  box-sizing: border-box;
}

.shell {
  width: min(1200px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 24px 0 48px;
  color: #172033;
  font-family:
    Inter, 'Microsoft YaHei', 'PingFang SC', system-ui, -apple-system, BlinkMacSystemFont, sans-serif;
}

.topbar {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
}

h1,
h2,
h3,
p {
  margin: 0;
}

h1 {
  font-size: 28px;
}

h2 {
  font-size: 18px;
}

h3 {
  margin-bottom: 8px;
  font-size: 15px;
}

.topbar p {
  margin-top: 8px;
  color: #5b6475;
}

.status {
  max-width: 420px;
  padding: 10px 12px;
  border: 1px solid #d7dce5;
  border-radius: 8px;
  background: #f7f9fc;
  color: #42506a;
  line-height: 1.5;
}

.status.active {
  border-color: #8bb6ff;
  background: #eef5ff;
}

.panel {
  margin-top: 16px;
  padding: 18px;
  border: 1px solid #dce1ea;
  border-radius: 8px;
  background: #ffffff;
}

.panel-head,
.upload-row,
.actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.panel-head {
  justify-content: space-between;
  margin-bottom: 14px;
}

.grid {
  display: grid;
  gap: 12px;
}

.form-grid {
  grid-template-columns: minmax(160px, 1fr) minmax(160px, 1fr) auto auto;
  align-items: end;
}

.quiz-grid {
  grid-template-columns: minmax(160px, 1fr) minmax(100px, 140px);
}

.subject-grid {
  grid-template-columns: minmax(180px, 1fr) auto minmax(220px, 1fr);
  align-items: end;
}

.subject-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}

.two-column {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 18px;
}

label {
  display: grid;
  gap: 6px;
  margin-bottom: 12px;
  color: #3d485c;
  font-size: 14px;
}

input,
select,
textarea,
button {
  min-height: 36px;
  border: 1px solid #cbd3df;
  border-radius: 6px;
  font: inherit;
}

input,
select,
textarea {
  width: 100%;
  padding: 8px 10px;
  background: #ffffff;
}

textarea {
  resize: vertical;
}

button {
  padding: 0 12px;
  border-color: #265fb7;
  background: #2f6fd0;
  color: #ffffff;
  cursor: pointer;
}

button:disabled {
  border-color: #b9c0cc;
  background: #c8ced8;
  cursor: not-allowed;
}

button.danger {
  border-color: #b83d4a;
  background: #cf4b59;
}

button.secondary {
  border-color: #cbd3df;
  background: #eef2f8;
  color: #334155;
}

.table-wrap {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

th,
td {
  padding: 10px;
  border-bottom: 1px solid #edf0f5;
  text-align: left;
  vertical-align: top;
}

th {
  background: #f6f8fb;
  color: #4c5870;
  font-weight: 600;
}

.badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  background: #edf1f7;
  color: #435066;
  font-size: 12px;
}

.badge.ready {
  background: #e8f7ef;
  color: #1f7a4c;
}

.badge.failed {
  background: #ffedf0;
  color: #bd2f43;
}

.badge.processing {
  background: #fff6dd;
  color: #8a6200;
}

.empty {
  color: #7a8495;
  text-align: center;
}

.result {
  display: grid;
  gap: 10px;
  margin-top: 14px;
  padding: 14px;
  border-radius: 8px;
  background: #f7f9fc;
}

.result ul,
.result ol {
  margin: 0;
  padding-left: 22px;
}

.result li {
  margin-bottom: 8px;
}

.result li span {
  display: block;
  margin-top: 4px;
  color: #5c6678;
}

.question {
  padding-bottom: 12px;
  border-bottom: 1px solid #e3e7ef;
}

pre {
  max-height: 260px;
  overflow: auto;
  margin: 14px 0 0;
  padding: 12px;
  border-radius: 8px;
  background: #101828;
  color: #e5edff;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
}

@media (max-width: 860px) {
  .topbar,
  .two-column {
    grid-template-columns: 1fr;
    display: grid;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .subject-grid {
    grid-template-columns: 1fr;
  }

  .panel-head,
  .upload-row,
  .actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
