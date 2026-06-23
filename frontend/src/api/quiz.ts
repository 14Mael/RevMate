import { request } from './http'
import type { QuizRequest, QuizResponse } from './types'

export function generateQuiz(payload: QuizRequest): Promise<QuizResponse> {
  // 出题需调用大模型生成多道题，耗时常超过默认 30s，单独放宽到 120s
  return request<QuizResponse>({ url: '/quiz', method: 'POST', data: payload, timeout: 120000 })
}
