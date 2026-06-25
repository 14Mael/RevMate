import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import QuizRunner from '../QuizRunner.vue'
import type { Question } from '@/api/types'

const messageWarning = vi.fn()
vi.mock('element-plus', () => ({
  ElMessage: { warning: (...args: unknown[]) => messageWarning(...args) }
}))

function singleQuestion(overrides: Partial<Question> = {}): Question {
  return {
    stem: 'Java 中哪个关键字用于定义常量？',
    type: 'single',
    options: ['final', 'static', 'const', 'var'],
    answer: 'A',
    analysis: 'final 关键字用于声明常量，一旦赋值不可修改。',
    ...overrides
  }
}

function fillQuestion(overrides: Partial<Question> = {}): Question {
  return {
    stem: '操作系统中，进程的三种基本状态是就绪态、运行态和____。',
    type: 'fill',
    answer: '阻塞态',
    analysis: '进程三态模型：就绪、运行、阻塞。',
    ...overrides
  }
}

function qaQuestion(overrides: Partial<Question> = {}): Question {
  return {
    stem: '简述 HTTP 与 HTTPS 的区别。',
    type: 'qa',
    answer: 'HTTPS 是 HTTP 的安全版本，通过 SSL/TLS 加密传输数据。',
    analysis: '主要区别在于加密、端口、证书等方面。',
    ...overrides
  }
}

beforeEach(() => {
  messageWarning.mockReset()
})

