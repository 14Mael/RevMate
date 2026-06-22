<script setup lang="ts">
/**
 * AI 出题页
 * 三阶段：配置阶段 → 答题阶段 → 回顾阶段
 * 设计依据：frontend-design-spec.md §7.6
 */
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { generateQuiz } from '@/api/quiz'
import { listMaterials } from '@/api/material'
import { listSubjects } from '@/api/subject'
import { saveWrongQuestion, saveWrongQuestionsBatch } from '@/api/wrongQuestion'
import { PhArrowLeft, PhCheckSquare, PhSparkle, PhWarning } from '@/components/icons'
import type { Material, Question, QuizType, Subject, WrongQuestionSaveRequest } from '@/api/types'

type Stage = 'config' | 'quiz' | 'review'
type CollectableType = Exclude<QuizType, 'qa'>

/* ==================== 状态 ==================== */

const stage = ref<Stage>('config')
const loading = ref(false)
const errorMsg = ref('')

// 配置
const subjects = ref<Subject[]>([])
const materials = ref<Material[]>([])
const selectedSubjectId = ref<number | ''>('')
const selectedMaterialId = ref<number | ''>('')
const selectedTypes = ref<QuizType[]>(['single', 'fill', 'qa'])
const questionCount = ref(5)

// 答题
const questions = ref<Question[]>([])
const userAnswers = ref<string[]>([])
const currentIndex = ref(0)

// 批改结果
const gradingResults = ref<{ correct: boolean; userAnswer: string }[]>([])

/* ==================== 计算 ==================== */

const currentQuestion = computed(() => questions.value[currentIndex.value] ?? null)
const isLastQuestion = computed(() => currentIndex.value >= questions.value.length - 1)
const isFirstQuestion = computed(() => currentIndex.value === 0)
const allAnswered = computed(() => userAnswers.value.every((a) => a.trim() !== ''))

const correctCount = computed(() => gradingResults.value.filter((r) => r.correct).length)
const totalCount = computed(() => questions.value.length)

const availableMaterials = computed(() => {
  if (!selectedSubjectId.value) return materials.value
  return materials.value.filter((m) => m.subjectId === selectedSubjectId.value)
})

const selectedCourse = computed(() => {
  const subject = subjects.value.find((s) => s.id === selectedSubjectId.value)
  return subject?.name || ''
})

/* ==================== 数据加载 ==================== */

async function loadData() {
  try {
    const [subjList, matList] = await Promise.all([listSubjects(), listMaterials()])
    subjects.value = subjList
    materials.value = matList
    if (subjList.length > 0) selectedSubjectId.value = subjList[0].id
  } catch {
    // mock 阶段静默失败
  }
}

loadData()

/* ==================== 题型配置 ==================== */

const typeOptions: { key: QuizType; label: string; desc: string }[] = [
  { key: 'single', label: '单选题', desc: '四选一' },
  { key: 'fill', label: '填空题', desc: '填写答案' },
  { key: 'qa', label: '简答题', desc: '文字作答' }
]

function toggleType(type: QuizType) {
  const idx = selectedTypes.value.indexOf(type)
  if (idx >= 0) {
    if (selectedTypes.value.length > 1) {
      selectedTypes.value.splice(idx, 1)
    } else {
      ElMessage.warning('至少选择一种题型')
    }
  } else {
    selectedTypes.value.push(type)
  }
}

/* ==================== 生成题目 ==================== */

async function handleGenerate() {
  if (!selectedSubjectId.value) {
    ElMessage.warning('请选择课程')
    return
  }
  if (selectedTypes.value.length === 0) {
    ElMessage.warning('请至少选择一种题型')
    return
  }

  loading.value = true
  errorMsg.value = ''

  try {
    // 对每种题型并行请求
    const perTypeCount = Math.max(1, Math.floor(questionCount.value / selectedTypes.value.length))
    const remainder = questionCount.value - perTypeCount * selectedTypes.value.length

    const payloads = selectedTypes.value.map((type, i) => ({
      subjectId: selectedSubjectId.value as number,
      materialId: selectedMaterialId.value || undefined,
      type,
      count: i === 0 ? perTypeCount + remainder : perTypeCount
    }))

    const results = await Promise.all(payloads.map((p) => generateQuiz(p)))
    const allQuestions = results.flatMap((r, i) =>
      r.questions.map((q) => ({ ...q, type: payloads[i].type }))
    )

    if (allQuestions.length === 0) {
      errorMsg.value = 'AI 未能生成题目，请调整配置后重试。'
      return
    }

    // 打乱题目顺序
    questions.value = allQuestions.sort(() => Math.random() - 0.5)
    userAnswers.value = new Array(questions.value.length).fill('')
    gradingResults.value = []
    currentIndex.value = 0
    stage.value = 'quiz'

    nextTick(() => scrollToQuestion())
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : '生成题目失败'
    ElMessage.error('生成题目失败')
  } finally {
    loading.value = false
  }
}

