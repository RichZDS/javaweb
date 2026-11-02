import { watch } from 'vue'
import { storeToRefs } from 'pinia'
import { onShow } from '@dcloudio/uni-app'
import { useSettingsStore } from '@/stores/settings'

/** 原生导航栏和底部页签跟着夜间模式变色（不是页签页时 setTabBarStyle 会失败，忽略即可） */
export function applyChrome(dark: boolean): void {
  uni.setNavigationBarColor({
    frontColor: dark ? '#ffffff' : '#000000',
    backgroundColor: dark ? '#1c2029' : '#ffffff',
    fail: () => {},
  })
  uni.setTabBarStyle({
    color: dark ? '#6f7785' : '#8a919e',
    selectedColor: dark ? '#6b93ff' : '#3a6cf4',
    backgroundColor: dark ? '#1c2029' : '#ffffff',
    borderStyle: dark ? 'black' : 'white',
    fail: () => {},
  })
}

/** 页面根节点的 class：`<view class="page-root" :class="themeClass">`；同时让导航栏跟着变色 */
export function useTheme() {
  const settings = useSettingsStore()
  const { themeClass, dark } = storeToRefs(settings)
  onShow(() => applyChrome(dark.value))
  watch(dark, (v) => applyChrome(v))
  return { themeClass }
}
