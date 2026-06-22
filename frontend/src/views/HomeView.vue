<script setup lang="ts">
/**
 * 智能问答页（核心演示页）
 *
 * 功能：
 * - 流式问答交互（逐段动画）
 * - 课程筛选下拉
 * - 来源引用卡片
 * - 引用文本高亮（来自资料预览选中）
 * - Markdown 渲染回答
 */
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { chatStreamWithFallback, getHistoryList, saveHistory, generateHistoryId } from '@/api/chat'
import { listSubjects } from '@/api/subject'
import { renderMarkdown } from '@/utils/markdown'
import ChatHistoryPanel from '@/components/ChatHistoryPanel.vue'
import { PhPaperPlaneTilt, PhSparkle, PhBookOpen, PhQuotes, PhCaretDown, PhClockCounterClockwise } from '@/components/icons'
import type { ChatHistoryItem, Source, Subject } from '@/api/types'

const route = useRoute()

/* ==================== 消息数据 ==================== */

interface ChatMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  sources: Source[]
  isStreaming: boolean
  quote?: string
}

const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const isGenerating = ref(false)
const selectedSubjectId = ref<number | ''>('')
const subjects = ref<Subject[]>([])
const quoteFromPreview = ref('')

const chatContainer = ref<HTMLDivElement>()

/* ==================== 历史记录 ==================== */

const sessionId = ref(generateHistoryId())
const historyList = ref<ChatHistoryItem[]>([])
const historyPanelVisible = ref(false)
const subjectNameMap = ref<Record<number, string>>({})

async function refreshHistoryList() {
  try {
    historyList.value = await getHistoryList()
  } catch {
    historyList.value = []
  }
}

function currentSessionTitle(): string {
  const firstUser = messages.value.find((m) => m.role === 'user')
  if (firstUser) return firstUser.content.slice(0, 40)
  return '新对话'
}

async function persistCurrentSession() {
  if (messages.value.length === 0) return
  const title = currentSessionTitle()
  const historyMessages = messages.value.map((m) => ({
    role: m.role,
    content: m.content,
    timestamp: new Date().toISOString()
  }))
  try {
    await saveHistory({
      id: sessionId.value,
      title,
      messages: historyMessages,
      createdAt: new Date().toISOString(),
      subjectId: selectedSubjectId.value as number,
      course: subjectNameMap.value[selectedSubjectId.value as number] || undefined
    })
    await refreshHistoryList()
  } catch {
    // 历史保存失败不影响问答，静默忽略
  }
}

function loadSession(item: ChatHistoryItem) {
  sessionId.value = item.id
  messages.value = item.messages.map((m, i) => ({
    id: nextId++,
    role: m.role,
    content: m.content,
    sources: [],
    isStreaming: false
  }))
  // 恢复课程选择
  if (item.subjectId) {
    selectedSubjectId.value = item.subjectId
  }
}

function startNewSession() {
  sessionId.value = generateHistoryId()
  messages.value = []
  nextId = 1
}

/* ==================== 初始化 ==================== */

onMounted(async () => {
  // 检查是否有来自预览页的引用文本
  const q = (route.query.quote as string) || ''
  if (q) {
    quoteFromPreview.value = q
    inputText.value = q
  }
  // 加载课程列表
  try {
    subjects.value = await listSubjects()
    selectedSubjectId.value = subjects.value[0]?.id ?? ''
    subjects.value.forEach((s) => {
      subjectNameMap.value[s.id] = s.name
    })
  } catch {
    subjects.value = []
  }
  // 加载最近一次历史
  await refreshHistoryList()
  const latest = historyList.value[0]
  if (latest) {
    loadSession(latest)
  }
})

/* ==================== 发送消息 ==================== */

