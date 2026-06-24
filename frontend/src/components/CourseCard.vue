<script setup lang="ts">
import type { CourseCard, SavedCourse } from '@/api/types'

const props = defineProps<{
  course: CourseCard | SavedCourse
  saved?: boolean
  busy?: boolean
}>()

const emit = defineEmits<{
  save: [course: CourseCard | SavedCourse]
  delete: [course: CourseCard | SavedCourse]
}>()

function openCourse() {
  window.open(props.course.url, '_blank', 'noopener,noreferrer')
}
</script>

<template>
  <article class="course-card">
    <div class="course-head">
      <div class="course-main">
        <div class="course-title">{{ course.title }}</div>
        <div class="course-meta">
          <span>{{ course.platform || '网页' }}</span>
          <span>{{ course.difficulty || '推荐' }}</span>
        </div>
      </div>
    </div>

    <p class="course-reason">{{ course.reason || '这条资源和当前主题相关，可以作为延伸学习材料。' }}</p>

    <div class="course-actions">
      <button class="link-btn" @click="openCourse">打开链接</button>
      <button v-if="saved" class="ghost-btn danger" :disabled="busy" @click="emit('delete', course)">
        取消收藏
      </button>
      <button v-else class="ghost-btn" :disabled="busy" @click="emit('save', course)">
        收藏
      </button>
    </div>
  </article>
</template>

<style scoped>
.course-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  padding: var(--space-lg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-card-bg);
}

.course-head {
  display: flex;
  justify-content: space-between;
  gap: var(--space-md);
}

.course-main {
  min-width: 0;
}

.course-title {
  color: var(--color-text-title);
  font-size: var(--font-size-h3);
  font-weight: 700;
  line-height: 1.4;
  word-break: break-word;
}

.course-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-xs);
  margin-top: var(--space-xs);
}

.course-meta span {
  padding: 2px 8px;
  border-radius: var(--radius-full);
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-size: var(--font-size-caption);
  font-weight: 600;
}

.course-reason {
  margin: 0;
  color: var(--color-text-body);
  font-size: var(--font-size-small);
  line-height: 1.7;
  word-break: break-word;
}

.course-actions {
  display: flex;
  gap: var(--space-sm);
  flex-wrap: wrap;
}

.link-btn,
.ghost-btn {
  padding: 7px 12px;
  border-radius: var(--radius-md);
  font-size: var(--font-size-small);
  cursor: pointer;
  transition: all var(--duration-fast);
}

.link-btn {
  border: 0;
  background: var(--color-primary);
  color: #fff;
}

.link-btn:hover {
  background: #4566E6;
}

.ghost-btn {
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text-body);
}

.ghost-btn:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.ghost-btn.danger:hover:not(:disabled) {
  border-color: var(--color-danger);
  color: var(--color-danger);
}

.ghost-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
