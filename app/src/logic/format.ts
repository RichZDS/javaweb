/** 展示格式化：比率、时长、时间、HTML 摘要。纯函数。 */

/** 0~1 的比率 → `67%`；null / 非数字显示「—」（没作答不能写成 0%） */
export function formatRate(rate: number | null | undefined, digits = 0): string {
  if (rate === null || rate === undefined || Number.isNaN(rate)) return '—'
  return `${(rate * 100).toFixed(digits)}%`
}

/** 秒 → `1小时02分` / `12分05秒` / `45秒` */
export function formatDuration(totalSec: number | null | undefined): string {
  const sec = Math.max(0, Math.round(totalSec ?? 0))
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  const s = sec % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  if (h > 0) return `${h}小时${pad(m)}分`
  if (m > 0) return `${m}分${pad(s)}秒`
  return `${s}秒`
}

/** 倒计时 `mm:ss`，超过 1 小时为 `h:mm:ss` */
export function formatClock(totalSec: number): string {
  const sec = Math.max(0, Math.floor(totalSec))
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  const s = sec % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`
}

const BEIJING_OFFSET_MS = 8 * 3600 * 1000

/**
 * UTC 时间串 → 北京时间 `MM-DD HH:mm`（跨年时带年份）。
 * 后端时间一律 UTC，「哪一天」按 Asia/Shanghai 算，展示也统一用北京时间，避免手机时区不同显示不一。
 */
export function formatDateTime(iso: string | null | undefined, now: Date = new Date()): string {
  if (!iso) return ''
  const t = Date.parse(iso)
  if (Number.isNaN(t)) return ''
  const d = new Date(t + BEIJING_OFFSET_MS)
  const n = new Date(now.getTime() + BEIJING_OFFSET_MS)
  const pad = (x: number) => String(x).padStart(2, '0')
  const md = `${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())} ${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}`
  return d.getUTCFullYear() === n.getUTCFullYear() ? md : `${d.getUTCFullYear()}-${md}`
}

/** 去掉 HTML 标签和多余空白，取前 n 个字，用于列表摘要 */
export function htmlToPreview(html: string | null | undefined, max = 60): string {
  if (!html) return ''
  const text = html
    .replace(/<style[\s\S]*?<\/style>/gi, '')
    .replace(/<script[\s\S]*?<\/script>/gi, '')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/g, ' ')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&amp;/g, '&')
    .replace(/\s+/g, ' ')
    .trim()
  return text.length > max ? `${text.slice(0, max)}…` : text
}
