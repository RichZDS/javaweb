import { describe, expect, it } from 'vitest'
import { addQuestions, buildPaperBody, emptyPaperForm, isStructureLocked, moveQuestion, previewTotal, validatePaperForm } from '@/logic/paper'

const fixed = () => ({
  ...emptyPaperForm(1),
  title: '卷一',
  passScore: 6,
  questions: [
    { questionId: 11, score: 5, sort: 1 },
    { questionId: 12, score: 5, sort: 2 },
  ],
})

describe('试卷表单', () => {
  it('预计总分：固定卷相加，规则卷 题数 × 分值', () => {
    expect(previewTotal(fixed())).toBe(10)
    const rule = { ...emptyPaperForm(1), mode: 'RULE' as const, rules: [{ categoryId: 2, type: 'SINGLE' as const, count: 10, score: 2 }, { categoryId: 3, type: 'FILL' as const, count: 2, score: 5 }] }
    expect(previewTotal(rule)).toBe(30)
  })

  it('挑题：已在卷里的跳过，新题排在最后', () => {
    const out = addQuestions(fixed().questions, [{ id: 12, type: 'SINGLE', stemPreview: 'x' }, { id: 13, type: 'JUDGE', stemPreview: 'y' }], 2)
    expect(out.map((q) => [q.questionId, q.score])).toEqual([[11, 5], [12, 5], [13, 2]])
  })

  it('上移下移，越界不动', () => {
    const qs = fixed().questions
    expect(moveQuestion(qs, 1, -1).map((q) => q.questionId)).toEqual([12, 11])
    expect(moveQuestion(qs, 0, -1).map((q) => q.questionId)).toEqual([11, 12])
  })

  it('请求体：sort 按当前顺序重排，数字是原生数字', () => {
    const f = fixed()
    f.questions = moveQuestion(f.questions, 1, -1)
    f.durationMin = '45' as unknown as number
    const body = buildPaperBody(f)
    expect(body.questions).toEqual([
      { questionId: 12, score: 5, sort: 1 },
      { questionId: 11, score: 5, sort: 2 },
    ])
    expect(body.durationMin).toBe(45)
    expect(body.rules).toBeUndefined()
  })

  it('校验：及格分高于总分、分值不是正整数、规则缺分类', () => {
    expect(validatePaperForm({ ...fixed(), passScore: 11 })).toContain('及格分（11）不能高于预计总分（10）')
    const bad = fixed()
    bad.questions[0].score = 2.5
    expect(validatePaperForm(bad)).toContain('每道题的分值是正整数')
    const rule = { ...emptyPaperForm(1), title: 'r', mode: 'RULE' as const, rules: [{ categoryId: null, type: 'SINGLE' as const, count: 0, score: 2 }] }
    expect(validatePaperForm(rule)).toEqual(expect.arrayContaining(['第 1 条规则没选分类', '第 1 条规则的题数要是正整数']))
    expect(validatePaperForm(fixed())).toEqual([])
  })

  it('有人考过：只校验标题', () => {
    expect(isStructureLocked({ attempts: 3 })).toBe(true)
    expect(isStructureLocked({ attempts: 0 })).toBe(false)
    expect(validatePaperForm({ ...fixed(), passScore: 999 }, true)).toEqual([])
  })
})
