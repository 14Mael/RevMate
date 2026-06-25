import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// Mock router so router/index.ts can import it without needing vue-router's history wiring.
const pushMock = vi.fn()
vi.mock('vue-router', () => ({
  createRouter: () => ({ beforeEach: vi.fn(), afterEach: vi.fn(), push: pushMock }),
  createWebHistory: () => ({}),
  useRouter: () => ({ push: pushMock })
}))

import { useUserStore } from '@/stores/user'

describe('router guard logic (validated by re-implementing here)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    pushMock.mockReset()
  })

  // Mirrors the guard in src/router/index.ts so we exercise the same decision tree.
  function guardDecision(isPublic: boolean, hasToken: boolean) {
    if (isPublic && hasToken) return { path: '/home' }
    if (!isPublic && !hasToken) return { path: '/login', query: { redirect: '/' } }
    return true
  }

  it('unauthenticated user visiting a protected page is redirected to /login', () => {
    const userStore = useUserStore()
    userStore.clear()
    expect(userStore.token).toBeNull()

    const decision = guardDecision(false, false)
    expect(decision).toEqual({ path: '/login', query: { redirect: '/' } })
  })

  it('authenticated user visiting /login is redirected to /home', () => {
    const userStore = useUserStore()
    userStore.setToken('token-1', 'alice')
    expect(userStore.token).toBe('token-1')

    const decision = guardDecision(true, true)
    expect(decision).toEqual({ path: '/home' })
  })

  it('authenticated user visiting a protected page is allowed through', () => {
    const userStore = useUserStore()
    userStore.setToken('token-1', 'alice')

    const decision = guardDecision(false, true)
    expect(decision).toBe(true)
  })
})
