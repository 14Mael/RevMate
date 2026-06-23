/**
 * 用户态 store
 * - token 持久化到 localStorage（key: revmate_token）
 * - 暴露 setToken / clear
 */

import { defineStore } from 'pinia'
import { ref } from 'vue'

const TOKEN_KEY = 'revmate_token'
const USERNAME_KEY = 'revmate_username'

export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  const username = ref<string | null>(localStorage.getItem(USERNAME_KEY))

  function setToken(newToken: string, name?: string) {
    token.value = newToken
    localStorage.setItem(TOKEN_KEY, newToken)
    if (name) {
      username.value = name
      localStorage.setItem(USERNAME_KEY, name)
    }
  }

  function clear() {
    token.value = null
    username.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USERNAME_KEY)
  }

  return { token, username, setToken, clear }
})
