import { request } from './http'
import type { Question, WrongQuestion, WrongQuestionSaveRequest } from './types'

export function listWrongQuestions(): Promise<WrongQuestion[]> {
  return request<WrongQuestion[]>({ url: '/wrong-questions', method: 'GET' })
}

export function saveWrongQuestion(payload: WrongQuestionSaveRequest): Promise<WrongQuestion> {
  return request<WrongQuestion>({ url: '/wrong-questions', method: 'POST', data: payload })
}

export function saveWrongQuestionsBatch(payload: WrongQuestionSaveRequest[]): Promise<WrongQuestion[]> {
  return request<WrongQuestion[]>({ url: '/wrong-questions/batch', method: 'POST', data: payload })
}

export function markWrongQuestionMastered(id: number): Promise<WrongQuestion> {
  return request<WrongQuestion>({ url: `/wrong-questions/${id}/master`, method: 'PATCH' })
}

export function deleteWrongQuestion(id: number): Promise<void> {
  return request<void>({ url: `/wrong-questions/${id}`, method: 'DELETE' })
}

export function reinforceWrongQuestion(id: number): Promise<Question[]> {
  return request<Question[]>({ url: `/wrong-questions/${id}/reinforce`, method: 'POST', timeout: 120000 })
}
