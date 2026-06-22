import { request } from './http'
import type { AnswerMode, ChatHistoryItem, ChatRequest, ChatResponse, ChatStreamEvent, Source } from './types'

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

/**
 * 组合流式：组件统一调用此入口。当前直接走真后端 SSE（chatStream）。
 * 历史上 frontend 分支用它在后端未就绪时降级到纯前端 mock，现已接通真后端。
 */
export async function* chatStreamWithFallback(
  payload: ChatRequest
): AsyncGenerator<{ text: string; done: boolean; sources: Source[]; answerMode?: AnswerMode }> {
  yield* chatStream(payload)
}

function authHeader(): Record<string, string> {
  const token = localStorage.getItem('revmate_token')
  return token ? { Authorization: `Bearer ${token}` } : {}
}

/* ========== 聊天历史（后端持久化，按账户隔离） ========== */

/** 列表已由后端按最近活跃时间倒序返回 */
export async function getHistoryList(): Promise<ChatHistoryItem[]> {
  return request<ChatHistoryItem[]>({ url: '/chat/history', method: 'GET' })
}

/** 按会话 id upsert（id 走路径，保留「同一会话按 id 更新」语义） */
export async function saveHistory(session: ChatHistoryItem): Promise<ChatHistoryItem> {
  const { id, title, messages, subjectId, course } = session
  return request<ChatHistoryItem>({
    url: `/chat/history/${id}`,
    method: 'PUT',
    data: { title, messages, subjectId, course }
  })
}

export async function deleteHistory(id: string): Promise<void> {
  await request<void>({ url: `/chat/history/${id}`, method: 'DELETE' })
}

export async function clearAllHistory(): Promise<void> {
  await request<void>({ url: '/chat/history', method: 'DELETE' })
}

export function generateHistoryId(): string {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
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
