/**
 * App 端才有的能力：本地通知（学习提醒）、版本检查。普通浏览器里这些函数什么都不做；
 * 在 Android 网页套壳里（User-Agent 带标记）只做版本检查，比的是套壳安装包的版本。
 * 用条件编译（#ifdef APP-PLUS / H5）隔开，H5 包里不会带 plus 相关代码。
 */
import { http } from '@/api/http'
import { upcomingReminders } from '@/logic/reminder'
import { parseShellVersion } from '@/logic/shell'
import { updateKind } from '@/logic/version'

/** 是否是 App（能用本地通知、能检查版本） */
export function isApp(): boolean {
  let app = false
  // #ifdef APP-PLUS
  app = true
  // #endif
  return app
}

const REMINDER_TEXT = '今天的题还没刷，来做几道吧'

/**
 * 重新排学习提醒：清掉旧的，再排接下来 7 天每天一条（系统本地通知不支持每天重复）。
 * 每次打开 App、改提醒设置时调用；关掉提醒时传 on=false 只清除。
 */
export function scheduleReminders(on: boolean, time: string): number {
  let count = 0
  // #ifdef APP-PLUS
  if (typeof plus === 'undefined' || !plus.push) return 0
  plus.push.clear()
  if (!on) return 0
  const now = Date.now()
  for (const at of upcomingReminders(new Date(now), time, 7)) {
    const delay = Math.round((at.getTime() - now) / 1000)
    plus.push.createMessage(REMINDER_TEXT, 'study-reminder', { title: '码上刷题', delay, cover: false, when: at })
    count++
  }
  // #endif
  return count
}

interface VersionInfo {
  platform: string
  latestVersion: string | null
  minVersion: string | null
  downloadUrl: string | null
  notes: string | null
}

/** 在 Android 网页套壳里打开时返回套壳的版本号，否则 null（App 端、普通浏览器都是 null） */
export function shellVersion(): string | null {
  let v: string | null = null
  // #ifdef H5
  v = parseShellVersion(typeof navigator !== 'undefined' ? navigator.userAgent : '')
  // #endif
  return v
}

/** 当前 App 版本：App 端取 plus.runtime.version，套壳取 User-Agent 里的版本，普通 H5 返回 manifest 里的版本 */
export function currentVersion(): string {
  let v = '1.0.0'
  // #ifdef APP-PLUS
  if (typeof plus !== 'undefined' && plus.runtime?.version) v = plus.runtime.version
  // #endif
  // #ifdef H5
  v = shellVersion() ?? v
  // #endif
  return v
}

/** 打开安装包下载地址：App 端用系统浏览器；套壳里直接跳转，套壳会把下载链接交给系统浏览器 */
function openDownload(url: string) {
  // #ifdef APP-PLUS
  plus.runtime.openURL(url)
  // #endif
  // #ifdef H5
  window.location.href = url
  // #endif
}

/**
 * 检查新版本（App 端和 Android 套壳）：低于最低版本必须升级，低于最新版本提示。
 * 套壳的网页内容总是最新的，这里比的是安装包本身的版本。
 * manual=true 时（设置页点「检查更新」）没有新版本也提示一声。
 */
export async function checkUpdate(manual = false): Promise<void> {
  const inShell = shellVersion() !== null
  if (!isApp() && !inShell) {
    if (manual) uni.showToast({ title: '网页版总是最新，不需要更新', icon: 'none' })
    return
  }
  const platform = !inShell && uni.getSystemInfoSync().platform === 'ios' ? 'ios' : 'android'
  let info: VersionInfo
  try {
    info = await http.get<VersionInfo>('/app/version', { platform }, { silent: !manual, anonymous: true })
  } catch {
    return
  }
  const current = currentVersion()
  const kind = updateKind(current, info.latestVersion, info.minVersion)
  if (kind === 'none') {
    if (manual) uni.showToast({ title: `已是最新版本 ${current}`, icon: 'none' })
    return
  }
  const notes = info.notes ? `\n${info.notes}` : ''
  uni.showModal({
    title: kind === 'force' ? '需要更新后才能继续使用' : `发现新版本 ${info.latestVersion}`,
    content: `当前版本 ${current}。${notes}`,
    showCancel: kind !== 'force',
    confirmText: info.downloadUrl ? '去更新' : '知道了',
    cancelText: '以后再说',
    success: (res) => {
      if (res.confirm && info.downloadUrl) openDownload(info.downloadUrl)
    },
  })
}
