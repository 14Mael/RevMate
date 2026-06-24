<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { deleteSavedCourse, listSavedCourses, recommendCourses, saveCourse } from '@/api/courses'
import CourseCardItem from '@/components/CourseCard.vue'
import { PhSparkle } from '@/components/icons'
import type { CourseCard, SavedCourse } from '@/api/types'

defineOptions({ name: 'RecommendView' })

type Tab = 'search' | 'saved'

const activeTab = ref<Tab>('search')
const topic = ref('')
const loading = ref(false)
const savedLoading = ref(false)
const results = ref<CourseCard[]>([])
const savedCourses = ref<SavedCourse[]>([])
const busyUrl = ref('')

function parseKeywords(text: string): string[] {
  return text
    .split(/[\s,，、;；]+/)
    .map((item) => item.trim())
    .filter(Boolean)
}

async function handleRecommend() {
  const keywords = parseKeywords(topic.value)
  if (keywords.length === 0) {
    ElMessage.warning('请输入想学习的主题')
    return
  }

  loading.value = true
  try {
    results.value = await recommendCourses(keywords)
    if (results.value.length === 0) {
      ElMessage.info('暂时没有找到合适的课程')
    }
  } catch {
    ElMessage.error('搜索服务暂不可用')
  } finally {
    loading.value = false
  }
}

async function loadSaved() {
  savedLoading.value = true
  try {
    savedCourses.value = await listSavedCourses()
  } finally {
    savedLoading.value = false
  }
}

async function handleSave(course: CourseCard | SavedCourse) {
  busyUrl.value = course.url
  try {
    const saved = await saveCourse({ ...course, subjectId: null })
    savedCourses.value = [saved, ...savedCourses.value.filter((item) => item.url !== saved.url)]
    ElMessage.success('已收藏')
  } finally {
    busyUrl.value = ''
  }
}

async function handleDelete(course: CourseCard | SavedCourse) {
  if (!('id' in course)) return
  busyUrl.value = course.url
  try {
    await deleteSavedCourse(course.id)
    savedCourses.value = savedCourses.value.filter((item) => item.id !== course.id)
    ElMessage.success('已取消收藏')
  } finally {
    busyUrl.value = ''
  }
}

onMounted(loadSaved)
</script>

<template>
  <div class="recommend-page">
    <header class="page-header">
      <div>
        <h1>推荐课程</h1>
        <p>输入一个复习主题，联网搜索真实学习资源，再整理成可收藏的课程卡片。</p>
      </div>
      <PhSparkle :size="30" weight="duotone" class="header-icon" />
    </header>

    <div class="tabs">
      <button :class="{ active: activeTab === 'search' }" @click="activeTab = 'search'">找课程</button>
      <button :class="{ active: activeTab === 'saved' }" @click="activeTab = 'saved'">已收藏</button>
    </div>

    <section v-if="activeTab === 'search'" class="search-section">
      <div class="search-box">
        <input
          v-model="topic"
          class="topic-input"
          placeholder="例如：Java 并发、操作系统 进程调度、线性代数 特征值"
          @keyup.enter="handleRecommend"
        />
        <button class="primary-btn" :disabled="loading" @click="handleRecommend">
          {{ loading ? '搜索中...' : '推荐课程' }}
        </button>
      </div>

      <div v-if="!loading && results.length === 0" class="empty-state">
        输入主题后，这里会展示课程推荐。
      </div>

      <div v-else class="course-list">
        <CourseCardItem
          v-for="course in results"
          :key="course.url"
          :course="course"
          :busy="busyUrl === course.url"
          @save="handleSave"
        />
      </div>
    </section>

    <section v-else class="course-list">
      <div v-if="savedLoading" class="empty-state">正在加载收藏...</div>
      <div v-else-if="savedCourses.length === 0" class="empty-state">还没有收藏课程。</div>
      <CourseCardItem
        v-for="course in savedCourses"
        v-else
        :key="course.id"
        :course="course"
        saved
        :busy="busyUrl === course.url"
        @delete="handleDelete"
      />
    </section>
  </div>
</template>

<style scoped>
.recommend-page {
  max-width: 960px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-xl);
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-xl);
  padding: var(--space-xl) 0;
}

.page-header h1 {
  margin: 0 0 var(--space-xs) 0;
  color: var(--color-text-title);
  font-size: var(--font-size-h1);
}

.page-header p {
  margin: 0;
  color: var(--color-text-assist);
  line-height: 1.7;
}

.header-icon {
  color: var(--color-primary);
  flex-shrink: 0;
}

.tabs {
  display: inline-flex;
  align-self: flex-start;
  padding: 3px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-card-bg);
}

.tabs button {
  border: 0;
  padding: 8px 18px;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-body);
  cursor: pointer;
  font-size: var(--font-size-body);
}

.tabs button.active {
  background: var(--color-primary);
  color: #fff;
}

.search-section {
  display: flex;
  flex-direction: column;
  gap: var(--space-xl);
}

.search-box {
  display: flex;
  gap: var(--space-md);
}

.topic-input {
  flex: 1;
  min-width: 0;
  padding: 12px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-card-bg);
  color: var(--color-text-body);
  font-size: var(--font-size-body);
  outline: none;
}

.topic-input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-primary) 12%, transparent);
}

.primary-btn {
  padding: 0 20px;
  border: 0;
  border-radius: var(--radius-md);
  background: var(--color-primary);
  color: #fff;
  font-size: var(--font-size-body);
  font-weight: 600;
  cursor: pointer;
  box-shadow: var(--shadow-button-primary);
}

.primary-btn:hover:not(:disabled) {
  background: #4566E6;
}

.primary-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.course-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--space-lg);
}

.empty-state {
  padding: var(--space-3xl);
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-text-assist);
  text-align: center;
  background: var(--color-card-bg);
}

@media (max-width: 720px) {
  .search-box {
    flex-direction: column;
  }

  .primary-btn {
    min-height: 42px;
  }
}
</style>