/* ==================== 答题 ==================== */

function selectOption(optionIndex: number) {
  if (!currentQuestion.value || stage.value !== 'quiz') return
  const label = String.fromCharCode(65 + optionIndex) // A, B, C, D
  userAnswers.value[currentIndex.value] = label
}

function nextQuestion() {
  if (!isLastQuestion.value) {
    currentIndex.value++
    nextTick(() => scrollToQuestion())
  }
}

function prevQuestion() {
  if (!isFirstQuestion.value) {
    currentIndex.value--
    nextTick(() => scrollToQuestion())
  }
}

function scrollToQuestion() {
  const el = document.querySelector('.quiz-card')
  el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

/* ==================== 批改 ==================== */

function handleSubmit() {
  if (!allAnswered.value) {
    ElMessage.warning('请完成所有题目后再提交')
    return
  }

  gradingResults.value = questions.value.map((q, i) => {
    const userAnswer = userAnswers.value[i].trim()
    const correctAnswer = q.answer.trim()

    let correct = false
    if (currentQuestion.value && q.options && q.options.length > 0) {
      // 选择题：比较选项字母
      correct = userAnswer.toUpperCase() === correctAnswer.toUpperCase()
    } else {
      // 填空/简答：模糊比对（去空格、大小写不敏感）
      correct = userAnswer.toLowerCase() === correctAnswer.toLowerCase()
    }

    return { correct, userAnswer }
  })

  stage.value = 'review'
  collectWrongQuestions()
  nextTick(() => scrollToQuestion())
}

async function collectWrongQuestions() {
  const requests = gradingResults.value
    .map((result, index) => ({ result, question: questions.value[index] }))
    .filter(({ result, question }) => !result.correct && collectableType(question))
    .map(({ result, question }) => buildWrongQuestionRequest(question, result.userAnswer, false))

  if (requests.length === 0) return

  try {
    const saved = await saveWrongQuestionsBatch(requests)
    questions.value = questions.value.map((question) => {
      const item = saved.find((wrongQuestion) => wrongQuestion.stem === question.stem)
      return item
        ? { ...question, wrongBookAdded: true, wrongCount: item.wrongCount, wrongQuestionId: item.id }
        : question
    })
  } catch (e) {
    console.warn('自动收录错题失败', e)
  }
}

async function handleManualAdd(question: Question, index: number) {
  const type = collectableType(question)
  if (!type) {
    ElMessage.warning('简答题暂不加入错题本')
    return
  }

  try {
    const saved = await saveWrongQuestion(buildWrongQuestionRequest(question, null, true))
    questions.value[index] = {
      ...questions.value[index],
      wrongBookAdded: true,
      wrongCount: saved.wrongCount,
      wrongQuestionId: saved.id
    }
    ElMessage.success('已加入错题本')
  } catch {
    ElMessage.error('加入错题本失败')
  }
}

function buildWrongQuestionRequest(question: Question, wrongAnswer: string | null, manual: boolean): WrongQuestionSaveRequest {
  const type = collectableType(question)
  if (!selectedSubjectId.value || !type) {
    throw new Error('题目无法加入错题本')
  }
  return {
    subjectId: selectedSubjectId.value,
    course: selectedCourse.value,
    type,
    stem: question.stem,
    options: question.options ?? null,
    answer: question.answer,
    analysis: question.analysis,
    wrongAnswer,
    manual
  }
}

function collectableType(question: Question): CollectableType | null {
  if (question.type === 'single' || question.type === 'fill') return question.type
  if (question.type === 'qa') return null
  return question.options && question.options.length > 0 ? 'single' : 'fill'
}

/* ==================== 导航 ==================== */

function backToConfig() {
  stage.value = 'config'
  questions.value = []
  userAnswers.value = []
  gradingResults.value = []
  errorMsg.value = ''
}

function retryQuiz() {
  handleGenerate()
}

/* ==================== 选项标签 ==================== */

function optionLabel(index: number): string {
  return String.fromCharCode(65 + index)
}

/* ==================== 题目类型标签 ==================== */

function questionTypeLabel(q: Question): string {
  if (q.options && q.options.length > 0) return '单选题'
  if (q.stem.includes('____') || q.stem.includes('___') || q.stem.includes('（）')) return '填空题'
  return '简答题'
}
</script>

<template>
  <div class="quiz-page">
    <!-- ========== 阶段一：配置 ========== -->
    <template v-if="stage === 'config'">
      <div class="config-panel">
        <div class="panel-header">
          <PhSparkle :size="28" weight="duotone" class="panel-icon" />
          <h2 class="panel-title">AI 出题</h2>
          <p class="panel-desc">选择题型和数量，AI 为你生成复习题目</p>
        </div>

        <!-- 课程选择 -->
        <div class="config-section">
          <label class="config-label">选择课程</label>
          <select v-model.number="selectedSubjectId" class="config-select">
            <option disabled value="">请选择课程</option>
            <option v-for="s in subjects" :key="s.id" :value="s.id">{{ s.name }}</option>
          </select>
        </div>

        <!-- 资料选择（可选） -->
        <div class="config-section">
          <label class="config-label">限定资料（可选）</label>
          <select v-model.number="selectedMaterialId" class="config-select">
            <option :value="''">全部资料</option>
            <option v-for="m in availableMaterials" :key="m.id" :value="m.id">{{ m.filename }}</option>
          </select>
        </div>

        <!-- 题型选择 -->
        <div class="config-section">
          <label class="config-label">题型</label>
          <div class="type-options">
            <button
              v-for="opt in typeOptions"
              :key="opt.key"
              class="type-chip"
              :class="{ active: selectedTypes.includes(opt.key) }"
              @click="toggleType(opt.key)"
            >
              <span class="type-chip-label">{{ opt.label }}</span>
              <span class="type-chip-desc">{{ opt.desc }}</span>
            </button>
          </div>
        </div>

        <!-- 题目数量 -->
        <div class="config-section">
          <label class="config-label">题目数量：{{ questionCount }}</label>
          <input
            v-model.number="questionCount"
            type="range"
            min="1"
            max="20"
            class="count-slider"
          />
          <div class="count-hints">
            <span>1</span>
            <span>10</span>
            <span>20</span>
          </div>
        </div>

        <!-- 错误提示 -->
        <div v-if="errorMsg" class="error-msg">
          <PhWarning :size="16" />
          {{ errorMsg }}
        </div>

        <!-- 生成按钮 -->
        <button class="generate-btn" :disabled="loading" @click="handleGenerate">
          <PhSparkle v-if="!loading" :size="18" weight="duotone" />
          <span v-if="loading" class="btn-spinner" />
          {{ loading ? '正在生成...' : '开始出题' }}
        </button>
      </div>
    </template>

    <!-- ========== 阶段二：答题 ========== -->
    <template v-if="stage === 'quiz'">
      <div class="quiz-stage">
        <!-- 进度条 -->
        <div class="quiz-progress">
          <button class="back-link" @click="backToConfig">
            <PhArrowLeft :size="16" />
            返回配置
          </button>
          <div class="progress-bar">
            <div class="progress-fill" :style="{ width: `${((currentIndex + 1) / questions.length) * 100}%` }" />
          </div>
          <span class="progress-text">{{ currentIndex + 1 }} / {{ questions.length }}</span>
        </div>

        <!-- 题目卡片 -->
        <div class="quiz-card" v-if="currentQuestion">
          <div class="question-type-badge">{{ questionTypeLabel(currentQuestion) }}</div>

          <div class="question-stem">
            <span class="question-number">{{ currentIndex + 1 }}.</span>
            {{ currentQuestion.stem }}
          </div>

          <!-- 选择题选项 -->
          <div v-if="currentQuestion.options && currentQuestion.options.length > 0" class="options-list">
            <button
              v-for="(opt, oi) in currentQuestion.options"
              :key="oi"
              class="option-btn"
              :class="{ selected: userAnswers[currentIndex] === optionLabel(oi) }"
              @click="selectOption(oi)"
            >
              <span class="option-letter">{{ optionLabel(oi) }}</span>
              <span class="option-text">{{ opt }}</span>
            </button>
          </div>

          <!-- 填空/简答 -->
          <div v-else class="answer-input-area">
            <textarea
              v-model="userAnswers[currentIndex]"
              class="answer-input"
              :placeholder="questionTypeLabel(currentQuestion) === '填空题' ? '请输入答案...' : '请输入你的回答...'"
              rows="3"
            />
          </div>

          <!-- 导航按钮 -->
          <div class="quiz-nav">
            <button class="nav-btn" :disabled="isFirstQuestion" @click="prevQuestion">上一题</button>
            <span class="nav-hint" v-if="!allAnswered">还有 {{ questions.length - userAnswers.filter(a => a.trim()).length }} 题未答</span>
            <button v-if="!isLastQuestion" class="nav-btn primary" @click="nextQuestion">下一题</button>
            <button v-else class="nav-btn primary submit" :disabled="!allAnswered" @click="handleSubmit">
              <PhCheckSquare :size="16" weight="fill" />
              提交批改
            </button>
          </div>
        </div>
      </div>
    </template>

    <!-- ========== 阶段三：回顾 ========== -->
    <template v-if="stage === 'review'">
      <div class="review-stage">
        <div class="review-header">
          <div class="review-score">
            <span class="score-number">{{ correctCount }}</span>
            <span class="score-divider">/</span>
            <span class="score-total">{{ totalCount }}</span>
          </div>
          <p class="score-label">
            <template v-if="correctCount === totalCount">全部正确，太棒了！</template>
            <template v-else-if="correctCount >= totalCount * 0.6">不错，继续加油！</template>
            <template v-else>需要多加复习哦</template>
          </p>
          <div class="review-actions">
            <button class="action-btn" @click="backToConfig">重新配置</button>
            <button class="action-btn primary" @click="retryQuiz">再来一次</button>
          </div>
        </div>

        <div
          v-for="(q, qi) in questions"
          :key="qi"
          class="review-card"
          :class="{ correct: gradingResults[qi]?.correct, wrong: !gradingResults[qi]?.correct }"
        >
          <div class="review-status">
            <PhCheckSquare v-if="gradingResults[qi]?.correct" :size="18" weight="fill" class="icon-correct" />
            <PhWarning v-else :size="18" weight="duotone" class="icon-wrong" />
          </div>

          <div class="review-body">
            <div class="review-question">
              <span class="question-number">{{ qi + 1 }}.</span>
              {{ q.stem }}
            </div>

            <div v-if="q.wrongBookAdded || (!gradingResults[qi]?.correct && q.type !== 'qa')" class="wrong-book-note">
              已加入错题本
              <span v-if="q.wrongCount && q.wrongCount >= 2">，这道题你已经错了 {{ q.wrongCount }} 次</span>
            </div>

            <!-- 用户答案 -->
            <div class="review-answer">
              <span class="answer-label">你的答案：</span>
              <span :class="gradingResults[qi]?.correct ? 'text-correct' : 'text-wrong'">
                {{ gradingResults[qi]?.userAnswer || '未作答' }}
              </span>
            </div>

            <!-- 正确答案 -->
            <div v-if="!gradingResults[qi]?.correct" class="review-answer correct-answer">
              <span class="answer-label">正确答案：</span>
              <span class="text-correct">{{ q.answer }}</span>
            </div>

            <!-- 解析 -->
            <div v-if="q.analysis" class="review-analysis">
              <span class="analysis-label">解析：</span>
              {{ q.analysis }}
            </div>

            <button
              v-if="gradingResults[qi]?.correct && collectableType(q) && !q.wrongBookAdded"
              class="manual-add-btn"
              @click="handleManualAdd(q, qi)"
            >
              加入错题本
            </button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.quiz-page {
  max-width: 780px;
  margin: 0 auto;
  padding: var(--space-3xl) var(--space-xl);
  min-height: calc(100vh - 64px);
}

/* ==================== 配置阶段 ==================== */
.config-panel {
  background: var(--color-card-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-3xl);
}

.panel-header {
  text-align: center;
  margin-bottom: var(--space-3xl);
}

.panel-icon {
  color: var(--color-primary);
  margin-bottom: var(--space-md);
}

.panel-title {
  font-size: var(--font-size-h1);
  font-weight: 700;
  color: var(--color-text-title);
  margin: 0 0 var(--space-sm) 0;
}

.panel-desc {
  font-size: var(--font-size-body);
  color: var(--color-text-assist);
  margin: 0;
}

.config-section {
  margin-bottom: var(--space-xl);
}

.config-label {
  display: block;
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-title);
  margin-bottom: var(--space-sm);
}

