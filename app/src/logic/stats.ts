/** 统计图表的数据整理：只做格式转换（日期标签、比率转百分数），口径全部来自后端。 */
import type { CategoryStat, DailyStat, ExamScoreStat } from '@/api/types'

/** 0~1 → 0~100，保留 1 位小数；null 保持 null（图上断开，不画成 0） */
export function toPercent(rate: number | null | undefined): number | null {
  if (rate === null || rate === undefined || Number.isNaN(rate)) return null
  return Math.round(rate * 1000) / 10
}

/** `2026-11-02` → `11-02` */
export function shortDate(date: string): string {
  const m = /^\d{4}-(\d{2})-(\d{2})/.exec(date)
  return m ? `${m[1]}-${m[2]}` : date
}

/** 「408 / 数据结构 / 线性表」→「线性表」 */
export function lastSegment(path: string): string {
  const parts = path.split('/').map((s) => s.trim()).filter(Boolean)
  return parts[parts.length - 1] ?? path
}

/** 近 N 天趋势：作答次数 + 正确率（%），没作答的日子正确率为 null */
export function trendData(daily: readonly DailyStat[]) {
  return {
    categories: daily.map((d) => shortDate(d.date)),
    answers: daily.map((d) => d.answerCount),
    rates: daily.map((d) => toPercent(d.correctRate)),
    total: daily.reduce((s, d) => s + d.answerCount, 0),
  }
}

/** 章节正确率条形图：只画做过的章节，按后端给的顺序 */
export function chapterData(stats: readonly CategoryStat[]) {
  const done = stats.filter((s) => s.answerCount > 0 && s.correctRate !== null)
  return {
    categories: done.map((s) => lastSegment(s.categoryPath)),
    rates: done.map((s) => toPercent(s.correctRate) ?? 0),
    skipped: stats.length - done.length,
  }
}

export interface PaperOption {
  paperId: number
  title: string
}

/** 有成绩的试卷列表（去重，按最近一次考试倒序） */
export function paperOptions(list: readonly ExamScoreStat[]): PaperOption[] {
  const sorted = [...list].sort((a, b) => Date.parse(b.submittedAt) - Date.parse(a.submittedAt))
  const seen = new Set<number>()
  const out: PaperOption[] = []
  for (const r of sorted) {
    if (seen.has(r.paperId)) continue
    seen.add(r.paperId)
    out.push({ paperId: r.paperId, title: r.title })
  }
  return out
}

/** 某份试卷的分数折线：按交卷时间先后，及格线取该卷的 passScore */
export function examScoreData(list: readonly ExamScoreStat[], paperId: number) {
  const rows = list
    .filter((r) => r.paperId === paperId)
    .sort((a, b) => Date.parse(a.submittedAt) - Date.parse(b.submittedAt))
  return {
    categories: rows.map((_, i) => `第${i + 1}次`),
    scores: rows.map((r) => r.score),
    passScore: rows.length ? rows[rows.length - 1].passScore : null,
    totalScore: rows.length ? rows[rows.length - 1].totalScore : null,
  }
}

/** 纵轴分几段，能让满分整除就用它，刻度都是整数（满分 100 → 5 段，满分 4 → 4 段） */
export function integerSplit(total: number | null | undefined): number {
  const t = Math.round(total ?? 0)
  if (t <= 0) return 5
  for (const n of [5, 4, 6, 3, 2]) if (t % n === 0 && t / n >= 1) return n
  return t < 5 ? t : 5
}
