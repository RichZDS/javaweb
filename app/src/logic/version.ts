/** App 版本比较（1.2.10 > 1.2.9），用于启动时检查新版本。 */

/** a > b 返回 1，相等 0，小于 -1；缺的段按 0，非数字段按 0 */
export function compareVersion(a: string, b: string): number {
  const pa = String(a ?? '').split('.').map((x) => parseInt(x, 10) || 0)
  const pb = String(b ?? '').split('.').map((x) => parseInt(x, 10) || 0)
  const n = Math.max(pa.length, pb.length)
  for (let i = 0; i < n; i++) {
    const d = (pa[i] ?? 0) - (pb[i] ?? 0)
    if (d !== 0) return d > 0 ? 1 : -1
  }
  return 0
}

export type UpdateKind = 'none' | 'optional' | 'force'

/**
 * 当前版本低于 minVersion：必须升级；低于 latestVersion：提示可升级；否则不提示。
 * 后端没配下载地址时只提示、不给跳转（由调用方处理）。
 */
export function updateKind(current: string, latest: string | null | undefined, min: string | null | undefined): UpdateKind {
  if (min && compareVersion(current, min) < 0) return 'force'
  if (latest && compareVersion(current, latest) < 0) return 'optional'
  return 'none'
}
