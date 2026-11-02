/** 管理员登录状态，存 localStorage；只有 ADMIN 能进后台 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { AuthResult, User } from '@/api/types'

const KEY = 'quiz.admin.session'

interface Saved {
  token: string
  expiresAt: string
  user: User | null
}

function load(): Saved {
  try {
    const raw = localStorage.getItem(KEY)
    if (raw) return JSON.parse(raw) as Saved
  } catch {
    /* 读不到当没登录 */
  }
  return { token: '', expiresAt: '', user: null }
}

export const useAuthStore = defineStore('auth', () => {
  const saved = load()
  const token = ref(saved.token)
  const expiresAt = ref(saved.expiresAt)
  const user = ref<User | null>(saved.user)

  const loggedIn = computed(() => {
    const t = Date.parse(expiresAt.value)
    return !!token.value && !Number.isNaN(t) && t > Date.now() && user.value?.role === 'ADMIN'
  })

  function apply(result: AuthResult) {
    token.value = result.token
    expiresAt.value = result.expiresAt
    user.value = result.user
    localStorage.setItem(KEY, JSON.stringify({ token: token.value, expiresAt: expiresAt.value, user: user.value }))
  }

  function clear() {
    token.value = ''
    expiresAt.value = ''
    user.value = null
    localStorage.removeItem(KEY)
  }

  return { token, user, loggedIn, apply, clear }
})
