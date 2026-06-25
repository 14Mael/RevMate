import { describe, it, expect, vi, beforeEach } from 'vitest'

const requestMock = vi.fn()
vi.mock('../http', () => ({
  request: (cfg: unknown) => requestMock(cfg)
}))

import { chat, getHistoryList, saveHistory, deleteHistory, clearAllHistory, generateHistoryId, chatStream } from '../chat'
import type { ChatStreamEvent } from '../types'

beforeEach(() => {
  requestMock.mockReset()
  localStorage.clear()
})

describe('chat API', () => {
  it('chat POSTs to /chat with payload', async () => {
    requestMock.mockResolvedValue({ answer: 'hi', sources: [], answerMode: 'general' })
    await chat({ subjectId: 1, question: '你好' })
    expect(requestMock).toHaveBeenCalledWith({
      url: '/chat',
      method: 'POST',
      data: { subjectId: 1, question: '你好' }
    })
  })
})

describe('chat history API', () => {
  it('getHistoryList GETs /chat/history', async () => {
    requestMock.mockResolvedValue([])
    await getHistoryList()
    expect(requestMock).toHaveBeenCalledWith({ url: '/chat/history', method: 'GET' })
  })

  it('saveHistory PUTs to /chat/history/:id with stripped fields', async () => {
    requestMock.mockResolvedValue({ id: 'abc', title: 'test', messages: [], createdAt: '', subjectId: 1 })
    await saveHistory({
      id: 'abc',
      title: 'test',
      messages: [{ role: 'user', content: 'hi' }],
      createdAt: '2024-01-01',
      subjectId: 1,
      course: 'Java'
    })
    const call = requestMock.mock.calls[0][0] as { url: string; method: string; data: Record<string, unknown> }
    expect(call.url).toBe('/chat/history/abc')
    expect(call.method).toBe('PUT')
    expect(call.data).toEqual({
      title: 'test',
      messages: [{ role: 'user', content: 'hi' }],
      subjectId: 1,
      course: 'Java'
    })
    expect(call.data.id).toBeUndefined()
  })

  it('deleteHistory DELETEs /chat/history/:id', async () => {
    requestMock.mockResolvedValue(undefined)
    await deleteHistory('abc123')
    expect(requestMock).toHaveBeenCalledWith({
      url: '/chat/history/abc123',
      method: 'DELETE'
    })
  })

  it('clearAllHistory DELETEs /chat/history', async () => {
    requestMock.mockResolvedValue(undefined)
    await clearAllHistory()
    expect(requestMock).toHaveBeenCalledWith({
      url: '/chat/history',
      method: 'DELETE'
    })
  })

  it('generateHistoryId returns non-empty string', () => {
    const id = generateHistoryId()
    expect(typeof id).toBe('string')
    expect(id.length).toBeGreaterThan(0)
  })

  it('generateHistoryId produces unique ids', () => {
    const ids = new Set(Array.from({ length: 100 }, () => generateHistoryId()))
    expect(ids.size).toBe(100)
  })
})