.config-select {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-page-bg);
  color: var(--color-text-body);
  font-size: var(--font-size-body);
  font-family: inherit;
  outline: none;
  cursor: pointer;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 256 256'%3E%3Cpath d='M213.66,101.66l-80,80a8,8,0,0,1-11.32,0l-80-80a8,8,0,0,1,11.32-11.32L128,164.69l74.34-74.35a8,8,0,0,1,11.32,11.32Z' fill='%238C8C9A'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
  padding-right: 36px;
  transition: border-color var(--duration-fast);
}
.config-select:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-primary) 12%, transparent);
}

.type-options {
  display: flex;
  gap: var(--space-md);
}

.type-chip {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: var(--space-md);
  border: 2px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-card-bg);
  cursor: pointer;
  transition: all var(--duration-fast);
}
.type-chip:hover {
  border-color: var(--color-primary);
}
.type-chip.active {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
}

.type-chip-label {
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-title);
}

.type-chip-desc {
  font-size: var(--font-size-caption);
  color: var(--color-text-assist);
}

.count-slider {
  width: 100%;
  height: 6px;
  appearance: none;
  background: var(--color-border);
  border-radius: var(--radius-full);
  outline: none;
  cursor: pointer;
}
.count-slider::-webkit-slider-thumb {
  appearance: none;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--color-primary);
  cursor: pointer;
  box-shadow: var(--shadow-sm);
}

