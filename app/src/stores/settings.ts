/**
 * 本机设置：字号三档、夜间模式。只存在本机，不上传。
 * 页面根节点用 useTheme() 拿到 class，CSS 变量在 App.vue 里按 class 切换。
 */
import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

export type FontScale = 'S' | 'M' | 'L'

const STORAGE_KEY = 'quiz.settings'

interface Persisted {
  fontScale: FontScale
  dark: boolean
}

function load(): Persisted {
  try {
    const raw = uni.getStorageSync(STORAGE_KEY) as Partial<Persisted> | ''
    if (raw && typeof raw === 'object') {
      return {
        fontScale: raw.fontScale === 'S' || raw.fontScale === 'L' ? raw.fontScale : 'M',
        dark: raw.dark === true,
      }
    }
  } catch {
    /* 用默认值 */
  }
  return { fontScale: 'M', dark: false }
}

export const useSettingsStore = defineStore('settings', () => {
  const initial = load()
  const fontScale = ref<FontScale>(initial.fontScale)
  const dark = ref(initial.dark)

  watch([fontScale, dark], () => {
    uni.setStorageSync(STORAGE_KEY, { fontScale: fontScale.value, dark: dark.value })
  })

  const themeClass = computed(() => `fs-${fontScale.value}${dark.value ? ' theme-dark' : ''}`)

  return { fontScale, dark, themeClass }
})