let nextId = 1

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || isGenerating.value) return
  if (!selectedSubjectId.value) {
    ElMessage.warning('请先在资料页上传资料并选择课程')
    return
  }

  const quote = quoteFromPreview.value || undefined
  const question = quote ? `${text}\n\n引用内容：${quote}` : text
  quoteFromPreview.value = ''

  // 添加用户消息
  messages.value.push({
    id: nextId++,
    role: 'user',
    content: text,
    sources: [],
    isStreaming: false,
    quote
  })
  inputText.value = ''
  scrollToBottom()

  // 添加 AI 占位消息
  const aiMsg: ChatMessage = {
    id: nextId++,
    role: 'assistant',
    content: '',
    sources: [],
    isStreaming: true
  }
  messages.value.push(aiMsg)
  // 取回数组内的响应式代理，直接改原始 aiMsg 不会触发逐字重渲染
  const streamingMsg = messages.value[messages.value.length - 1]
  isGenerating.value = true
  scrollToBottom()

  try {
    const gen = chatStreamWithFallback({
      subjectId: selectedSubjectId.value,
      question
    })
    for await (const chunk of gen) {
      streamingMsg.content += chunk.text
      if (chunk.done) {
        streamingMsg.sources = chunk.sources
        streamingMsg.isStreaming = false
        isGenerating.value = false
        await persistCurrentSession()
      }
      scrollToBottom()
    }
  } catch (e) {
    streamingMsg.content = '抱歉，请求失败，请稍后重试。'
    streamingMsg.isStreaming = false
    isGenerating.value = false
    ElMessage.error('问答请求失败')
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

/* ==================== 快捷提问 ==================== */

const quickQuestions = [
  '进程的基本概念是什么？',
  '常见的 CPU 调度算法有哪些？',
  'TCP 三次握手的过程是怎样的？',
  'SQL 的增删改查如何操作？'
]

function askQuick(question: string) {
  inputText.value = question
  sendMessage()
}

/* ==================== 工具 ==================== */

function scrollToBottom() {
  nextTick(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight
    }
  })
}

function clearQuote() {
  quoteFromPreview.value = ''
  inputText.value = ''
}

const showWelcome = computed(() => messages.value.length === 0)

/** 来源图标 */
const sourceIcon = {
  material: PhBookOpen,
  web: PhBookOpen
} as const
</script>