describe('chatStream SSE 解析', () => {
  function mockStreamResponse(events: ChatStreamEvent[]) {
    const encoder = new TextEncoder()
    const chunks: Uint8Array[] = []
    for (const event of events) {
      const line = `data: ${JSON.stringify(event)}\n\n`
      chunks.push(encoder.encode(line))
    }

    let index = 0
    const reader = {
      read: vi.fn().mockImplementation(() => {
        if (index >= chunks.length) {
          return Promise.resolve({ done: true, value: undefined })
        }
        return Promise.resolve({ done: false, value: chunks[index++] })
      })
    }

    const response = {
      ok: true,
      body: {
        getReader: () => reader
      }
    }

    const fetchMock = vi.fn().mockResolvedValue(response)
    vi.stubGlobal('fetch', fetchMock)
    return { reader, fetchMock }
  }

  it('流式接收单个 delta 事件', async () => {
    mockStreamResponse([
      { type: 'delta', text: 'Hello' },
      { type: 'done', sources: [] }
    ])

    const gen = chatStream({ subjectId: 1, question: 'hi' })
    const results = []
    for await (const chunk of gen) {
      results.push(chunk)
    }

    const texts = results.filter((r) => !r.done).map((r) => r.text).join('')
    expect(texts).toContain('Hello')
    expect(results[results.length - 1].done).toBe(true)
  })

  it('流式接收多个 delta 事件并拼接完整文本', async () => {
    mockStreamResponse([
      { type: 'delta', text: '你' },
      { type: 'delta', text: '好' },
      { type: 'delta', text: '，' },
      { type: 'delta', text: '有什么可以帮你的？' },
      { type: 'done', sources: [{ type: 'web', title: 'test', snippet: '...', url: 'https://example.com' }], answerMode: 'web' }
    ])

    const gen = chatStream({ subjectId: 1, question: 'hi' })
    const results = []
    for await (const chunk of gen) {
      results.push(chunk)
    }

    const doneChunk = results.find((r) => r.done)
    expect(doneChunk).toBeDefined()
    expect(doneChunk?.sources).toHaveLength(1)
    expect(doneChunk?.answerMode).toBe('web')
  })

  it('请求失败时抛出错误', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 500 }))

    const gen = chatStream({ subjectId: 1, question: 'hi' })
    await expect(async () => {
      for await (const _ of gen) {
        // noop
      }
    }).rejects.toThrow('500')
  })

  it('响应体为空时抛出错误', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, body: null }))

    const gen = chatStream({ subjectId: 1, question: 'hi' })
    await expect(async () => {
      for await (const _ of gen) {
        // noop
      }
    }).rejects.toThrow('body is empty')
  })

  it('请求时自动带上 localStorage 中的 token', async () => {
    localStorage.setItem('revmate_token', 'test-jwt-token')
    const { fetchMock } = mockStreamResponse([{ type: 'done', sources: [] }])

    const gen = chatStream({ subjectId: 1, question: 'hi' })
    for await (const _ of gen) {
      // noop
    }

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [, options] = fetchMock.mock.calls[0]
    expect(options.headers.Authorization).toBe('Bearer test-jwt-token')
  })

  it('没有 token 时不带上 Authorization 头', async () => {
    const { fetchMock } = mockStreamResponse([{ type: 'done', sources: [] }])

    const gen = chatStream({ subjectId: 1, question: 'hi' })
    for await (const _ of gen) {
      // noop
    }

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [, options] = fetchMock.mock.calls[0]
    expect(options.headers.Authorization).toBeUndefined()
  })

  it('SSE 事件被拆分成多个 chunk 时仍能正确解析（粘包）', async () => {
    const encoder = new TextEncoder()
    const fullEvent = `data: ${JSON.stringify({ type: 'delta', text: 'Hello World' })}\n\n`
    const mid = Math.floor(fullEvent.length / 2)
    const chunk1 = fullEvent.slice(0, mid)
    const chunk2 = fullEvent.slice(mid)

    let index = 0
    const chunks = [encoder.encode(chunk1), encoder.encode(chunk2), encoder.encode(`data: ${JSON.stringify({ type: 'done', sources: [] })}\n\n`)]
    const reader = {
      read: vi.fn().mockImplementation(() => {
        if (index >= chunks.length) {
          return Promise.resolve({ done: true, value: undefined })
        }
        return Promise.resolve({ done: false, value: chunks[index++] })
      })
    }

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      body: { getReader: () => reader }
    }))

    const gen = chatStream({ subjectId: 1, question: 'hi' })
    const results = []
    for await (const chunk of gen) {
      results.push(chunk)
    }

    const texts = results.filter((r) => !r.done).map((r) => r.text).join('')
    expect(texts).toContain('Hello World')
  })

  it('空 data 行被忽略', async () => {
    const encoder = new TextEncoder()
    const raw = `data: ${JSON.stringify({ type: 'delta', text: 'Hi' })}\n\ndata:\n\ndata: ${JSON.stringify({ type: 'done', sources: [] })}\n\n`

    let index = 0
    const chunks = [encoder.encode(raw)]
    const reader = {
      read: vi.fn().mockImplementation(() => {
        if (index >= chunks.length) {
          return Promise.resolve({ done: true, value: undefined })
        }
        return Promise.resolve({ done: false, value: chunks[index++] })
      })
    }

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      body: { getReader: () => reader }
    }))

    const gen = chatStream({ subjectId: 1, question: 'hi' })
    const results = []
    for await (const chunk of gen) {
      results.push(chunk)
    }

    expect(results[results.length - 1].done).toBe(true)
  })
})
