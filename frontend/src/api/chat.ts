import { request } from './http'
import type { AnswerMode, ChatRequest, ChatResponse, ChatStreamEvent, Source } from './types'

const DISPLAY_CHUNK_SIZE = 4
const DISPLAY_CHUNK_DELAY_MS = 24

export async function chat(payload: ChatRequest): Promise<ChatResponse> {
  return request<ChatResponse>({ url: '/chat', method: 'POST', data: payload })
}

export async function* chatStream(
  payload: ChatRequest
): AsyncGenerator<{ text: string; done: boolean; sources: Source[]; answerMode?: AnswerMode }> {
  const response = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...authHeader()
    },
    body: JSON.stringify(payload)
  })

  if (!response.ok) {
    throw new Error(`Stream request failed with status ${response.status}`)
  }
  if (!response.body) {
    throw new Error('Stream response body is empty')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const parsed = parseSseBuffer(buffer)
    buffer = parsed.rest

    for (const event of parsed.events) {
      for await (const chunk of toStreamChunks(event)) {
        yield chunk
      }
    }
  }

  buffer += decoder.decode()
  const parsed = parseSseBuffer(buffer, true)
  for (const event of parsed.events) {
    for await (const chunk of toStreamChunks(event)) {
      yield chunk
    }
  }
}

function authHeader(): Record<string, string> {
  const token = localStorage.getItem('revmate_token')
  return token ? { Authorization: `Bearer ${token}` } : {}
}

function parseSseBuffer(buffer: string, flush = false): { events: ChatStreamEvent[]; rest: string } {
  const normalized = buffer.replace(/\r\n/g, '\n')
  const parts = normalized.split('\n\n')
  const rest = flush ? '' : (parts.pop() ?? '')
  const events = parts.map(parseSseBlock).filter((event): event is ChatStreamEvent => event !== null)

  return { events, rest }
}

function parseSseBlock(block: string): ChatStreamEvent | null {
  const data = block
    .split('\n')
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n')

  if (!data) return null
  return JSON.parse(data) as ChatStreamEvent
}

async function* toStreamChunks(event: ChatStreamEvent): AsyncGenerator<{
  text: string
  done: boolean
  sources: Source[]
  answerMode?: AnswerMode
}> {
  if (event.type === 'done') {
    yield {
      text: '',
      done: true,
      sources: event.sources ?? [],
      answerMode: event.answerMode
    }
    return
  }

  const text = event.text ?? ''
  const chunks = text.match(new RegExp(`[\\s\\S]{1,${DISPLAY_CHUNK_SIZE}}`, 'g')) ?? ['']
  for (let i = 0; i < chunks.length; i++) {
    yield {
      text: chunks[i],
      done: false,
      sources: []
    }
    if (i < chunks.length - 1) {
      await new Promise((resolve) => window.setTimeout(resolve, DISPLAY_CHUNK_DELAY_MS))
    }
  }
}
