<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { PhArrowLeft, PhCheckSquare, PhWarning } from '@/components/icons'
import type { GradingResult, Question } from '@/api/types'

const props = withDefaults(defineProps<{
  questions: Question[]
  backLabel?: string
  retryLabel?: string
  showRetry?: boolean
  allowManualAdd?: boolean
}>(), {
  backLabel: '返回',
  retryLabel: '再来一次',
  showRetry: true,
  allowManualAdd: false
})

const emit = defineEmits<{
  back: []
  retry: []
  completed: [payload: { results: GradingResult[]; answers: string[] }]
  manualAdd: [payload: { question: Question; index: number; userAnswer: string }]
}>()

const stage = ref<'quiz' | 'review'>('quiz')
const userAnswers = ref<string[]>([])
const currentIndex = ref(0)
const gradingResults = ref<GradingResult[]>([])

watch(
  () => props.questions,
  () => {
    stage.value = 'quiz'
    userAnswers.value = new Array(props.questions.length).fill('')
    gradingResults.value = []
    currentIndex.value = 0
    nextTick(() => scrollToQuestion())
  },
  { immediate: true }
)

const currentQuestion = computed(() => props.questions[currentIndex.value] ?? null)
const isLastQuestion = computed(() => currentIndex.value >= props.questions.length - 1)
const isFirstQuestion = computed(() => currentIndex.value === 0)
const allAnswered = computed(() => userAnswers.value.every((answer) => answer.trim() !== ''))
const correctCount = computed(() => gradingResults.value.filter((result) => result.correct).length)
const totalCount = computed(() => props.questions.length)