<template>
  <div class="chat-page">
    <!-- 顶部工具栏 -->
    <header class="chat-header">
      <div class="header-left">
        <PhSparkle :size="22" weight="duotone" class="header-icon" />
        <div>
          <h1 class="header-title">智能问答</h1>
          <p class="header-sub">基于你的复习资料，AI 为你解答</p>
        </div>
      </div>
      <div class="header-right">
        <button class="history-btn" @click="historyPanelVisible = true" title="历史记录">
          <PhClockCounterClockwise :size="16" />
        </button>
        <div class="course-selector">
          <PhBookOpen :size="16" class="course-icon" />
          <select v-model.number="selectedSubjectId" class="course-select">
            <option disabled value="">选择课程</option>
            <option v-for="subject in subjects" :key="subject.id" :value="subject.id">{{ subject.name }}</option>
          </select>
          <PhCaretDown :size="14" class="select-arrow" />
        </div>
      </div>
    </header>

    <!-- 引用预览条 -->
    <div v-if="quoteFromPreview" class="quote-bar">
      <PhQuotes :size="16" class="quote-icon" />
      <span class="quote-text">基于选中的内容提问：{{ quoteFromPreview }}</span>
      <button class="quote-clear" @click="clearQuote">清除</button>
    </div>

    <!-- 消息区域 -->
    <main class="chat-body" ref="chatContainer">
      <!-- 欢迎态 -->
      <div v-if="showWelcome" class="welcome">
        <div class="welcome-icon">
          <PhSparkle :size="48" weight="duotone" />
        </div>
        <h2 class="welcome-title">有什么学习问题？</h2>
        <p class="welcome-desc">
          我可以基于你上传的复习资料，为你解答课程相关的问题。<br />
          <template v-if="selectedSubjectId">
            当前限定课程：<strong>{{ subjects.find((subject) => subject.id === selectedSubjectId)?.name }}</strong>
          </template>
          <template v-else>
            请先到资料页上传资料并选择课程。
          </template>
        </p>
        <div class="quick-actions">
          <button
            v-for="q in quickQuestions"
            :key="q"
            class="quick-btn"
            @click="askQuick(q)"
          >
            {{ q }}
          </button>
        </div>
      </div>

      <!-- 消息列表 -->
      <div v-for="msg in messages" :key="msg.id" class="message" :class="`msg-${msg.role}`">
        <!-- 用户消息 -->
        <template v-if="msg.role === 'user'">
          <div class="msg-bubble user-bubble">
            <div v-if="msg.quote" class="msg-quote">
              <PhQuotes :size="12" />
              {{ msg.quote }}
            </div>
            <div class="msg-text">{{ msg.content }}</div>
          </div>
        </template>

        <!-- AI 消息 -->
        <template v-else>
          <div class="msg-bubble ai-bubble">
            <div class="msg-text markdown-body" v-html="renderMarkdown(msg.content)" />
            <span v-if="msg.isStreaming" class="typing-indicator">
              <span class="dot" />
              <span class="dot" />
              <span class="dot" />
            </span>
          </div>
          <!-- 来源引用 -->
          <div v-if="msg.sources.length > 0 && !msg.isStreaming" class="sources-section">
            <div class="sources-label">参考来源</div>
            <div class="sources-list">
              <article v-for="(src, i) in msg.sources" :key="i" class="source-card">
                <component :is="sourceIcon[src.type]" :size="14" class="source-type-icon" />
                <div class="source-body">
                  <div class="source-title">{{ src.title }}</div>
                  <div class="source-snippet">{{ src.snippet }}</div>
                  <div class="source-meta" v-if="src.page">来自 {{ src.page }}</div>
                </div>
              </article>
            </div>
          </div>
        </template>
      </div>

      <!-- 占位底部分隔 -->
      <div class="chat-bottom-spacer" />
    </main>

    <!-- 输入区域 -->
    <footer class="chat-input-area">
      <div class="input-row">
        <textarea
          v-model="inputText"
          class="chat-input"
          placeholder="输入你的学习问题..."
          rows="1"
          autofocus
          :disabled="isGenerating"
          @keydown="handleKeydown"
        />
        <button
          class="send-btn"
          :disabled="!inputText.trim() || isGenerating"
          @click="sendMessage"
        >
          <PhPaperPlaneTilt :size="18" weight="fill" />
        </button>
      </div>
      <p class="input-hint">按 Enter 发送，Shift+Enter 换行</p>
    </footer>

    <!-- 历史记录面板 -->
    <ChatHistoryPanel
      :visible="historyPanelVisible"
      :active-id="sessionId"
      :histories="historyList"
      :courses="subjectNameMap"
      @close="historyPanelVisible = false"
      @load="loadSession"
      @updated="refreshHistoryList"
    />
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 64px);
  max-height: calc(100vh - 64px);
  background: var(--color-page-bg);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

/* ==================== Header ==================== */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-md) var(--space-xl);
  border-bottom: 1px solid var(--color-border);
  background: var(--color-card-bg);
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.header-icon {
  color: var(--color-primary);
  flex-shrink: 0;
}

.header-title {
  font-size: var(--font-size-h3);
  font-weight: 700;
  color: var(--color-text-title);
  margin: 0;
  line-height: 1.4;
}

.header-sub {
  font-size: var(--font-size-small);
  color: var(--color-text-assist);
  margin: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.history-btn {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-assist);
  cursor: pointer;
  transition: all var(--duration-fast);
}
.history-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.course-selector {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  padding: 6px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-page-bg);
  position: relative;
}

.course-icon {
  color: var(--color-primary);
  flex-shrink: 0;
}

.course-select {
  border: 0;
  background: transparent;
  color: var(--color-text-body);
  font-size: var(--font-size-body);
  outline: none;
  cursor: pointer;
  appearance: none;
  padding-right: 16px;
  min-width: 100px;
}

.select-arrow {
  position: absolute;
  right: 10px;
  pointer-events: none;
  color: var(--color-text-assist);
}

/* ==================== Quote Bar ==================== */
.quote-bar {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-sm) var(--space-xl);
  background: var(--color-primary-light);
  border-bottom: 1px solid color-mix(in srgb, var(--color-primary) 15%, transparent);
  flex-shrink: 0;
  font-size: var(--font-size-small);
  color: var(--color-primary);
}

