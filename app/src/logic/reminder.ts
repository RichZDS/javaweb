/**
 * 学习提醒（本地通知）的时间计算。
 * 系统本地通知不支持「每天重复」，所以每次打开 App 或改设置时，把接下来几天的提醒一次排好。
 */

/** `20:30` → {h:20, m:30}；格式不对返回 null */
export function parseTime(text: string): { h: number; m: number } | null {
  const m = /^(\d{1,2}):(\d{2})$/.exec(String(text ?? '').trim())
  if (!m) return null
  const h = Number(m[1])
  const min = Number(m[2])
  if (h > 23 || min > 59) return null
  return { h, m: min }
}

/**
 * 从 now 起接下来 days 次提醒的时间（本机时区）。
 * 今天的时间还没到就从今天开始，过了就从明天开始。
 */
export function upcomingReminders(now: Date, time: string, days = 7): Date[] {
  const t = parseTime(time)
  if (!t || days <= 0) return []
  const first = new Date(now.getFullYear(), now.getMonth(), now.getDate(), t.h, t.m, 0, 0)
  if (first.getTime() <= now.getTime()) first.setDate(first.getDate() + 1)
  return Array.from({ length: days }, (_, i) => {
    const d = new Date(first)
    d.setDate(first.getDate() + i)
    return d
  })
}