function selectOption(optionIndex: number) {
  if (!currentQuestion.value || stage.value !== 'quiz') return
  userAnswers.value[currentIndex.value] = optionLabel(optionIndex)
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

function handleSubmit() {
  if (!allAnswered.value) {
    ElMessage.warning('请完成所有题目后再提交')
    return
  }

  gradingResults.value = props.questions.map((question, index) => {
    const userAnswer = userAnswers.value[index].trim()
    const correctAnswer = question.answer.trim()
    const correct = question.options && question.options.length > 0
      ? userAnswer.toUpperCase() === correctAnswer.toUpperCase()
      : userAnswer.toLowerCase() === correctAnswer.toLowerCase()

    return { correct, userAnswer }
  })

  stage.value = 'review'
  emit('completed', { results: gradingResults.value, answers: [...userAnswers.value] })
  nextTick(() => scrollToQuestion())
}

function scrollToQuestion() {
  const el = document.querySelector('.quiz-card, .review-stage')
  el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function optionLabel(index: number): string {
  return String.fromCharCode(65 + index)
}

function questionTypeLabel(question: Question): string {
  if (question.type === 'single') return '单选题'
  if (question.type === 'fill') return '填空题'
  if (question.type === 'qa') return '简答题'
  if (question.options && question.options.length > 0) return '单选题'
  if (question.stem.includes('____') || question.stem.includes('___') || question.stem.includes('（）')) return '填空题'
  return '简答题'
}

function canManualAdd(question: Question, index: number): boolean {
  return props.allowManualAdd
    && gradingResults.value[index]?.correct
    && (question.type === 'single' || question.type === 'fill' || !question.type)
    && !question.wrongBookAdded
}
</script>

<template>
  <div class="runner">
    <template v-if="stage === 'quiz'">
      <div class="quiz-stage">
        <div class="quiz-progress">
          <button class="back-link" @click="emit('back')">
            <PhArrowLeft :size="16" />
            {{ backLabel }}
          </button>
          <div class="progress-bar">
            <div class="progress-fill" :style="{ width: `${((currentIndex + 1) / questions.length) * 100}%` }" />
          </div>
          <span class="progress-text">{{ currentIndex + 1 }} / {{ questions.length }}</span>
        </div>

        <div class="quiz-card" v-if="currentQuestion">
          <div class="question-type-badge">{{ questionTypeLabel(currentQuestion) }}</div>
          <div class="question-stem">
            <span class="question-number">{{ currentIndex + 1 }}.</span>
            {{ currentQuestion.stem }}
          </div>

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

          <div v-else class="answer-input-area">
            <textarea
              v-model="userAnswers[currentIndex]"
              class="answer-input"
              :placeholder="questionTypeLabel(currentQuestion) === '填空题' ? '请输入答案...' : '请输入你的回答...'"
              rows="3"
            />
          </div>

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

    <template v-else>
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
            <button class="action-btn" @click="emit('back')">{{ backLabel }}</button>
            <button v-if="showRetry" class="action-btn primary" @click="emit('retry')">{{ retryLabel }}</button>
          </div>
        </div>

        <div
          v-for="(question, qi) in questions"
          :key="`${question.stem}-${qi}`"
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
              {{ question.stem }}
            </div>

            <div v-if="question.wrongBookAdded || (!gradingResults[qi]?.correct && question.type !== 'qa')" class="wrong-book-note">
              已加入错题本
              <span v-if="question.wrongCount && question.wrongCount >= 2">，这道题你已经错了 {{ question.wrongCount }} 次</span>
            </div>

            <div class="review-answer">
              <span class="answer-label">你的答案：</span>
              <span :class="gradingResults[qi]?.correct ? 'text-correct' : 'text-wrong'">
                {{ gradingResults[qi]?.userAnswer || '未作答' }}
              </span>
            </div>

            <div v-if="!gradingResults[qi]?.correct" class="review-answer correct-answer">
              <span class="answer-label">正确答案：</span>
              <span class="text-correct">{{ question.answer }}</span>
            </div>

            <div v-if="question.analysis" class="review-analysis">
              <span class="analysis-label">解析：</span>
              {{ question.analysis }}
            </div>

            <button
              v-if="canManualAdd(question, qi)"
              class="manual-add-btn"
              @click="emit('manualAdd', { question, index: qi, userAnswer: gradingResults[qi]?.userAnswer || '' })"
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
.runner,
.quiz-stage,
.review-stage {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
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
.back-link:hover { color: var(--color-primary); }

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

.quiz-card,
.review-header {
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

.question-number { color: var(--color-primary); }

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
.option-btn:hover,
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

.answer-input-area { margin-bottom: var(--space-xl); }

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

.nav-btn,
.action-btn,
.manual-add-btn {
  padding: 8px 20px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-card-bg);
  color: var(--color-text-body);
  font-size: var(--font-size-body);
  cursor: pointer;
  transition: all var(--duration-fast);
}
.nav-btn:hover:not(:disabled),
.action-btn:hover,
.manual-add-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.nav-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.nav-btn.primary,
.action-btn.primary {
  background: var(--color-primary);
  color: #fff;
  border-color: var(--color-primary);
}
.nav-btn.primary:hover:not(:disabled),
.action-btn.primary:hover {
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

.review-header {
  text-align: center;
}

.review-score {
  font-size: 36px;
  font-weight: 700;
  color: var(--color-text-title);
  line-height: 1.2;
}
.score-number { color: var(--color-primary); }
.score-divider,
.score-total { color: var(--color-text-assist); }
.score-divider { margin: 0 2px; }

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

.review-card {
  display: flex;
  gap: var(--space-lg);
  padding: var(--space-xl);
  background: var(--color-card-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  transition: border-color var(--duration-fast);
}
.review-card.correct { border-left: 4px solid var(--color-success); }
.review-card.wrong { border-left: 4px solid var(--color-danger); }

.review-status { flex-shrink: 0; }
.icon-correct { color: var(--color-success); }
.icon-wrong { color: var(--color-danger); }

.review-body { min-width: 0; flex: 1; }

.review-question {
  font-size: var(--font-size-body);
  font-weight: 600;
  color: var(--color-text-title);
  line-height: var(--line-height-body);
  margin-bottom: var(--space-md);
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

.review-answer {
  font-size: var(--font-size-small);
  margin-bottom: var(--space-xs);
}
.answer-label { color: var(--color-text-assist); }
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
  font-size: var(--font-size-small);
}
</style>
