/**
 * 登录状态：令牌 + 当前用户，持久化到本地，7 天内免登录（令牌有效期由后端定，见 expiresAt）。
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api'
import type { AuthResult, User } from '@/api/types'
import { isTokenValid } from '@/logic/auth'
import { resolveAssetUrl } from '@/logic/http'
import { API_BASE } from '@/api/http'

const STORAGE_KEY = 'quiz.session'

interface Persisted {
  token: string
  expiresAt: string
  user: User | null
}

function load(): Persisted {
  try {
    const raw = uni.getStorageSync(STORAGE_KEY) as Persisted | ''
    if (raw && typeof raw === 'object') return raw
  } catch {
    /* 读不到就当没登录 */
  }
  return { token: '', expiresAt: '', user: null }
}

export const useUserStore = defineStore('user', () => {
  const initial = load()
  const token = ref(initial.token)
  const expiresAt = ref(initial.expiresAt)
  const user = ref<User | null>(initial.user)

  const loggedIn = computed(() => isTokenValid(token.value, expiresAt.value))
  const targetCategoryId = computed(() => user.value?.targetCategoryId ?? null)
  const targetCategoryName = computed(() => user.value?.targetCategoryName ?? '')
  const displayName = computed(() => user.value?.nickname || user.value?.username || '')
  const avatar = computed(() => resolveAssetUrl(API_BASE, user.value?.avatarUrl))

  function persist() {
    uni.setStorageSync(STORAGE_KEY, { token: token.value, expiresAt: expiresAt.value, user: user.value })
  }

  function applyAuth(result: AuthResult) {
    token.value = result.token
    expiresAt.value = result.expiresAt
    user.value = result.user
    persist()
  }

  function setUser(next: User) {
    user.value = next
    persist()
  }

  function clear() {
    token.value = ''
    expiresAt.value = ''
    user.value = null
    uni.removeStorageSync(STORAGE_KEY)
  }

  async function login(username: string, password: string) {
    applyAuth(await authApi.login({ username, password }))
  }

  async function register(username: string, password: string, nickname?: string) {
    applyAuth(await authApi.register({ username, password, nickname: nickname || undefined }))
  }

  /** 启动时拉一次最新资料；令牌失效会走 401 统一处理 */
  async function refresh() {
    if (!loggedIn.value) return
    setUser(await authApi.me())
  }

  async function update(body: { nickname?: string; avatarUrl?: string; targetCategoryId?: number }) {
    setUser(await authApi.updateMe(body))
  }

  async function setWrongThreshold(value: number) {
    setUser(await authApi.updateSettings({ wrongRemoveThreshold: Math.round(Number(value)) }))
  }

  return {
    token,
    expiresAt,
    user,
    loggedIn,
    targetCategoryId,
    targetCategoryName,
    displayName,
    avatar,
    login,
    register,
    refresh,
    update,
    setWrongThreshold,
    clear,
  }
})
