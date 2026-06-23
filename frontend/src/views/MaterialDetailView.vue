<script setup lang="ts">
/**
 * 资料详情预览页
 * - 主区：浏览器内置 PDF 查看器（iframe），支持文本选择、缩放、翻页
 * - 右侧边栏：针对当前资料的问答
 * - 顶部工具栏：返回、文件名、下载、侧边栏切换
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { PhArrowLeft, PhChat, PhDownloadSimple, PhFileText, PhWarning } from '@/components/icons'
import MaterialChatSidebar from '@/components/MaterialChatSidebar.vue'
import { getMaterial, getPreviewUrl, getAudioUrl, getTranscript, canPreview } from '@/api/material'
import type { Material } from '@/api/types'

defineOptions({ name: 'MaterialDetailView' })

const route = useRoute()
const router = useRouter()

const materialId = computed(() => Number(route.params.id))
const material = ref<Material | null>(null)
const loading = ref(true)
const loadError = ref('')
const previewUrl = ref('')
const audioUrl = ref('')
const transcript = ref('')
const sidebarOpen = ref(true)
const pdfIframe = ref<HTMLIFrameElement>()

const isAudio = computed(() => material.value?.type === 'audio')

// 从 URL hash 提取页码（格式：#page=3）
const targetPage = computed(() => {
  const match = route.hash.match(/page=(\d+)/)
  return match ? Number(match[1]) : null
})

const previewAvailable = computed(() => {
  if (!material.value || !canPreview(material.value)) return false
  if (isAudio.value) return !!audioUrl.value || !!transcript.value
  return !!previewUrl.value
})

// 下载链接：音频用原文件，其它用预览 PDF
const downloadUrl = computed(() => (isAudio.value ? audioUrl.value : previewUrl.value))

const selectedText = ref('')
const selectionTipVisible = ref(false)

function revokeBlobUrls() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
  if (audioUrl.value) {
    URL.revokeObjectURL(audioUrl.value)
    audioUrl.value = ''
  }
}

async function loadMaterial() {
  loading.value = true
  loadError.value = ''
  revokeBlobUrls()
  transcript.value = ''
  try {
    const m = await getMaterial(materialId.value)
    if (!m) {
      loadError.value = '资料不存在'
      return
    }
    material.value = m
    if (canPreview(m)) {
      if (m.type === 'audio') {
        const [url, text] = await Promise.all([getAudioUrl(m.id), getTranscript(m.id)])
        audioUrl.value = url
        transcript.value = text
      } else {
        previewUrl.value = await getPreviewUrl(m.id)
      }
    }
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/materials')
}

function toggleSidebar() {
  sidebarOpen.value = !sidebarOpen.value
}

// PDF 加载完成后跳转到指定页码
const hasNavigated = ref(false)

function onPdfLoad() {
  // 无页码时直接显示第一页，不需要特殊处理
  if (!targetPage.value || !pdfIframe.value || hasNavigated.value) return
  
  hasNavigated.value = true
  
  // 方案1：直接修改 iframe 内部 location.hash，不触发重新加载
  try {
    const iframeWin = pdfIframe.value.contentWindow
    if (iframeWin) {
      iframeWin.location.hash = `page=${targetPage.value}`
      return
    }
  } catch {
    // 跨域异常，继续尝试
  }
  
  // 方案2：通过 postMessage 尝试调用 PDF 查看器 API
  try {
    pdfIframe.value.contentWindow?.postMessage({
      type: 'scroll',
      pageNumber: targetPage.value
    }, '*')
  } catch {
    // 静默忽略
  }
  
  // 方案3：延迟后重试（处理 iframe 内部初始化延迟）
  setTimeout(() => {
    if (!pdfIframe.value) return
    try {
      const iframeWin = pdfIframe.value.contentWindow
      if (iframeWin && iframeWin.location) {
        iframeWin.location.hash = `page=${targetPage.value}`
      }
    } catch {
      // 静默忽略
    }
  }, 500)
}

// 文本选中提示
function handleMouseUp() {
  const selection = window.getSelection()
  const text = selection?.toString().trim() || ''
  if (text && text.length >= 2) {
    selectedText.value = text
    selectionTipVisible.value = true
  } else {
    window.setTimeout(() => {
      if (!window.getSelection()?.toString().trim()) {
        selectionTipVisible.value = false
        selectedText.value = ''
      }
    }, 200)
  }
}

watch(materialId, () => {
  hasNavigated.value = false
  loadMaterial()
})

// 监听 URL hash 变化（同一页面内跳转页码）
watch(() => route.hash, (newHash) => {
  const match = newHash.match(/page=(\d+)/)
  if (match && pdfIframe.value) {
    try {
      pdfIframe.value.contentWindow!.location.hash = `page=${match[1]}`
    } catch {
      // 静默忽略
    }
  }
})

onMounted(loadMaterial)
onUnmounted(revokeBlobUrls)
</script>

<template>
  <div class="detail-page" @mouseup="handleMouseUp">
    <!-- 顶部工具栏 -->
    <header class="toolbar">
      <button class="back-btn" @click="goBack">
        <PhArrowLeft :size="18" weight="bold" />
        <span>返回</span>
      </button>

      <div class="file-info" v-if="material">
        <PhFileText :size="20" class="file-icon" />
        <div class="file-meta">
          <div class="file-name" :title="material.filename">{{ material.filename }}</div>
          <div class="file-course" v-if="material.course">{{ material.course }}</div>
        </div>
      </div>
      <div v-else class="file-info placeholder">加载中...</div>

      <div class="toolbar-right">
        <button
          class="sidebar-toggle-btn"
          :class="{ active: sidebarOpen }"
          @click="toggleSidebar"
          title="问答侧边栏"
        >
          <PhChat :size="16" />
          <span>问答</span>
        </button>
        <a class="download-btn" :href="downloadUrl || undefined" :download="material?.filename || 'preview'">
          <PhDownloadSimple :size="16" />
          下载
        </a>
      </div>
    </header>

    <!-- 主区域：PDF + 侧边栏 -->
    <div class="detail-main">
      <!-- PDF 预览区 -->
      <main class="preview-area">
        <div v-if="loadError" class="error-state">
          <p>{{ loadError }}</p>
          <button class="back-btn" @click="goBack">返回资料列表</button>
        </div>

        <div v-else-if="loading" class="loading-state">
          <span>正在加载文档...</span>
        </div>

        <!-- 不可预览 -->
        <div v-else-if="!previewAvailable" class="no-preview-state">
          <PhWarning :size="40" weight="duotone" />
          <h3>暂不支持预览</h3>
          <p>当前资料还没有可用的 PDF 预览。</p>
          <p class="hint">{{ material?.previewMessage || '如果是 Office 文档，请稍后刷新；如果处理失败，请检查文件或 LibreOffice 环境。' }}</p>
          <button class="back-btn" @click="goBack">返回资料列表</button>
        </div>

        <!-- 音频预览：播放器 + 转写文字稿 -->
        <div v-else-if="isAudio" class="audio-preview">
          <div class="audio-player-bar">
            <PhFileText :size="18" class="audio-icon" />
            <audio v-if="audioUrl" class="audio-player" :src="audioUrl" controls preload="metadata" />
          </div>
          <div class="transcript-panel">
            <div class="transcript-title">转写文字稿</div>
            <p v-if="transcript" class="transcript-text">{{ transcript }}</p>
            <p v-else class="transcript-empty">暂无转写文字稿</p>
          </div>
        </div>

        <!-- 浏览器内置 PDF 查看器 -->
        <iframe
          v-else
          ref="pdfIframe"
          class="pdf-iframe"
          :src="previewUrl"
          frameborder="0"
          title="PDF Preview"
          @load="onPdfLoad"
        />
      </main>

      <!-- 侧边栏问答 -->
      <MaterialChatSidebar
        v-if="sidebarOpen && material && previewAvailable"
        :material-id="material.id"
        :subject-id="material.subjectId"
        :material-name="material.filename"
        @close="sidebarOpen = false"
      />
    </div>
  </div>
</template>

<style scoped>
.detail-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: calc(100vh - 64px);
  background: var(--color-page-bg);
  border-radius: var(--radius-lg);
  overflow: hidden;
  position: relative;
}

/* ---------- 工具栏 ---------- */
.toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-lg);
  padding: var(--space-md) var(--space-lg);
  background: var(--color-card-bg);
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  padding: 6px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-body);
  cursor: pointer;
  transition: all var(--duration-fast);
  font-size: var(--font-size-body);
}
.back-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.file-info {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: var(--space-md);
}
.file-info.placeholder {
  color: var(--color-text-assist);
}
.file-icon {
  color: var(--color-primary);
  flex-shrink: 0;
}
.file-meta {
  min-width: 0;
}
.file-name {
  color: var(--color-text-title);
  font-weight: 600;
  font-size: var(--font-size-h3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-course {
  color: var(--color-text-assist);
  font-size: var(--font-size-small);
  margin-top: 2px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.sidebar-toggle-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-body);
  cursor: pointer;
  transition: all var(--duration-fast);
  font-size: var(--font-size-body);
}
.sidebar-toggle-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.sidebar-toggle-btn.active {
  background: var(--color-primary-light);
  color: var(--color-primary);
  border-color: var(--color-primary);
}

.download-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  border: 0;
  border-radius: var(--radius-md);
  background: var(--color-primary);
  color: #fff;
  font-size: var(--font-size-body);
  cursor: pointer;
  transition: all var(--duration-fast);
  box-shadow: var(--shadow-button-primary);
}
.download-btn:hover:not(:disabled) {
  background: #4566E6;
}
.download-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* ---------- 主区域 ---------- */
.detail-main {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* ---------- 预览区 ---------- */
.preview-area {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  position: relative;
  background: #525659;
}

.pdf-iframe {
  width: 100%;
  height: 100%;
  border: 0;
}

/* ---------- 音频预览 ---------- */
.audio-preview {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--color-page-bg);
  overflow: hidden;
}