.quote-icon {
  flex-shrink: 0;
}

.quote-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quote-clear {
  border: 0;
  background: transparent;
  color: var(--color-text-assist);
  font-size: var(--font-size-small);
  cursor: pointer;
  flex-shrink: 0;
  text-decoration: underline;
}
.quote-clear:hover {
  color: var(--color-text-body);
}

/* ==================== Body ==================== */
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-xl);
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
  scroll-behavior: smooth;
}

.chat-bottom-spacer {
  height: var(--space-xs);
}

/* Welcome */
.welcome {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  min-height: 360px;
}

.welcome-icon {
  color: var(--color-primary);
  margin-bottom: var(--space-lg);
  opacity: 0.85;
}

.welcome-title {
  font-size: var(--font-size-h1);
  font-weight: 700;
  color: var(--color-text-title);
  margin: 0 0 var(--space-md) 0;
}

.welcome-desc {
  font-size: var(--font-size-body);
  color: var(--color-text-assist);
  margin: 0 0 var(--space-xl) 0;
  max-width: 420px;
  line-height: var(--line-height-body);
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
  justify-content: center;
  max-width: 520px;
}

.quick-btn {
  padding: 8px 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-full);
  background: var(--color-card-bg);
  color: var(--color-text-body);
  font-size: var(--font-size-small);
  cursor: pointer;
  transition: all var(--duration-fast);
  white-space: nowrap;
}

.quick-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--color-primary-light);
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

/* ==================== Messages ==================== */
.message {
  display: flex;
  flex-direction: column;
  max-width: 820px;
}

.msg-user {
  align-self: flex-end;
  align-items: flex-end;
}

.msg-assistant {
  align-self: flex-start;
  align-items: flex-start;
  width: 100%;
}

/* Bubbles */
.msg-bubble {
  padding: var(--space-lg);
  border-radius: var(--radius-lg);
  font-size: var(--font-size-body);
  line-height: var(--line-height-body);
  overflow-wrap: break-word;
}

.user-bubble {
  background: var(--color-primary);
  color: #fff;
  max-width: 72%;
  width: fit-content;
  border-bottom-right-radius: var(--radius-sm);
}

.msg-text {
  white-space: pre-wrap;
}

.ai-bubble {
  background: var(--color-card-bg);
  color: var(--color-text-body);
  border: 1px solid var(--color-border);
  max-width: 100%;
  border-bottom-left-radius: var(--radius-sm);
}

.msg-quote {
  margin-bottom: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  background: rgba(255, 255, 255, 0.15);
  border-radius: var(--radius-sm);
  font-size: var(--font-size-small);
  opacity: 0.9;
  display: flex;
  align-items: flex-start;
  gap: var(--space-xs);
  line-height: 1.5;
}

/* Typing indicator */
.typing-indicator {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 0;
}

.typing-indicator .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-text-assist);
  animation: typing-bounce 1.2s infinite ease-in-out both;
}
.typing-indicator .dot:nth-child(1) { animation-delay: 0s; }
.typing-indicator .dot:nth-child(2) { animation-delay: 0.15s; }
.typing-indicator .dot:nth-child(3) { animation-delay: 0.3s; }

@keyframes typing-bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

/* Markdown body */
.markdown-body :deep(p) {
  margin: 0 0 var(--space-sm);
}
.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(strong) {
  color: var(--color-text-title);
  font-weight: 700;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  color: var(--color-text-title);
  font-weight: 700;
  line-height: 1.35;
  margin: var(--space-md) 0 var(--space-sm);
}
.markdown-body :deep(h1) { font-size: 1.3em; }
.markdown-body :deep(h2) { font-size: 1.2em; }
.markdown-body :deep(h3) { font-size: 1.08em; }
.markdown-body :deep(h4) { font-size: 1em; }
.markdown-body :deep(*:first-child) {
  margin-top: 0;
}

.markdown-body :deep(a) {
  color: var(--color-primary);
  text-decoration: underline;
}

