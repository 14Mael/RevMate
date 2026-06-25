import { describe, it, expect, vi, beforeEach } from 'vitest'

// Stub http.request with a controllable spy.
const requestMock = vi.fn()
vi.mock('../http', () => ({
  request: (cfg: unknown) => requestMock(cfg)
}))

import { login, register } from '../auth'
import { listSubjects, createSubject, findOrCreateSubject } from '../subject'
import { generateQuiz } from '../quiz'
import {
  listWrongQuestions,
  saveWrongQuestion,
  saveWrongQuestionsBatch,
  markWrongQuestionMastered,
  deleteWrongQuestion,
  reinforceWrongQuestion
} from '../wrongQuestion'

beforeEach(() => {
  requestMock.mockReset()
})

describe('auth API', () => {
  it('login posts to /auth/login and forwards the payload', async () => {
    requestMock.mockResolvedValue({ token: 'jwt' })
    await login({ username: 'alice', password: 'secret' })
    expect(requestMock).toHaveBeenCalledWith({
      url: '/auth/login',
      method: 'POST',
      data: { username: 'alice', password: 'secret' }
    })
  })

  it('register posts to /auth/register', async () => {
    requestMock.mockResolvedValue(undefined)
    await register({ username: 'bob', password: 'pw123456' })
    expect(requestMock).toHaveBeenCalledWith({
      url: '/auth/register',
      method: 'POST',
      data: { username: 'bob', password: 'pw123456' }
    })
  })
})

describe('subject API', () => {
  it('listSubjects GETs /subjects', async () => {
    requestMock.mockResolvedValue([{ id: 1, name: 'Java' }])
    const result = await listSubjects()
    expect(requestMock.mock.calls[0][0]).toEqual({ url: '/subjects', method: 'GET' })
    expect(result[0].name).toBe('Java')
  })

  it('createSubject POSTs trimmed name', async () => {
    requestMock.mockResolvedValue({ id: 2, name: 'OS' })
    await createSubject({ name: 'OS' })
    expect(requestMock).toHaveBeenCalledWith({
      url: '/subjects',
      method: 'POST',
      data: { name: 'OS' }
    })
  })

  it('findOrCreateSubject rejects blank name', async () => {
    await expect(findOrCreateSubject('   ')).rejects.toThrow('课程名称不能为空')
    expect(requestMock).not.toHaveBeenCalled()
  })

  it('findOrCreateSubject returns existing subject by trimmed name', async () => {
    requestMock.mockResolvedValue([{ id: 1, name: 'Java' }])
    const result = await findOrCreateSubject('  Java  ')
    expect(result.id).toBe(1)
    expect(requestMock).toHaveBeenCalledTimes(1)
  })

  it('findOrCreateSubject creates new subject when none exists', async () => {
    requestMock.mockResolvedValueOnce([])
    requestMock.mockResolvedValueOnce({ id: 9, name: 'NewOne' })
    const result = await findOrCreateSubject('NewOne')
    expect(result.id).toBe(9)
    expect(requestMock).toHaveBeenCalledTimes(2)
    expect(requestMock.mock.calls[1][0]).toEqual({
      url: '/subjects',
      method: 'POST',
      data: { name: 'NewOne' }
    })
  })
})

describe('quiz API', () => {
  it('generateQuiz POSTs to /quiz with extended timeout', async () => {
    requestMock.mockResolvedValue({ questions: [] })
    await generateQuiz({ subjectId: 1, type: 'single', count: 5 })
    expect(requestMock).toHaveBeenCalledWith({
      url: '/quiz',
      method: 'POST',
      data: { subjectId: 1, type: 'single', count: 5 },
      timeout: 120000
    })
  })
})

describe('wrong question API', () => {
  it('listWrongQuestions GETs /wrong-questions', async () => {
    requestMock.mockResolvedValue([])
    await listWrongQuestions()
    expect(requestMock).toHaveBeenCalledWith({ url: '/wrong-questions', method: 'GET' })
  })

  it('saveWrongQuestion POSTs payload', async () => {
    requestMock.mockResolvedValue({ id: 1 })
    const payload = {
      subjectId: 1,
      course: 'Java',
      type: 'single' as const,
      stem: 'Q',
      answer: 'A',
      manual: true
    }
    await saveWrongQuestion(payload)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/wrong-questions',
      method: 'POST',
      data: payload
    })
  })

  it('saveWrongQuestionsBatch POSTs to /wrong-questions/batch', async () => {
    requestMock.mockResolvedValue([])
    await saveWrongQuestionsBatch([])
    expect(requestMock.mock.calls[0][0].url).toBe('/wrong-questions/batch')
    expect(requestMock.mock.calls[0][0].method).toBe('POST')
  })

  it('markWrongQuestionMastered PATCHes /master', async () => {
    requestMock.mockResolvedValue({ id: 11, mastered: true })
    await markWrongQuestionMastered(11)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/wrong-questions/11/master',
      method: 'PATCH'
    })
  })

  it('deleteWrongQuestion DELETEs the id', async () => {
    requestMock.mockResolvedValue(undefined)
    await deleteWrongQuestion(11)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/wrong-questions/11',
      method: 'DELETE'
    })
  })

  it('reinforceWrongQuestion POSTs to /reinforce with extended timeout', async () => {
    requestMock.mockResolvedValue([])
    await reinforceWrongQuestion(11)
    expect(requestMock).toHaveBeenCalledWith({
      url: '/wrong-questions/11/reinforce',
      method: 'POST',
      timeout: 120000
    })
  })
})
