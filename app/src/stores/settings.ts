/**
 * 本机设置：字号三档、夜间模式、学习提醒。只存在本机，不上传。
 * 页面根节点用 useTheme() 拿到 class，CSS 变量在 App.vue 里按 class 切换。
 */
import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

export type FontScale = 'S' | 'M' | 'L'

const STORAGE_KEY = 'quiz.settings'

interface Persisted {
  fontScale: FontScale
  dark: boolean
  reminderOn: boolean
  /** HH:mm，本机时间 */
  reminderTime: string
}

const DEFAULTS: Persisted = { fontScale: 'M', dark: false, reminderOn: false, reminderTime: '20:00' }

function load(): Persisted {
  try {
    const raw = uni.getStorageSync(STORAGE_KEY) as Partial<Persisted> | ''
    if (raw && typeof raw === 'object') {
      return {
        fontScale: raw.fontScale === 'S' || raw.fontScale === 'L' ? raw.fontScale : 'M',
        dark: raw.dark === true,
        reminderOn: raw.reminderOn === true,
        reminderTime: typeof raw.reminderTime === 'string' && /^\d{1,2}:\d{2}$/.test(raw.reminderTime) ? raw.reminderTime : DEFAULTS.reminderTime,
      }
    }
  } catch {
    /* 用默认值 */
  }
  return { ...DEFAULTS }
}

export const useSettingsStore = defineStore('settings', () => {
  const initial = load()
  const fontScale = ref<FontScale>(initial.fontScale)
  const dark = ref(initial.dark)
  const reminderOn = ref(initial.reminderOn)
  const reminderTime = ref(initial.reminderTime)

  watch([fontScale, dark, reminderOn, reminderTime], () => {
    uni.setStorageSync(STORAGE_KEY, {
      fontScale: fontScale.value,
      dark: dark.value,
      reminderOn: reminderOn.value,
      reminderTime: reminderTime.value,
    })
  })

  const themeClass = computed(() => `fs-${fontScale.value}${dark.value ? ' theme-dark' : ''}`)

  return { fontScale, dark, reminderOn, reminderTime, themeClass }
})
