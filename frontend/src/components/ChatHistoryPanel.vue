<script setup lang="ts">
/**
 * 聊天历史侧边面板
 * - 左侧滑出抽屉，宽 320px
 * - 列表项：标题、日期、课程标签
 * - 点击加载会话、删除确认、清空全部
 */
import { computed } from 'vue'
import { ElMessageBox } from 'element-plus'
import { PhTrash, PhX, PhWarning } from '@/components/icons'
import { deleteHistory, clearAllHistory, getHistoryList } from '@/api/chat'
import type { ChatHistoryItem } from '@/api/types'

const props = defineProps<{
  visible: boolean
  activeId?: string
  histories: ChatHistoryItem[]
  courses: Record<number, string>
}>()

const emit = defineEmits<{
  close: []
  load: [item: ChatHistoryItem]
  updated: []
}>()

function formatDate(dateStr: string): string {
  const d = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  const days = Math.floor(diff / 86400000)

  if (days === 0) {
    return `今天 ${d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}`
  } else if (days === 1) {
    return `昨天 ${d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}`
  } else if (days < 7) {
    return `${days} 天前`
  }
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

async function handleDelete(id: string, e: Event) {
  e.stopPropagation()
  try {
    await ElMessageBox.confirm('确定删除这条历史记录？', '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    deleteHistory(id)
    emit('updated')
  } catch {
    // 取消
  }
}

async function handleClearAll() {
  if (props.histories.length === 0) return
  try {
    await ElMessageBox.confirm('确定清空全部历史记录？此操作不可撤销。', '清空全部', {
      confirmButtonText: '清空',
      cancelButtonText: '取消',
      type: 'warning'
    })
    clearAllHistory()
    emit('updated')
  } catch {
    // 取消
  }
}

function handleSelect(item: ChatHistoryItem) {
  emit('load', item)
  emit('close')
}
</script>

<template>
  <Teleport to="body">
    <!-- 遮罩层 -->
    <div v-if="visible" class="overlay" @click="emit('close')" />

    <!-- 抽屉面板 -->
    <aside v-if="visible" class="history-panel">
      <!-- 标题栏 -->
      <header class="panel-header">
        <h3 class="panel-title">历史记录</h3>
        <button class="close-btn" @click="emit('close')">
          <PhX :size="16" />
        </button>
      </header>

      <!-- 列表 -->
      <main class="panel-body">
        <div v-if="histories.length === 0" class="empty-state">
          <p>暂无历史记录</p>
          <p class="empty-hint">发送问答后会自动保存</p>
        </div>

        <div
          v-for="item in histories"
          :key="item.id"
          class="history-item"
          :class="{ active: item.id === activeId }"
          @click="handleSelect(item)"
        >
          <div class="item-main">
            <div class="item-title">{{ item.title }}</div>
            <div class="item-meta">
              <span class="item-date">{{ formatDate(item.createdAt) }}</span>
              <span v-if="item.course || courses[item.subjectId]" class="item-course">
                {{ item.course || courses[item.subjectId] || '' }}
              </span>
            </div>
          </div>
          <button class="item-delete" @click="handleDelete(item.id, $event)" title="删除">
            <PhTrash :size="14" />
          </button>
        </div>
      </main>

      <!-- 底部清空 -->
      <footer class="panel-footer">
        <button class="clear-btn" :disabled="histories.length === 0" @click="handleClearAll">
          清空全部
        </button>
      </footer>
    </aside>
  </Teleport>
</template>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.25);
  z-index: 1000;
  animation: fadeIn var(--duration-fast) ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.history-panel {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  width: 320px;
  background: var(--color-card-bg);
  z-index: 1001;
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-lg);
  animation: slideInLeft var(--duration-base) var(--ease-default);
}

@keyframes slideInLeft {
  from { transform: translateX(-100%); }
  to { transform: translateX(0); }
}

/* ==================== Header ==================== */
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-lg) var(--space-xl);
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}

.panel-title {
  font-size: var(--font-size-h3);
  font-weight: 700;
  color: var(--color-text-title);
  margin: 0;
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
.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-sm);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-3xl) var(--space-xl);
  text-align: center;
  color: var(--color-text-assist);
  font-size: var(--font-size-body);
}

.empty-hint {
  font-size: var(--font-size-small);
  margin-top: var(--space-xs);
  color: var(--color-text-placeholder);
}

/* ==================== History Item ==================== */
.history-item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-md);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--duration-fast);
  margin-bottom: 2px;
}

.history-item:hover {
  background: var(--color-page-bg);
}

.history-item.active {
  background: var(--color-primary-light);
}

.item-main {
  flex: 1;
  min-width: 0;
}

.item-title {
  font-size: var(--font-size-body);
  font-weight: 500;
  color: var(--color-text-title);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 4px;
}

.history-item.active .item-title {
  color: var(--color-primary);
}

.item-meta {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  font-size: var(--font-size-caption);
  color: var(--color-text-assist);
}

.item-course {
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  background: var(--color-page-bg);
  color: var(--color-text-assist);
  font-size: var(--font-size-caption);
}

.history-item.active .item-course {
  background: color-mix(in srgb, var(--color-primary) 10%, transparent);
  color: var(--color-primary);
}

.item-delete {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-assist);
  cursor: pointer;
  transition: all var(--duration-fast);
  opacity: 0;
  flex-shrink: 0;
}

.history-item:hover .item-delete {
  opacity: 1;
}

.item-delete:hover {
  background: #FFF2F0;
  color: var(--color-danger);
}

/* ==================== Footer ==================== */
.panel-footer {
  padding: var(--space-md) var(--space-xl);
  border-top: 1px solid var(--color-border);
  flex-shrink: 0;
}

.clear-btn {
  width: 100%;
  padding: 6px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-assist);
  font-size: var(--font-size-small);
  cursor: pointer;
  transition: all var(--duration-fast);
}
.clear-btn:hover:not(:disabled) {
  border-color: var(--color-danger);
  color: var(--color-danger);
}
.clear-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
