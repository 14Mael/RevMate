<script setup lang="ts">
/**
 * 资料预览页侧边栏问答
 * - 紧凑版消息气泡 + 输入框
 * - 复用 HomeView 的 Markdown 渲染和消息样式
 * - 不展示欢迎态、快捷提问、课程筛选、引用预览条
 */
import { nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { chatStreamWithFallback } from '@/api/chat'
import { renderMarkdown } from '@/utils/markdown'
import { PhPaperPlaneTilt, PhSparkle, PhX, PhBookOpen } from '@/components/icons'
import type { Source, Subject } from '@/api/types'

const props = defineProps<{ 
  materialId: number
  subjectId: number
  materialName: string
}>()

const emit = defineEmits<{
  close: []
}>()

interface ChatMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  sources: Source[]
  isStreaming: boolean
}

const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const isGenerating = ref(false)
const chatContainer = ref<HTMLDivElement>()

let nextId = 1

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || isGenerating.value) return

  messages.value.push({
    id: nextId++,
    role: 'user',
    content: text,
    sources: [],
    isStreaming: false
  })
  inputText.value = ''
  scrollToBottom()

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
      subjectId: props.subjectId,
      materialId: props.materialId,
      question: text
    })
    for await (const chunk of gen) {
      streamingMsg.content += chunk.text
      if (chunk.done) {
        streamingMsg.sources = chunk.sources
        streamingMsg.isStreaming = false
        isGenerating.value = false
      }
      scrollToBottom()
    }
  } catch {
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

function scrollToBottom() {
  nextTick(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight
    }
  })
}

const sourceIcon: Record<string, typeof PhBookOpen> = {
  material: PhBookOpen,
  web: PhBookOpen
}
</script>

<template>
  <aside class="chat-sidebar">
    <!-- 标题栏 -->
    <header class="sidebar-header">
      <div class="sidebar-title">
        <PhSparkle :size="18" weight="duotone" class="title-icon" />
        <span>针对此资料提问</span>
      </div>
      <button class="close-btn" @click="emit('close')">
        <PhX :size="16" />
      </button>
    </header>

    <!-- 消息列表 -->
    <main class="sidebar-body" ref="chatContainer">
      <div v-if="messages.length === 0" class="empty-hint">
        <p>基于 <strong>{{ materialName }}</strong> 提问，AI 会结合资料内容为你解答。</p>
      </div>

      <div v-for="msg in messages" :key="msg.id" class="message" :class="`msg-${msg.role}`">
        <!-- 用户消息 -->
        <template v-if="msg.role === 'user'">
          <div class="msg-bubble user-bubble">
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
                </div>
              </article>
            </div>
          </div>
        </template>
      </div>

      <div class="chat-bottom-spacer" />
    </main>

    <!-- 输入区域 -->
    <footer class="sidebar-input-area">
      <div class="input-row">
        <textarea
          v-model="inputText"
          class="chat-input"
          placeholder="输入问题..."
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
          <PhPaperPlaneTilt :size="16" weight="fill" />
        </button>
      </div>
      <p class="input-hint">Enter 发送</p>
    </footer>
  </aside>
</template>

<style scoped>
.chat-sidebar {
  display: flex;
  flex-direction: column;
  width: 380px;
  flex-shrink: 0;
  height: 100%;
  background: var(--color-card-bg);
  border-left: 1px solid var(--color-border);
}

/* ==================== Header ==================== */
.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-md) var(--space-lg);
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}

.sidebar-title {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-title);
}

.title-icon {
  color: var(--color-primary);
  flex-shrink: 0;
}

.close-btn {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-assist);
  cursor: pointer;
  transition: all var(--duration-fast);
}
.close-btn:hover {
  background: var(--color-page-bg);
  color: var(--color-text-title);
}

/* ==================== Body ==================== */
.sidebar-body {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-md);
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.chat-bottom-spacer {
  height: var(--space-xs);
}

.empty-hint {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-3xl);
  text-align: center;
  color: var(--color-text-assist);
  font-size: var(--font-size-small);
  line-height: var(--line-height-body);
}

.empty-hint strong {
  color: var(--color-text-body);
}

/* ==================== Messages ==================== */
.message {
  display: flex;
  flex-direction: column;
}

.msg-user {
  align-items: flex-end;
}

.msg-assistant {
  align-items: flex-start;
}

