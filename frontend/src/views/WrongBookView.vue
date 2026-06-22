<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  deleteWrongQuestion,
  listWrongQuestions,
  markWrongQuestionMastered,
  reinforceWrongQuestion
} from '@/api/wrongQuestion'
import QuizRunner from '@/components/QuizRunner.vue'
import { PhBookOpen, PhCheckSquare, PhSparkle, PhTrash, PhWarning } from '@/components/icons'
import type { GradingResult, Question, WrongQuestion } from '@/api/types'

type Stage = 'list' | 'redo'
type TypeFilter = 'all' | 'single' | 'fill'

const stage = ref<Stage>('list')
const loading = ref(false)
const selectedCourse = ref('all')
const selectedType = ref<TypeFilter>('all')
const wrongQuestions = ref<WrongQuestion[]>([])
const redoQuestions = ref<Question[]>([])
const reinforceLoadingId = ref<number | null>(null)
const reinforceMap = ref<Record<number, Question[]>>({})
const reinforceAnswers = ref<Record<string, string>>({})
const reinforceChecked = ref<Record<string, boolean>>({})

const courses = computed(() => Array.from(new Set(wrongQuestions.value.map((item) => item.course))))

const filteredQuestions = computed(() => wrongQuestions.value.filter((item) => {
  const courseMatched = selectedCourse.value === 'all' || item.course === selectedCourse.value
  const typeMatched = selectedType.value === 'all' || item.type === selectedType.value
  return courseMatched && typeMatched
}))

const redoableQuestions = computed(() => filteredQuestions.value.filter((item) => !item.mastered))

async function loadWrongQuestions() {
  loading.value = true
  try {
    wrongQuestions.value = await listWrongQuestions()
  } catch {
    ElMessage.error('加载错题本失败')
  } finally {
    loading.value = false
  }
}

loadWrongQuestions()

function startRedo() {
  if (redoableQuestions.value.length === 0) {
    ElMessage.info('这门课没有待复习的错题')
    return
  }
  redoQuestions.value = redoableQuestions.value.map(toQuestion)
  stage.value = 'redo'
}

async function handleRedoCompleted(payload: { results: GradingResult[]; answers: string[] }) {
  const correctItems = payload.results
    .map((result, index) => ({ result, question: redoQuestions.value[index] }))
    .filter(({ result }) => result.correct)

  await Promise.all(correctItems.map(({ question }) =>
    question.wrongQuestionId ? markWrongQuestionMastered(question.wrongQuestionId) : Promise.resolve(null)
  ))
  if (correctItems.length > 0) {
    ElMessage.success(`已掌握 ${correctItems.length} 道错题`)
    await loadWrongQuestions()
  }
}

async function markMastered(item: WrongQuestion) {
  try {
    const updated = await markWrongQuestionMastered(item.id)
    replaceWrongQuestion(updated)
    ElMessage.success('已标记为掌握')
  } catch {
    ElMessage.error('标记失败')
  }
}

async function removeWrongQuestion(item: WrongQuestion) {
  try {
    await deleteWrongQuestion(item.id)
    wrongQuestions.value = wrongQuestions.value.filter((question) => question.id !== item.id)
    ElMessage.success('已删除')
  } catch {
    ElMessage.error('删除失败')
  }
}

async function reinforce(item: WrongQuestion) {
  reinforceLoadingId.value = item.id
  try {
    reinforceMap.value[item.id] = (await reinforceWrongQuestion(item.id)).map((question) => ({
      ...question,
      type: item.type
    }))
  } catch {
    ElMessage.error('举一反三生成失败，请稍后重试')
  } finally {
    reinforceLoadingId.value = null
  }
}

function checkReinforceAnswer(itemId: number, index: number, question: Question) {
  const key = reinforceKey(itemId, index)
  if (!reinforceAnswers.value[key]?.trim()) {
    ElMessage.warning('请先作答')
    return
  }
  reinforceChecked.value[key] = true
}

function reinforceCorrect(itemId: number, index: number, question: Question): boolean {
  const answer = reinforceAnswers.value[reinforceKey(itemId, index)] || ''
  return answer.trim().toLowerCase() === question.answer.trim().toLowerCase()
}

function reinforceKey(itemId: number, index: number) {
  return `${itemId}-${index}`
}

