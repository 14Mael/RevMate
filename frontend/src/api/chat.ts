import { request } from './http'
import type { ChatRequest, ChatResponse, Source } from './types'

export async function chat(payload: ChatRequest): Promise<ChatResponse> {
  return request<ChatResponse>({ url: '/chat', method: 'POST', data: payload })
}

export async function* chatStream(payload: ChatRequest): AsyncGenerator<{ text: string; done: boolean; sources: Source[] }> {
  const response = await chat(payload)
  const chunks = response.answer.match(/[\s\S]{1,8}/g) ?? ['']
  for (let i = 0; i < chunks.length; i++) {
    const done = i === chunks.length - 1
    yield { text: chunks[i], done, sources: done ? response.sources : [] }
    if (!done) {
      await new Promise((resolve) => window.setTimeout(resolve, 20))
    }
  }
}
