/** 页面路径和跳转守卫 */
import { useUserStore } from '@/stores/user'

export const PAGES = {
  home: '/pages/index/index',
  exam: '/pages/exam/index',
  stats: '/pages/stats/index',
  me: '/pages/me/index',
  login: '/pages/login/login',
  register: '/pages/register/register',
  target: '/pages/target/target',
  practiceSetup: '/pages/practice/setup',
  practiceAnswer: '/pages/practice/answer',
  practiceResult: '/pages/practice/result',
  wrong: '/pages/wrong/index',
  question: '/pages/question/detail',
  search: '/pages/search/index',
  examNotice: '/pages/exam/notice',
  examAnswer: '/pages/exam/answer',
  examResult: '/pages/exam/result',
  examRecords: '/pages/exam/records',
  favorites: '/pages/favorite/index',
  practiceRecords: '/pages/practice/records',
  leaderboard: '/pages/stats/leaderboard',
} as const

let redirecting = false

/** 去登录页（清掉页面栈）；连续多个 401 只跳一次 */
export function goLogin(message?: string): void {
  if (redirecting) return
  redirecting = true
  if (message) uni.showToast({ title: message, icon: 'none', duration: 2000 })
  setTimeout(() => {
    uni.reLaunch({ url: PAGES.login, complete: () => setTimeout(() => (redirecting = false), 500) })
  }, message ? 600 : 0)
}

/**
 * 页签页在 onShow 里调用：没登录去登录页，没选目标考试去选（首次登录强制选）。
 * 返回 true 表示可以继续加载本页数据。
 */
export function ensureReady(): boolean {
  const user = useUserStore()
  if (!user.loggedIn) {
    goLogin()
    return false
  }
  if (!user.targetCategoryId) {
    uni.reLaunch({ url: `${PAGES.target}?first=1` })
    return false
  }
  return true
}

/** 登录或注册成功后的去处 */
export function afterLogin(): void {
  const user = useUserStore()
  if (!user.targetCategoryId) uni.reLaunch({ url: `${PAGES.target}?first=1` })
  else uni.switchTab({ url: PAGES.home })
}