.markdown-body :deep(code) {
  padding: 1px 5px;
  border-radius: var(--radius-sm);
  background: var(--color-page-bg);
  color: var(--color-primary);
  font-size: 0.92em;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
}

.markdown-body :deep(pre) {
  margin: var(--space-md) 0;
  padding: var(--space-md);
  border-radius: var(--radius-md);
  background: var(--color-page-bg);
  border: 1px solid var(--color-border);
  overflow-x: auto;
  font-size: var(--font-size-small);
  line-height: 1.6;
}

.markdown-body :deep(pre code) {
  background: transparent;
  padding: 0;
  color: var(--color-text-body);
}

.markdown-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: var(--space-md) 0;
  font-size: var(--font-size-small);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.markdown-body :deep(th) {
  padding: var(--space-sm) var(--space-md);
  text-align: left;
  font-weight: 700;
  color: var(--color-text-title);
  background: var(--color-page-bg);
  border-bottom: 1px solid var(--color-border);
  border-right: 1px solid var(--color-border);
}

.markdown-body :deep(td) {
  padding: var(--space-sm) var(--space-md);
  border-bottom: 1px solid var(--color-border);
  border-right: 1px solid var(--color-border);
}

.markdown-body :deep(tr:last-child td) {
  border-bottom: 0;
}

.markdown-body :deep(th:last-child),
.markdown-body :deep(td:last-child) {
  border-right: 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: var(--space-sm) 0;
  padding-left: var(--space-xl);
}

.markdown-body :deep(li) {
  margin-bottom: var(--space-xs);
}

/* ==================== Sources ==================== */
.sources-section {
  margin-top: var(--space-md);
  max-width: 100%;
}

.sources-label {
  font-size: var(--font-size-caption);
  font-weight: 600;
  color: var(--color-text-assist);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: var(--space-sm);
}

.sources-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
}

.source-card {
  display: flex;
  gap: var(--space-sm);
  padding: var(--space-md);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-card-bg);
  min-width: 260px;
  max-width: 360px;
  flex: 1 1 260px;
  transition: all var(--duration-fast);
}

.source-card:hover {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-sm);
}

.source-type-icon {
  color: var(--color-primary);
  flex-shrink: 0;
  margin-top: 2px;
}

.source-body {
  min-width: 0;
}

.source-title {
  font-size: var(--font-size-small);
  font-weight: 600;
  color: var(--color-text-title);
  margin-bottom: 2px;
}

.source-snippet {
  font-size: var(--font-size-caption);
  color: var(--color-text-assist);
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.source-meta {
  font-size: var(--font-size-caption);
  color: var(--color-primary);
  margin-top: 4px;
}

/* ==================== Input ==================== */
.chat-input-area {
  flex-shrink: 0;
  padding: var(--space-md) var(--space-xl) var(--space-lg);
  border-top: 1px solid var(--color-border);
  background: var(--color-card-bg);
}

.input-row {
  display: flex;
  align-items: flex-end;
  gap: var(--space-sm);
}

.chat-input {
  flex: 1;
  resize: none;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 10px 14px;
  font-size: var(--font-size-body);
  font-family: inherit;
  line-height: var(--line-height-body);
  background: var(--color-page-bg);
  color: var(--color-text-body);
  outline: none;
  transition: border-color var(--duration-fast);
  max-height: 120px;
  min-height: 42px;
}

.chat-input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-primary) 12%, transparent);
}

.chat-input:disabled {
  opacity: 0.6;
}

.send-btn {
  width: 42px;
  height: 42px;
  border: 0;
  border-radius: var(--radius-md);
  background: var(--color-primary);
  color: #fff;
  cursor: pointer;
  display: grid;
  place-items: center;
  transition: all var(--duration-fast);
  flex-shrink: 0;
}

.send-btn:hover:not(:disabled) {
  background: #4566E6;
  transform: translateY(-1px);
  box-shadow: var(--shadow-md);
}

.send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.input-hint {
  font-size: var(--font-size-caption);
  color: var(--color-text-assist);
  margin: 6px 0 0 2px;
}
</style>
