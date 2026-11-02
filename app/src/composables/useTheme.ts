import { storeToRefs } from 'pinia'
import { useSettingsStore } from '@/stores/settings'

/** 页面根节点的 class：`<view class="page-root" :class="themeClass">` */
export function useTheme() {
  const { themeClass } = storeToRefs(useSettingsStore())
  return { themeClass }
}
