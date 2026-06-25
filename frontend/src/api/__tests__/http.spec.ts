import { describe, it, expect } from 'vitest'

// http.ts is a thin axios wrapper. We only verify:
// 1. The module exports a `request` function with the expected signature.
// 2. The `default` axios instance has the configured baseURL.
// We do not exercise the interceptors here — they are wired to Element Plus
// and the user store at module-load time, which is not the unit-under-test.

import http, { request } from '../http'

describe('http module surface', () => {
  it('exports a request function', () => {
    expect(typeof request).toBe('function')
  })

  it('exports a configured axios instance with /api baseURL and 30s timeout', () => {
    expect(typeof http.request).toBe('function')
    expect((http.defaults.baseURL ?? '').replace(/\/$/, '')).toBe('/api')
    expect(http.defaults.timeout).toBe(30000)
  })
})
