import { describe, expect, it } from 'vitest'
import {
  addOption,
  buildQuestionBody,
  changeType,
  emptyQuestionForm,
  formFromQuestion,
  removeOption,
  validateQuestionForm,
  type QuestionForm,
} from '@/logic/question'
import type { AdminQuestion } from '@/api/types'

function choice(overrides: Partial<QuestionForm> = {}): QuestionForm {
  const f = emptyQuestionForm(12)
  f.stem = '题干'
  f.options = ['甲', '乙', '丙', '丁'].map((text, i) => ({ key: 'ABCD'[i], text }))
  f.analysis = '解析'
  return { ...f, ...overrides }
}

describe('选项增删', () => {
  it('删掉中间的选项后 key 依次前移，正确答案跟着改', () => {
    const f = removeOption(choice({ type: 'MULTIPLE', correct: ['B', 'D'] }), 1)
    expect(f.options.map((o) => `${o.key}${o.text}`)).toEqual(['A甲', 'B丙', 'C丁'])
    expect(f.correct).toEqual(['C'])
  })

  it('最少 2 个、最多 6 个', () => {
    let f = choice()
    f = removeOption(removeOption(f, 0), 0)
    expect(f.options).toHaveLength(2)
    expect(removeOption(f, 0).options).toHaveLength(2)
    for (let i = 0; i < 10; i++) f = addOption(f)
    expect(f.options.map((o) => o.key)).toEqual(['A', 'B', 'C', 'D', 'E', 'F'])
  })
})

describe('切换题型', () => {
  it('多选切单选只保留一个答案；切判断、填空清空答案', () => {
    const multi = choice({ type: 'MULTIPLE', correct: ['A', 'C'] })
    expect(changeType(multi, 'SINGLE').correct).toEqual(['A'])
    expect(changeType(multi, 'JUDGE').correct).toEqual([])
    const fill = changeType(multi, 'FILL')
    expect(fill.correct).toEqual([])
    expect(fill.blanks.length).toBeGreaterThan(0)
  })
})

describe('校验', () => {
  it('发布的单选题：要 1 个答案、要解析', () => {
    expect(validateQuestionForm(choice({ status: 'PUBLISHED', correct: [] }))).toContain('单选题要选 1 个正确答案')
    expect(validateQuestionForm(choice({ status: 'PUBLISHED', correct: ['A'], analysis: ' ' }))).toContain('发布前请填写解析')
    expect(validateQuestionForm(choice({ status: 'PUBLISHED', correct: ['A'] }))).toEqual([])
  })

  it('多选至少 2 个答案；草稿可以先不填答案', () => {
    expect(validateQuestionForm(choice({ type: 'MULTIPLE', status: 'PUBLISHED', correct: ['A'] }))).toContain('多选题至少选 2 个正确答案')
    expect(validateQuestionForm(choice({ type: 'MULTIPLE', status: 'DRAFT', correct: [] }))).toEqual([])
  })

  it('空选项、没选章节、没题干都报出来', () => {
    const f = choice({ categoryId: null, stem: '' })
    f.options[2].text = ''
    const errors = validateQuestionForm(f)
    expect(errors).toEqual(expect.arrayContaining(['请选择章节（第三级分类）', '请填写题干', '选项 C 不能为空']))
  })

  it('填空：发布时每空都要有答案', () => {
    const f = { ...choice({ type: 'FILL', status: 'PUBLISHED' }), blanks: [['int'], []] }
    expect(validateQuestionForm(f)).toContain('第 2 空还没有可接受的答案')
  })
})

describe('请求体', () => {
  it('单选、多选：带 options，答案按选项顺序，填空字段为 null', () => {
    const body = buildQuestionBody(choice({ type: 'MULTIPLE', correct: ['C', 'A'], status: 'PUBLISHED' }))
    expect(body.options).toEqual([
      { key: 'A', text: '甲' },
      { key: 'B', text: '乙' },
      { key: 'C', text: '丙' },
      { key: 'D', text: '丁' },
    ])
    expect(body.correctAnswer).toEqual(['A', 'C'])
    expect(body.acceptedAnswers).toBeNull()
    expect(body.fillOptions).toBeNull()
  })

  it('判断：不传 options', () => {
    const body = buildQuestionBody({ ...choice({ type: 'JUDGE' }), options: [], correct: ['F'] })
    expect('options' in body).toBe(false)
    expect(body.correctAnswer).toEqual(['F'])
  })

  it('填空：不传 options，答案去空白去重，带 fillOptions', () => {
    const f = { ...choice({ type: 'FILL', ignoreCase: true }), blanks: [[' int ', 'Integer', 'int'], ['5']] }
    const body = buildQuestionBody(f)
    expect('options' in body).toBe(false)
    expect(body.correctAnswer).toBeNull()
    expect(body.acceptedAnswers).toEqual([['int', 'Integer'], ['5']])
    expect(body.fillOptions).toEqual({ ignoreCase: true, ignoreSpaces: false })
  })

  it('数字字段是原生数字，标签去重去空白', () => {
    const f = choice({ correct: ['A'] })
    f.categoryId = '12' as unknown as number
    f.difficulty = '4' as unknown as number
    f.tags = [' 链表 ', '链表', '']
    const body = buildQuestionBody(f)
    expect(body.categoryId).toBe(12)
    expect(body.difficulty).toBe(4)
    expect(body.tags).toEqual(['链表'])
    expect(body.source).toBe('原创')
  })

  it('新建题不填排序时不传 sort（后端排到章节末尾）', () => {
    expect('sort' in buildQuestionBody(choice({ correct: ['A'] }))).toBe(false)
    expect(buildQuestionBody(choice({ correct: ['A'], sort: 5 })).sort).toBe(5)
  })

  it('编辑回填：接口数据 → 表单 → 请求体保持一致', () => {
    const q = {
      id: 7, categoryId: 12, categoryPath: 'a/b/c', type: 'FILL', stem: 's', options: [], correctAnswer: null,
      acceptedAnswers: [['a', 'b']], fillOptions: { ignoreCase: false, ignoreSpaces: true }, analysis: 'x',
      difficulty: 2, tags: ['t'], imageUrl: '/uploads/1.png', source: '原创', sort: 3, status: 'PUBLISHED',
      blankCount: 1, version: 2, answerCount: 0, correctRate: null, createdAt: '', updatedAt: '',
    } as unknown as AdminQuestion
    const body = buildQuestionBody(formFromQuestion(q))
    expect(body).toMatchObject({ categoryId: 12, type: 'FILL', acceptedAnswers: [['a', 'b']], fillOptions: { ignoreCase: false, ignoreSpaces: true }, imageUrl: '/uploads/1.png', sort: 3, status: 'PUBLISHED' })
  })
})