.count-hints {
  display: flex;
  justify-content: space-between;
  font-size: var(--font-size-caption);
  color: var(--color-text-assist);
  margin-top: var(--space-xs);
}

.error-msg {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-md);
  border-radius: var(--radius-md);
  background: #FFF2F0;
  border: 1px solid #FFCCC7;
  color: var(--color-danger);
  font-size: var(--font-size-small);
  margin-bottom: var(--space-xl);
}

.generate-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-sm);
  padding: 12px;
  border: 0;
  border-radius: var(--radius-md);
  background: var(--color-primary);
  color: #fff;
  font-size: var(--font-size-h3);
  font-weight: 600;
  cursor: pointer;
  transition: all var(--duration-fast);
  box-shadow: var(--shadow-button-primary);
}
.generate-btn:hover:not(:disabled) {
  background: #4566E6;
}
.generate-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* ==================== 答题阶段 ==================== */
.quiz-stage {
  display: flex;
  flex-direction: column;
  gap: var(--space-xl);
}

.quiz-progress {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  border: 0;
  background: transparent;
  color: var(--color-text-assist);
  font-size: var(--font-size-small);
  cursor: pointer;
  flex-shrink: 0;
  padding: 0;
}
.back-link:hover {
  color: var(--color-primary);
}

.progress-bar {
  flex: 1;
  height: 6px;
  background: var(--color-border);
  border-radius: var(--radius-full);
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: var(--color-primary);
  border-radius: var(--radius-full);
  transition: width var(--duration-base);
}