.msg-bubble {
  padding: var(--space-sm) var(--space-md);
  border-radius: var(--radius-md);
  font-size: var(--font-size-small);
  line-height: var(--line-height-dense);
  overflow-wrap: break-word;
}

.user-bubble {
  background: var(--color-primary);
  color: #fff;
  max-width: 80%;
  width: fit-content;
  border-bottom-right-radius: var(--radius-sm);
}

.ai-bubble {
  background: var(--color-page-bg);
  color: var(--color-text-body);
  border: 1px solid var(--color-border);
  max-width: 100%;
  border-bottom-left-radius: var(--radius-sm);
}

.msg-text {
  white-space: pre-wrap;
}

/* Typing indicator */
.typing-indicator {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 0;
}

.typing-indicator .dot {
  width: 5px;
  height: 5px;
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
  margin: 0 0 var(--space-xs);
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
  margin: var(--space-sm) 0 var(--space-xs);
  font-size: 1.05em;
}
.markdown-body :deep(*:first-child) {
  margin-top: 0;
}

.markdown-body :deep(a) {
  color: var(--color-primary);
  text-decoration: underline;
}

.markdown-body :deep(code) {
  padding: 1px 4px;
  border-radius: var(--radius-sm);
  background: var(--color-card-bg);
  color: var(--color-primary);
  font-size: 0.92em;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
}

.markdown-body :deep(pre) {
  margin: var(--space-sm) 0;
  padding: var(--space-sm);
  border-radius: var(--radius-sm);
  background: var(--color-card-bg);
  border: 1px solid var(--color-border);
  overflow-x: auto;
  font-size: var(--font-size-caption);
  line-height: 1.5;
}

.markdown-body :deep(pre code) {
  background: transparent;
  padding: 0;
  color: var(--color-text-body);
}

.markdown-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: var(--space-sm) 0;
  font-size: var(--font-size-caption);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.markdown-body :deep(th) {
  padding: var(--space-xs) var(--space-sm);
  text-align: left;
  font-weight: 700;
  color: var(--color-text-title);
  background: var(--color-card-bg);
  border-bottom: 1px solid var(--color-border);
  border-right: 1px solid var(--color-border);
}

.markdown-body :deep(td) {
  padding: var(--space-xs) var(--space-sm);
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
  margin: var(--space-xs) 0;
  padding-left: var(--space-lg);
}

.markdown-body :deep(li) {
  margin-bottom: 2px;
}

/* ==================== Sources ==================== */
.sources-section {
  margin-top: var(--space-sm);
}

.sources-label {
  font-size: var(--font-size-caption);
  font-weight: 600;
  color: var(--color-text-assist);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: var(--space-xs);
}

.sources-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}

.source-card {
  display: flex;
  gap: var(--space-xs);
  padding: var(--space-sm);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-page-bg);
  transition: all var(--duration-fast);
}

.source-card:hover {
  border-color: var(--color-primary);
}

.source-type-icon {
  color: var(--color-primary);
  flex-shrink: 0;
  margin-top: 1px;
}

.source-body {
  min-width: 0;
}

.source-title {
  font-size: var(--font-size-caption);
  font-weight: 600;
  color: var(--color-text-title);
  margin-bottom: 1px;
}

.source-snippet {
  font-size: var(--font-size-caption);
  color: var(--color-text-assist);
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

/* ==================== Input ==================== */
.sidebar-input-area {
  flex-shrink: 0;
  padding: var(--space-sm) var(--space-md) var(--space-md);
  border-top: 1px solid var(--color-border);
}

.input-row {
  display: flex;
  align-items: flex-end;
  gap: var(--space-xs);
}

.chat-input {
  flex: 1;
  resize: none;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 8px 10px;
  font-size: var(--font-size-small);
  font-family: inherit;
  line-height: var(--line-height-body);
  background: var(--color-page-bg);
  color: var(--color-text-body);
  outline: none;
  transition: border-color var(--duration-fast);
  max-height: 100px;
  min-height: 34px;
}

.chat-input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-primary) 12%, transparent);
}

.chat-input:disabled {
  opacity: 0.6;
}

.send-btn {
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: var(--radius-sm);
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
}

.send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.input-hint {
  font-size: var(--font-size-caption);
  color: var(--color-text-assist);
  margin: 4px 0 0 2px;
}
</style>