function replaceWrongQuestion(updated: WrongQuestion) {
  wrongQuestions.value = wrongQuestions.value.map((item) => item.id === updated.id ? updated : item)
}

function toQuestion(item: WrongQuestion): Question {
  return {
    stem: item.stem,
    type: item.type,
    options: item.options ?? undefined,
    answer: item.answer,
    analysis: item.analysis || '',
    wrongQuestionId: item.id,
    wrongCount: item.wrongCount,
    wrongBookAdded: true
  }
}
</script>

<template>
  <div class="wrong-book-page">
    <template v-if="stage === 'redo'">
      <QuizRunner
        :questions="redoQuestions"
        back-label="返回错题本"
        retry-label="重新重做"
        @back="stage = 'list'"
        @retry="startRedo"
        @completed="handleRedoCompleted"
      />
    </template>

    <template v-else>
      <header class="page-header">
        <div>
          <div class="eyebrow">
            <PhBookOpen :size="18" weight="duotone" />
            错题本
          </div>
          <h2>复习错题，补上薄弱点</h2>
          <p>自动收录答错的选择题和填空题，也可以从回顾页手动加入。</p>
        </div>
        <button class="primary-btn" :disabled="redoableQuestions.length === 0" @click="startRedo">
          <PhCheckSquare :size="16" weight="fill" />
          重做错题
        </button>
      </header>

      <div class="filters">
        <select v-model="selectedCourse" class="filter-select">
          <option value="all">全部课程</option>
          <option v-for="course in courses" :key="course" :value="course">{{ course }}</option>
        </select>
        <select v-model="selectedType" class="filter-select">
          <option value="all">全部题型</option>
          <option value="single">单选题</option>
          <option value="fill">填空题</option>
        </select>
        <span class="filter-count">{{ filteredQuestions.length }} 道错题，{{ redoableQuestions.length }} 道待复习</span>
      </div>

      <div v-if="loading" class="empty-state">正在加载错题本...</div>
      <div v-else-if="filteredQuestions.length === 0" class="empty-state">
        <PhWarning :size="18" weight="duotone" />
        当前筛选下暂无错题。
      </div>

      <div v-else class="wrong-list">
        <article v-for="item in filteredQuestions" :key="item.id" class="wrong-card">
          <div class="card-head">
            <div class="badges">
              <span class="badge">{{ item.course }}</span>
              <span class="badge">{{ item.type === 'single' ? '单选题' : '填空题' }}</span>
              <span class="badge danger">错 {{ item.wrongCount }} 次</span>
              <span v-if="item.mastered" class="badge success">已掌握</span>
            </div>
            <div class="card-actions">
              <button class="ghost-btn" :disabled="item.mastered" @click="markMastered(item)">已掌握</button>
              <button class="ghost-btn" @click="reinforce(item)">
                <PhSparkle :size="15" weight="duotone" />
                {{ reinforceLoadingId === item.id ? '生成中...' : '举一反三' }}
              </button>
              <button class="icon-btn danger" title="删除" @click="removeWrongQuestion(item)">
                <PhTrash :size="15" />
              </button>
            </div>
          </div>

          <div class="stem">{{ item.stem }}</div>
          <ol v-if="item.options && item.options.length > 0" class="options">
            <li v-for="option in item.options" :key="option">{{ option }}</li>
          </ol>

          <div class="answer-grid">
            <div>
              <span>你的错误答案</span>
              <strong class="wrong">{{ item.wrongAnswer || '手动加入' }}</strong>
            </div>
            <div>
              <span>正确答案</span>
              <strong class="right">{{ item.answer }}</strong>
            </div>
          </div>

          <div v-if="item.analysis" class="analysis">
            <strong>解析：</strong>{{ item.analysis }}
          </div>

          <div v-if="reinforceMap[item.id]?.length" class="reinforce-list">
            <div v-for="(question, index) in reinforceMap[item.id]" :key="`${question.stem}-${index}`" class="reinforce-item">
              <div class="reinforce-stem">{{ index + 1 }}. {{ question.stem }}</div>
              <ol v-if="question.options?.length" class="options">
                <li v-for="option in question.options" :key="option">{{ option }}</li>
              </ol>
              <div class="reinforce-answer">
                <input v-model="reinforceAnswers[reinforceKey(item.id, index)]" placeholder="输入你的答案" />
                <button class="ghost-btn" @click="checkReinforceAnswer(item.id, index, question)">检查</button>
              </div>
              <div v-if="reinforceChecked[reinforceKey(item.id, index)]" class="reinforce-result">
                <span :class="reinforceCorrect(item.id, index, question) ? 'right' : 'wrong'">
                  {{ reinforceCorrect(item.id, index, question) ? '回答正确' : `正确答案：${question.answer}` }}
                </span>
                <span v-if="question.analysis">，{{ question.analysis }}</span>
              </div>
            </div>
          </div>
        </article>
      </div>
    </template>
  </div>