.progress-text {
  font-size: var(--font-size-small);
  color: var(--color-text-assist);
  flex-shrink: 0;
  min-width: 60px;
  text-align: right;
}

.quiz-card {
  background: var(--color-card-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-3xl);
}

.question-type-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: var(--radius-full);
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-size: var(--font-size-caption);
  font-weight: 600;
  margin-bottom: var(--space-lg);
}

.question-stem {
  font-size: var(--font-size-h3);
  font-weight: 600;
  color: var(--color-text-title);
  line-height: var(--line-height-body);
  margin-bottom: var(--space-xl);
}

.question-number {
  color: var(--color-primary);
}

.options-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  margin-bottom: var(--space-xl);
}

.option-btn {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-md);
  border: 2px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-card-bg);
  cursor: pointer;
  transition: all var(--duration-fast);
  text-align: left;
}
.option-btn:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
}
.option-btn.selected {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
}

.option-letter {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-full);
  background: var(--color-page-bg);
  color: var(--color-text-assist);
  font-weight: 700;
  font-size: var(--font-size-body);
  flex-shrink: 0;
}
.option-btn.selected .option-letter {
  background: var(--color-primary);
  color: #fff;
}

.option-text {
  font-size: var(--font-size-body);
  color: var(--color-text-body);
  line-height: var(--line-height-body);
}

