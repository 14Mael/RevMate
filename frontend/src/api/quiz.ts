import { request } from './http'
import type { QuizRequest, QuizResponse } from './types'

export function generateQuiz(payload: QuizRequest): Promise<QuizResponse> {
  return request<QuizResponse>({ url: '/quiz', method: 'POST', data: payload })
}