describe('QuizRunner', () => {
  describe('初始化与状态', () => {
    it('初始处于 quiz 阶段，显示第一题', () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion()] }
      })
      expect(wrapper.find('.quiz-stage').exists()).toBe(true)
      expect(wrapper.find('.review-stage').exists()).toBe(false)
      expect(wrapper.find('.question-stem').text()).toContain('Java 中哪个关键字用于定义常量？')
      expect(wrapper.find('.progress-text').text()).toBe('1 / 1')
    })

    it('多题时正确显示进度', () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion(), fillQuestion(), qaQuestion()] }
      })
      expect(wrapper.find('.progress-text').text()).toBe('1 / 3')
    })

    it('第一题时上一题按钮禁用', () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion(), fillQuestion()] }
      })
      const prevBtn = wrapper.find('.quiz-nav .nav-btn:not(.primary)')
      expect(prevBtn.attributes('disabled')).toBeDefined()
    })

    it('最后一题显示提交按钮，而非下一题', () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion()] }
      })
      expect(wrapper.find('.nav-btn.submit').exists()).toBe(true)
      expect(wrapper.find('.nav-btn.primary:not(.submit)').exists()).toBe(false)
    })
  })

  describe('单选题交互', () => {
    it('点击选项后设置用户答案并高亮', async () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion()] }
      })
      const options = wrapper.findAll('.option-btn')
      expect(options.length).toBe(4)

      await options[2].trigger('click')
      expect(options[2].classes()).toContain('selected')
      expect(options[0].classes()).not.toContain('selected')
    })

    it('多次点击切换选中项', async () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion()] }
      })
      const options = wrapper.findAll('.option-btn')

      await options[0].trigger('click')
      expect(options[0].classes()).toContain('selected')

      await options[1].trigger('click')
      expect(options[0].classes()).not.toContain('selected')
      expect(options[1].classes()).toContain('selected')
    })

    it('选项字母标签正确（A/B/C/D）', () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion()] }
      })
      const letters = wrapper.findAll('.option-letter').map((el) => el.text())
      expect(letters).toEqual(['A', 'B', 'C', 'D'])
    })
  })

  describe('填空题与简答题输入', () => {
    it('填空题显示 textarea 输入框', () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [fillQuestion()] }
      })
      expect(wrapper.find('.answer-input').exists()).toBe(true)
      expect(wrapper.find('.answer-input').attributes('placeholder')).toBe('请输入答案...')
    })

    it('简答题显示不同的 placeholder', () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [qaQuestion()] }
      })
      expect(wrapper.find('.answer-input').attributes('placeholder')).toBe('请输入你的回答...')
    })
  })

  describe('题目导航', () => {
    it('下一题按钮推进到下一题', async () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion(), fillQuestion()] }
      })
      expect(wrapper.find('.question-stem').text()).toContain('Java')

      const nextBtn = wrapper.find('.nav-btn.primary:not(.submit)')
      await nextBtn.trigger('click')

      expect(wrapper.find('.question-stem').text()).toContain('操作系统')
      expect(wrapper.find('.progress-text').text()).toBe('2 / 2')
    })

    it('上一题按钮回退到上一题', async () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion(), fillQuestion()] }
      })
      await wrapper.find('.nav-btn.primary:not(.submit)').trigger('click')
      expect(wrapper.find('.question-stem').text()).toContain('操作系统')

      const prevBtn = wrapper.find('.quiz-nav .nav-btn:not(.primary)')
      await prevBtn.trigger('click')

      expect(wrapper.find('.question-stem').text()).toContain('Java')
    })

    it('未答完题时显示未答提示', async () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion(), fillQuestion()] }
      })
      expect(wrapper.find('.nav-hint').text()).toContain('还有 2 题未答')

      const options = wrapper.findAll('.option-btn')
      await options[0].trigger('click')

      expect(wrapper.find('.nav-hint').text()).toContain('还有 1 题未答')
    })
  })

  describe('提交与批改', () => {
    it('未全部作答时提交按钮被禁用', async () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion(), fillQuestion()] }
      })
      await wrapper.find('.nav-btn.primary:not(.submit)').trigger('click')
      const submitBtn = wrapper.find('.nav-btn.submit')
      expect(submitBtn.attributes('disabled')).toBeDefined()
    })

    it('全部作答后提交按钮可用', async () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion({ answer: 'A' })] }
      })
      const options = wrapper.findAll('.option-btn')
      await options[0].trigger('click')
      const submitBtn = wrapper.find('.nav-btn.submit')
      expect(submitBtn.attributes('disabled')).toBeUndefined()
    })

    it('未答完时调用 handleSubmit 弹出警告', async () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion(), fillQuestion()] }
      })
      const vm = wrapper.vm as { handleSubmit: () => void }
      vm.handleSubmit()
      expect(messageWarning).toHaveBeenCalledWith('请完成所有题目后再提交')
    })

    it('单选题答对，批改结果正确', async () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion({ answer: 'A' })] }
      })
      const options = wrapper.findAll('.option-btn')
      await options[0].trigger('click')
      await wrapper.find('.nav-btn.submit').trigger('click')

      expect(wrapper.find('.review-stage').exists()).toBe(true)
      expect(wrapper.find('.review-card.correct').exists()).toBe(true)
      expect(wrapper.find('.review-card.wrong').exists()).toBe(false)
      expect(wrapper.find('.score-number').text()).toBe('1')
      expect(wrapper.find('.score-total').text()).toBe('1')
    })

    it('单选题答错，批改结果错误', async () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion({ answer: 'A' })] }
      })
      const options = wrapper.findAll('.option-btn')
      await options[1].trigger('click')
      await wrapper.find('.nav-btn.submit').trigger('click')

      expect(wrapper.find('.review-card.wrong').exists()).toBe(true)
      expect(wrapper.find('.score-number').text()).toBe('0')
    })

    it('填空题答对（大小写不敏感）', async () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [fillQuestion({ answer: '阻塞态' })] }
      })
      const textarea = wrapper.find('.answer-input')
      await textarea.setValue('阻塞态')
      await wrapper.find('.nav-btn.submit').trigger('click')

      expect(wrapper.find('.review-card.correct').exists()).toBe(true)
    })

    it('全部答对时显示满分提示', async () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion({ answer: 'A' })] }
      })
      const options = wrapper.findAll('.option-btn')
      await options[0].trigger('click')
      await wrapper.find('.nav-btn.submit').trigger('click')

      expect(wrapper.find('.score-label').text()).toContain('全部正确，太棒了！')
    })

    it('60% 以上显示鼓励提示', async () => {
      const questions = [
        singleQuestion({ answer: 'A' }),
        singleQuestion({ answer: 'A', stem: '第二题' }),
        singleQuestion({ answer: 'A', stem: '第三题' })
      ]
      const wrapper = mount(QuizRunner, { props: { questions } })

      const q1Options = wrapper.findAll('.option-btn')
      await q1Options[0].trigger('click')

      const nextBtn = wrapper.find('.nav-btn.primary:not(.submit)')
      await nextBtn.trigger('click')
      const q2Options = wrapper.findAll('.option-btn')
      await q2Options[0].trigger('click')

      await nextBtn.trigger('click')
      const q3Options = wrapper.findAll('.option-btn')
      await q3Options[1].trigger('click')

      await wrapper.find('.nav-btn.submit').trigger('click')
      expect(wrapper.find('.score-label').text()).toContain('不错，继续加油！')
    })

    it('低于 60% 显示复习提示', async () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion({ answer: 'A' })] }
      })
      const options = wrapper.findAll('.option-btn')
      await options[1].trigger('click')
      await wrapper.find('.nav-btn.submit').trigger('click')

      expect(wrapper.find('.score-label').text()).toContain('需要多加复习哦')
    })

    it('提交后触发 completed 事件', async () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion({ answer: 'A' })] }
      })
      const options = wrapper.findAll('.option-btn')
      await options[0].trigger('click')
      await wrapper.find('.nav-btn.submit').trigger('click')

      const emitted = wrapper.emitted('completed') as unknown[][]
      expect(emitted).toHaveLength(1)
      expect(emitted[0][0]).toEqual({
        results: [{ correct: true, userAnswer: 'A' }],
        answers: ['A']
      })
    })
  })

  describe('回顾阶段', () => {
    it('显示解析内容', async () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion({ answer: 'A' })] }
      })
      const options = wrapper.findAll('.option-btn')
      await options[0].trigger('click')
      await wrapper.find('.nav-btn.submit').trigger('click')

      expect(wrapper.find('.review-analysis').text()).toContain('final 关键字用于声明常量')
    })

    it('错题显示正确答案', async () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion({ answer: 'A' })] }
      })
      const options = wrapper.findAll('.option-btn')
      await options[1].trigger('click')
      await wrapper.find('.nav-btn.submit').trigger('click')

      const correctAnswerDiv = wrapper.find('.correct-answer')
      expect(correctAnswerDiv.exists()).toBe(true)
      expect(correctAnswerDiv.text()).toContain('A')
    })

    it('答对的题不显示正确答案行', async () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion({ answer: 'A' })] }
      })
      const options = wrapper.findAll('.option-btn')
      await options[0].trigger('click')
      await wrapper.find('.nav-btn.submit').trigger('click')

      expect(wrapper.find('.correct-answer').exists()).toBe(false)
    })

    it('错题自动显示「已加入错题本」提示（单选）', async () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion({ answer: 'A' })] }
      })
      const options = wrapper.findAll('.option-btn')
      await options[1].trigger('click')
      await wrapper.find('.nav-btn.submit').trigger('click')

      expect(wrapper.find('.wrong-book-note').text()).toContain('已加入错题本')
    })

    it('简答题答错不自动显示错题本提示', async () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [qaQuestion()] }
      })
      const textarea = wrapper.find('.answer-input')
      await textarea.setValue('不知道')
      await wrapper.find('.nav-btn.submit').trigger('click')

      expect(wrapper.find('.wrong-book-note').exists()).toBe(false)
    })

    it('错题带 wrongCount 时显示错误次数', async () => {
      const wrapper = mount(QuizRunner, {
        props: {
          questions: [singleQuestion({ answer: 'A', wrongCount: 3, wrongBookAdded: true })]
        }
      })
      const options = wrapper.findAll('.option-btn')
      await options[1].trigger('click')
      await wrapper.find('.nav-btn.submit').trigger('click')

      expect(wrapper.find('.wrong-book-note').text()).toContain('这道题你已经错了 3 次')
    })
  })

  describe('事件回调', () => {
    it('点击返回按钮触发 back 事件', async () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion()] }
      })
      await wrapper.find('.back-link').trigger('click')
      expect(wrapper.emitted('back')).toHaveLength(1)
    })

    it('回顾阶段点击返回也触发 back 事件', async () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion({ answer: 'A' })] }
      })
      const options = wrapper.findAll('.option-btn')
      await options[0].trigger('click')
      await wrapper.find('.nav-btn.submit').trigger('click')

      const reviewActions = wrapper.find('.review-actions .action-btn:not(.primary)')
      await reviewActions.trigger('click')
      expect(wrapper.emitted('back')).toHaveLength(1)
    })

    it('点击再来一次触发 retry 事件', async () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion({ answer: 'A' })] }
      })
      const options = wrapper.findAll('.option-btn')
      await options[0].trigger('click')
      await wrapper.find('.nav-btn.submit').trigger('click')

      const retryBtn = wrapper.find('.review-actions .action-btn.primary')
      await retryBtn.trigger('click')
      expect(wrapper.emitted('retry')).toHaveLength(1)
    })

    it('showRetry=false 时不显示重试按钮', async () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion({ answer: 'A' })], showRetry: false }
      })
      const options = wrapper.findAll('.option-btn')
      await options[0].trigger('click')
      await wrapper.find('.nav-btn.submit').trigger('click')

      expect(wrapper.find('.review-actions .action-btn.primary').exists()).toBe(false)
    })
  })

  describe('题目类型标签', () => {
    it('单选题显示「单选题」标签', () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion()] }
      })
      expect(wrapper.find('.question-type-badge').text()).toBe('单选题')
    })

    it('填空题显示「填空题」标签', () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [fillQuestion()] }
      })
      expect(wrapper.find('.question-type-badge').text()).toBe('填空题')
    })

    it('简答题显示「简答题」标签', () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [qaQuestion()] }
      })
      expect(wrapper.find('.question-type-badge').text()).toBe('简答题')
    })

    it('无 type 但有 options 判定为单选题', () => {
      const q = singleQuestion()
      delete q.type
      const wrapper = mount(QuizRunner, { props: { questions: [q] } })
      expect(wrapper.find('.question-type-badge').text()).toBe('单选题')
    })

    it('无 type 无 options 但题干含 ____ 判定为填空题', () => {
      const q: Question = {
        stem: '一年有____个月',
        answer: '12',
        analysis: ''
      }
      const wrapper = mount(QuizRunner, { props: { questions: [q] } })
      expect(wrapper.find('.question-type-badge').text()).toBe('填空题')
    })
  })

  describe('手动加入错题本（allowManualAdd）', () => {
    it('allowManualAdd=false 时不显示手动加入按钮', async () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion({ answer: 'A' })], allowManualAdd: false }
      })
      const options = wrapper.findAll('.option-btn')
      await options[0].trigger('click')
      await wrapper.find('.nav-btn.submit').trigger('click')

      expect(wrapper.find('.manual-add-btn').exists()).toBe(false)
    })

    it('allowManualAdd 且答对时显示加入错题本按钮', async () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion({ answer: 'A' })], allowManualAdd: true }
      })
      const options = wrapper.findAll('.option-btn')
      await options[0].trigger('click')
      await wrapper.find('.nav-btn.submit').trigger('click')

      expect(wrapper.find('.manual-add-btn').exists()).toBe(true)
      expect(wrapper.find('.manual-add-btn').text()).toContain('加入错题本')
    })

    it('点击手动加入触发 manualAdd 事件', async () => {
      const question = singleQuestion({ answer: 'A' })
      const wrapper = mount(QuizRunner, {
        props: { questions: [question], allowManualAdd: true }
      })
      const options = wrapper.findAll('.option-btn')
      await options[0].trigger('click')
      await wrapper.find('.nav-btn.submit').trigger('click')
      await wrapper.find('.manual-add-btn').trigger('click')

      const emitted = wrapper.emitted('manualAdd') as unknown[][]
      expect(emitted).toHaveLength(1)
      expect(emitted[0][0]).toMatchObject({
        index: 0,
        userAnswer: 'A'
      })
    })

    it('wrongBookAdded=true 时不显示手动加入按钮', async () => {
      const wrapper = mount(QuizRunner, {
        props: {
          questions: [singleQuestion({ answer: 'A', wrongBookAdded: true })],
          allowManualAdd: true
        }
      })
      const options = wrapper.findAll('.option-btn')
      await options[0].trigger('click')
      await wrapper.find('.nav-btn.submit').trigger('click')

      expect(wrapper.find('.manual-add-btn').exists()).toBe(false)
    })
  })

  describe('props 变化重置状态', () => {
    it('切换 questions 后重置到 quiz 阶段第一题', async () => {
      const wrapper = mount(QuizRunner, {
        props: { questions: [singleQuestion({ answer: 'A' })] }
      })
      const options = wrapper.findAll('.option-btn')
      await options[0].trigger('click')
      await wrapper.find('.nav-btn.submit').trigger('click')
      expect(wrapper.find('.review-stage').exists()).toBe(true)

      await wrapper.setProps({ questions: [fillQuestion()] })
      expect(wrapper.find('.quiz-stage').exists()).toBe(true)
      expect(wrapper.find('.progress-text').text()).toBe('1 / 1')
      expect(wrapper.find('.question-stem').text()).toContain('操作系统')
    })
  })
})
