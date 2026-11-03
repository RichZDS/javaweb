/**
 * Android 网页套壳（仓库 android/）在 User-Agent 末尾加的标记，形如 `MaShangShuaTiApp/1.0.0`。
 * 网页版靠它知道自己是在套壳里打开的：这时不提示「去手机 App 里打开」，检查更新比的是套壳的版本。
 */
export const SHELL_UA_MARK = 'MaShangShuaTiApp'

const SHELL_UA_RE = new RegExp(`(?:^|\\s)${SHELL_UA_MARK}/(\\d+(?:\\.\\d+)*)(?=\\s|$)`)

/** 从 User-Agent 里取套壳版本号；不是套壳返回 null */
export function parseShellVersion(ua: string | null | undefined): string | null {
  const m = SHELL_UA_RE.exec(ua ?? '')
  return m ? m[1] : null
}
