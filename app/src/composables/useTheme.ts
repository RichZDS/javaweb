import { watch } from 'vue'
import { storeToRefs } from 'pinia'
import { onReady, onShow } from '@dcloudio/uni-app'
import { useSettingsStore } from '@/stores/settings'

/**
 * 原生导航栏和底部页签跟着夜间模式变色（不是页签页时 setTabBarStyle 会失败，忽略即可）。
 * H5 上切页签时这两个接口的生效时机不稳定，另外给根节点挂 quiz-dark，由 App.vue 里的样式覆盖导航栏和页签栏。
 */
export function applyChrome(dark: boolean): void {
  // #ifdef H5
  document.documentElement.classList.toggle('quiz-dark', dark)
  // #endif
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
  // H5 上页面切换时 onShow 早于新页面成为「当前页」，所以 onReady 和下一轮事件循环里再设一次
  const apply = () => {
    applyChrome(dark.value)
    setTimeout(() => applyChrome(dark.value), 0)
  }
  onShow(apply)
  onReady(apply)
  watch(dark, apply)
  return { themeClass }
}
