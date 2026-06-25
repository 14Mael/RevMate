import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserStore } from '../user'

describe('useUserStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('starts with no token when localStorage is empty', () => {
    const store = useUserStore()
    expect(store.token).toBeNull()
    expect(store.username).toBeNull()
  })

  it('setToken persists token to localStorage', () => {
    const store = useUserStore()
    store.setToken('abc-123', 'alice')
    expect(store.token).toBe('abc-123')
    expect(store.username).toBe('alice')
    expect(localStorage.getItem('revmate_token')).toBe('abc-123')
    expect(localStorage.getItem('revmate_username')).toBe('alice')
  })

  it('setToken without username only updates token', () => {
    const store = useUserStore()
    store.setToken('xyz-999')
    expect(store.token).toBe('xyz-999')
    expect(store.username).toBeNull()
    expect(localStorage.getItem('revmate_token')).toBe('xyz-999')
  })

  it('clear removes token and username from state and storage', () => {
    const store = useUserStore()
    store.setToken('abc-123', 'alice')
    store.clear()
    expect(store.token).toBeNull()
    expect(store.username).toBeNull()
    expect(localStorage.getItem('revmate_token')).toBeNull()
    expect(localStorage.getItem('revmate_username')).toBeNull()
  })
})