</template>

<style scoped>
.wrong-book-page {
  max-width: 980px;
  margin: 0 auto;
  padding: var(--space-3xl) var(--space-xl);
}

.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-xl);
  margin-bottom: var(--space-xl);
}
.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  color: var(--color-primary);
  font-size: var(--font-size-small);
  font-weight: 700;
  margin-bottom: var(--space-sm);
}
.page-header h2 {
  margin: 0;
  color: var(--color-text-title);
  font-size: var(--font-size-h1);
}
.page-header p {
  margin: var(--space-sm) 0 0;
  color: var(--color-text-assist);
}

.filters {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  margin-bottom: var(--space-xl);
}
.filter-select {
  min-width: 160px;
  padding: 9px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-card-bg);
  color: var(--color-text-body);
}
.filter-count {
  color: var(--color-text-assist);
  font-size: var(--font-size-small);
}

.wrong-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}
.wrong-card,
.empty-state {
  background: var(--color-card-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-xl);
}
.empty-state {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  color: var(--color-text-assist);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  margin-bottom: var(--space-md);
}
.badges,
.card-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-xs);
}
.badge {
  padding: 3px 9px;
  border-radius: var(--radius-full);
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-size: var(--font-size-caption);
  font-weight: 600;
}
.badge.danger {
  color: var(--color-danger);
  background: color-mix(in srgb, var(--color-danger) 10%, transparent);
}
.badge.success {
  color: var(--color-success);
  background: color-mix(in srgb, var(--color-success) 10%, transparent);
}

.stem {
  color: var(--color-text-title);
  font-weight: 700;
  line-height: var(--line-height-body);
  margin-bottom: var(--space-md);
}
.options {
  margin: 0 0 var(--space-md) 20px;
  padding: 0;
  color: var(--color-text-body);
  line-height: var(--line-height-body);
}

.answer-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-md);
  margin: var(--space-md) 0;
}
.answer-grid div {
  padding: var(--space-md);
  border-radius: var(--radius-md);
  background: var(--color-page-bg);
}
.answer-grid span {
  display: block;
  color: var(--color-text-assist);
  font-size: var(--font-size-caption);
  margin-bottom: 4px;
}
.wrong { color: var(--color-danger); }
.right { color: var(--color-success); }

.analysis,
.reinforce-item {
  padding: var(--space-md);
  border-radius: var(--radius-md);
  background: var(--color-page-bg);
  color: var(--color-text-body);
  line-height: var(--line-height-body);
}

.reinforce-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  margin-top: var(--space-md);
}
.reinforce-stem {
  font-weight: 700;
  margin-bottom: var(--space-sm);
}
.reinforce-answer {
  display: flex;
  gap: var(--space-sm);
}
.reinforce-answer input {
  flex: 1;
  padding: 8px 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-card-bg);
}
.reinforce-result {
  margin-top: var(--space-sm);
  font-size: var(--font-size-small);
}

.primary-btn,
.ghost-btn,
.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-xs);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--duration-fast);
}
.primary-btn {
  padding: 10px 18px;
  border: 1px solid var(--color-primary);
  background: var(--color-primary);
  color: #fff;
  font-weight: 700;
}
.primary-btn:disabled,
.ghost-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.ghost-btn {
  padding: 7px 11px;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text-body);
}
.ghost-btn:hover:not(:disabled) {
  color: var(--color-primary);
  border-color: var(--color-primary);
}
.icon-btn {
  width: 32px;
  height: 32px;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text-assist);
}
.icon-btn.danger:hover {
  color: var(--color-danger);
  border-color: var(--color-danger);
}

@media (max-width: 720px) {
  .page-header,
  .card-head,
  .filters {
    align-items: stretch;
    flex-direction: column;
  }
  .answer-grid {
    grid-template-columns: 1fr;
  }
}
</style>