.audio-player-bar {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-lg);
  background: var(--color-card-bg);
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}
.audio-icon {
  color: var(--color-primary);
  flex-shrink: 0;
}
.audio-player {
  flex: 1;
  min-width: 0;
  height: 40px;
}

.transcript-panel {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--space-lg);
}
.transcript-title {
  color: var(--color-text-assist);
  font-size: var(--font-size-small);
  font-weight: 600;
  margin-bottom: var(--space-md);
}
.transcript-text {
  color: var(--color-text-body);
  font-size: var(--font-size-body);
  line-height: 1.9;
  white-space: pre-wrap;
  word-break: break-word;
}
.transcript-empty {
  color: var(--color-text-assist);
  font-size: var(--font-size-body);
}

.error-state,
.loading-state,
.no-preview-state {
  min-height: 400px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-lg);
  color: var(--color-text-assist);
  background: var(--color-card-bg);
}

.no-preview-state h3 {
  color: var(--color-text-title);
  font-size: var(--font-size-h2);
  margin-bottom: 0;
}

.no-preview-state p {
  max-width: 420px;
  text-align: center;
  font-size: var(--font-size-body);
  line-height: 1.7;
}

.no-preview-state .hint {
  color: var(--color-text-assist);
  font-size: var(--font-size-small);
}

.no-preview-state code {
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-size: var(--font-size-small);
}
</style>
