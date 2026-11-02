import { describe, expect, it } from 'vitest'
import { chapterData, examScoreData, integerSplit, lastSegment, paperOptions, shortDate, toPercent, trendData } from '@/logic/stats'
import type { ExamScoreStat } from '@/api/types'

describe('统计数据整理', () => {
  it('比率转百分数，null 保持 null', () => {
    expect(toPercent(2 / 3)).toBe(66.7)
    expect(toPercent(0)).toBe(0)
    expect(toPercent(null)).toBeNull()
  })

  it('日期和路径缩短', () => {
    expect(shortDate('2026-11-02')).toBe('11-02')
    expect(lastSegment('考研 408 / 数据结构 / 线性表')).toBe('线性表')
  })

  it('趋势：没作答的日子正确率为 null，不画成 0', () => {
    const t = trendData([
      { date: '2026-11-01', answerCount: 0, correctCount: 0, correctRate: null },
      { date: '2026-11-02', answerCount: 4, correctCount: 3, correctRate: 0.75 },
    ])
    expect(t).toEqual({ categories: ['11-01', '11-02'], answers: [0, 4], rates: [null, 75], total: 4 })
  })

  it('章节：只画做过的章节', () => {
    const c = chapterData([
      { categoryId: 1, categoryPath: 'a / b / 线性表', questionCount: 5, doneCount: 2, answerCount: 3, correctRate: 2 / 3 },
      { categoryId: 2, categoryPath: 'a / b / 树', questionCount: 9, doneCount: 0, answerCount: 0, correctRate: null },
    ])
    expect(c).toEqual({ categories: ['线性表'], rates: [66.7], skipped: 1 })
  })

  it('考试分数：按试卷分开，按交卷先后，及格线取该卷', () => {
    const rows: ExamScoreStat[] = [
      { recordId: 3, paperId: 1, title: '卷一', score: 80, totalScore: 100, passScore: 60, rate: 0.8, submittedAt: '2026-11-03T00:00:00Z' },
      { recordId: 1, paperId: 1, title: '卷一', score: 55, totalScore: 100, passScore: 60, rate: 0.55, submittedAt: '2026-11-01T00:00:00Z' },
      { recordId: 2, paperId: 2, title: '卷二', score: 40, totalScore: 50, passScore: 30, rate: 0.8, submittedAt: '2026-11-04T00:00:00Z' },
    ]
    expect(paperOptions(rows)).toEqual([
      { paperId: 2, title: '卷二' },
      { paperId: 1, title: '卷一' },
    ])
    expect(examScoreData(rows, 1)).toEqual({ categories: ['第1次', '第2次'], scores: [55, 80], passScore: 60, totalScore: 100 })
    expect(examScoreData(rows, 9).passScore).toBeNull()
  })

  it('纵轴分段让刻度都是整数', () => {
    expect(integerSplit(100)).toBe(5)
    expect(integerSplit(4)).toBe(4)
    expect(integerSplit(12)).toBe(4)
    expect(integerSplit(7)).toBe(5)
    expect(integerSplit(3)).toBe(3)
    expect(integerSplit(null)).toBe(5)
  })
})
