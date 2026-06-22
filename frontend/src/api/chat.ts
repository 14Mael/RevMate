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

/* ========== 聊天历史（localStorage） ========== */

const HISTORY_KEY = 'revmate_chat_history'
const MAX_HISTORY = 50

function loadHistory(): ChatHistoryItem[] {
  try {
    const raw = localStorage.getItem(HISTORY_KEY)
    return raw ? (JSON.parse(raw) as ChatHistoryItem[]) : []
  } catch {
    return []
  }
}

function saveHistoryList(list: ChatHistoryItem[]): void {
  localStorage.setItem(HISTORY_KEY, JSON.stringify(list.slice(0, MAX_HISTORY)))
}

export function getHistoryList(): ChatHistoryItem[] {
  return loadHistory().sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))
}

export function saveHistory(session: ChatHistoryItem): void {
  const list = loadHistory().filter((item) => item.id !== session.id)
  list.unshift(session)
  saveHistoryList(list)
}

export function deleteHistory(id: string): void {
  const list = loadHistory().filter((item) => item.id !== id)
  saveHistoryList(list)
}

export function clearAllHistory(): void {
  localStorage.removeItem(HISTORY_KEY)
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
