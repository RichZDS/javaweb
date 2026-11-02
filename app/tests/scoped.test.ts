import { describe, expect, it } from 'vitest'
import { collectCategoryIds, finishLoad, sessionInExam, startLoad, viewOf, type Slot } from '@/logic/scoped'

const ok = <T>(data: T) => ({ ok: true as const, data })
const fail = (error: unknown) => ({ ok: false as const, error })

describe('按目标考试分开存的数据', () => {
  it('切到别的考试：从空开始，不带上一份考试的数据', () => {
    let s: Slot<string[]> | null = finishLoad(startLoad(null, 18), 18, ok(['Java 薄弱点']))
    s = startLoad(s, 39)
    expect(viewOf(s, 39)).toEqual({ status: 'loading', data: null, error: null })
  })

  it('新考试的请求失败：显示失败，不显示旧考试的数据（PM-M3-05）', () => {
    let s: Slot<string[]> | null = finishLoad(startLoad(null, 18), 18, ok(['Java 薄弱点']))
    s = finishLoad(startLoad(s, 39), 39, fail('网络'))
    expect(viewOf(s, 39)).toEqual({ status: 'error', data: null, error: '网络' })
  })

  it('迟到的旧考试响应被丢弃', () => {
    const s1 = startLoad<string[]>(null, 18)
    const s2 = startLoad(s1, 39) // 用户已切到 39
    const late = finishLoad(s2, 18, ok(['Java 薄弱点']))
    expect(late).toBe(s2)
    expect(viewOf(late, 39).data).toBeNull()
  })

  it('同一考试刷新失败：保留这个考试已有的数据并标出错误', () => {
    let s: Slot<number> | null = finishLoad(startLoad(null, 1), 1, ok(5))
    s = finishLoad(startLoad(s, 1), 1, fail('500'))
    expect(viewOf(s, 1)).toEqual({ status: 'error', data: 5, error: '500' })
  })

  it('当前没有目标考试或 slot 属于别的考试：当作未加载', () => {
    const s = finishLoad(startLoad(null, 1), 1, ok(5))
    expect(viewOf(s, null).data).toBeNull()
    expect(viewOf(s, 2).data).toBeNull()
  })
})

describe('继续练习只认当前考试的练习', () => {
  const tree = { id: 1, children: [{ id: 2, children: [{ id: 3 }, { id: 4 }] }] }
  const ids = collectCategoryIds(tree)

  it('收集整棵树的 id', () => {
    expect([...ids].sort()).toEqual([1, 2, 3, 4])
  })

  it('题目都在这个考试里才算', () => {
    expect(sessionInExam({ questions: [{ categoryId: 3 }, { categoryId: 4 }] }, ids)).toBe(true)
    expect(sessionInExam({ questions: [{ categoryId: 3 }, { categoryId: 40 }] }, ids)).toBe(false)
    expect(sessionInExam({ questions: [{ categoryId: 40 }] }, ids)).toBe(false)
  })

  it('没有练习、没有题、还没拿到分类树：都不显示', () => {
    expect(sessionInExam(null, ids)).toBe(false)
    expect(sessionInExam({ questions: [] }, ids)).toBe(false)
    expect(sessionInExam({ questions: [{ categoryId: 3 }] }, new Set())).toBe(false)
  })
})