.answer-input-area {
  margin-bottom: var(--space-xl);
}

.answer-input {
  width: 100%;
  padding: var(--space-md);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-page-bg);
  color: var(--color-text-body);
  font-size: var(--font-size-body);
  font-family: inherit;
  line-height: var(--line-height-body);
  outline: none;
  resize: vertical;
  transition: border-color var(--duration-fast);
}
.answer-input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-primary) 12%, transparent);
}

.quiz-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
}

.nav-btn {
  padding: 8px 20px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-card-bg);
  color: var(--color-text-body);
  font-size: var(--font-size-body);
  cursor: pointer;
  transition: all var(--duration-fast);
}
.nav-btn:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.nav-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.nav-btn.primary {
  background: var(--color-primary);
  color: #fff;
  border-color: var(--color-primary);
}
.nav-btn.primary:hover:not(:disabled) {
  background: #4566E6;
}

.nav-btn.submit {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
}

.nav-hint {
  font-size: var(--font-size-caption);
  color: var(--color-text-assist);
}

/* ==================== 回顾阶段 ==================== */
.review-stage {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}

.review-header {
  text-align: center;
  padding: var(--space-3xl);
  background: var(--color-card-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}

.review-score {
  font-size: 36px;
  font-weight: 700;
  color: var(--color-text-title);
  line-height: 1.2;
}

.score-number {
  color: var(--color-primary);
}

.score-divider {
  color: var(--color-text-assist);
  margin: 0 2px;
}

.score-total {
  color: var(--color-text-assist);
}

.score-label {
  font-size: var(--font-size-body);
  color: var(--color-text-assist);
  margin: var(--space-sm) 0 var(--space-xl) 0;
}

.review-actions {
  display: flex;
  justify-content: center;
  gap: var(--space-md);
}

.action-btn {
  padding: 8px 20px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-card-bg);
  color: var(--color-text-body);
  font-size: var(--font-size-body);
  cursor: pointer;
  transition: all var(--duration-fast);
}
.action-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.action-btn.primary {
  background: var(--color-primary);
  color: #fff;
  border-color: var(--color-primary);
}
.action-btn.primary:hover {
  background: #4566E6;
}

.review-card {
  display: flex;
  gap: var(--space-lg);
  padding: var(--space-xl);
  background: var(--color-card-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  transition: border-color var(--duration-fast);
}
.review-card.correct {
  border-left: 4px solid var(--color-success);
}
.review-card.wrong {
  border-left: 4px solid var(--color-danger);
}

.review-status {
  flex-shrink: 0;
}
.icon-correct { color: var(--color-success); }
.icon-wrong { color: var(--color-danger); }

.review-body {
  min-width: 0;
}

.review-question {
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-title);
  line-height: var(--line-height-body);
  margin-bottom: var(--space-md);
}

.review-answer {
  font-size: var(--font-size-small);
  margin-bottom: var(--space-xs);
}

.wrong-book-note {
  display: inline-block;
  margin-bottom: var(--space-sm);
  padding: 3px 10px;
  border-radius: var(--radius-full);
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-size: var(--font-size-caption);
  font-weight: 600;
}

.answer-label {
  color: var(--color-text-assist);
}

.text-correct { color: var(--color-success); font-weight: 600; }
.text-wrong { color: var(--color-danger); font-weight: 600; }

.correct-answer {
  border-left: 3px solid var(--color-success);
  padding-left: var(--space-sm);
}

.review-analysis {
  margin-top: var(--space-md);
  padding: var(--space-md);
  background: var(--color-page-bg);
  border-radius: var(--radius-md);
  font-size: var(--font-size-small);
  color: var(--color-text-body);
  line-height: var(--line-height-body);
}

.analysis-label {
  font-weight: 600;
  color: var(--color-text-title);
}

.manual-add-btn {
  margin-top: var(--space-md);
  padding: 8px 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-card-bg);
  color: var(--color-text-body);
  font-size: var(--font-size-small);
  cursor: pointer;
  transition: all var(--duration-fast);
}
.manual-add-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

/* ==================== 加载旋转 ==================== */
.btn-spinner {
  display: inline-block;
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
