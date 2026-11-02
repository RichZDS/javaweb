import { describe, expect, it } from 'vitest'
import {
  cardStatus,
  emptyAnswer,
  formatAccepted,
  formatAnswer,
  formatCorrect,
  hasAnyAnswer,
  isComplete,
  setBlank,
  toSubmitAnswer,
  toggleChoice,
} from '@/logic/answer'

const OPTS = ['A', 'B', 'C', 'D'].map((key) => ({ key, text: key }))

describe('toggleChoice', () => {
  it('单选和判断：点哪个就是哪个', () => {
    expect(toggleChoice('SINGLE', ['A'], 'C', OPTS)).toEqual(['C'])
    expect(toggleChoice('JUDGE', [], 'F')).toEqual(['F'])
  })

  it('多选：切换选中，并按选项顺序排列', () => {
    let a = toggleChoice('MULTIPLE', [], 'C', OPTS)
    a = toggleChoice('MULTIPLE', a, 'A', OPTS)
    expect(a).toEqual(['A', 'C'])
    expect(toggleChoice('MULTIPLE', a, 'C', OPTS)).toEqual(['A'])
  })

  it('不修改传入的数组', () => {
    const before = ['A']
    toggleChoice('MULTIPLE', before, 'B', OPTS)
    expect(before).toEqual(['A'])
  })
})

describe('填空', () => {
  it('空作答按空数生成', () => {
    expect(emptyAnswer('FILL', 2)).toEqual(['', ''])
    expect(emptyAnswer('FILL', 0)).toEqual([''])
    expect(emptyAnswer('SINGLE')).toEqual([])
  })

  it('setBlank 只改一个空并补齐长度', () => {
    expect(setBlank([], 1, '5', 2)).toEqual(['', '5'])
    expect(setBlank(['int', ''], 0, 'Integer', 2)).toEqual(['Integer', ''])
  })

  it('提交时截到空数，不 trim（空白处理交给后端）', () => {
    expect(toSubmitAnswer('FILL', [' Java ', '5', 'extra'], 2)).toEqual([' Java ', '5'])
    expect(toSubmitAnswer('FILL', ['x'], 2)).toEqual(['x', ''])
  })

  it('多行输出原样保留换行', () => {
    expect(toSubmitAnswer('FILL', ['1\r\n2'], 1)).toEqual(['1\r\n2'])
  })
})

describe('能否提交', () => {
  it('没选 / 全是空白不能提交', () => {
    expect(hasAnyAnswer('SINGLE', [])).toBe(false)
    expect(hasAnyAnswer('FILL', ['  ', ''])).toBe(false)
    expect(hasAnyAnswer('FILL', ['', 'x'])).toBe(true)
    expect(hasAnyAnswer('MULTIPLE', null)).toBe(false)
  })

  it('isComplete：填空每空都要有内容', () => {
    expect(isComplete('FILL', ['a', ''], 2)).toBe(false)
    expect(isComplete('FILL', ['a', 'b'], 2)).toBe(true)
    expect(isComplete('SINGLE', ['A'])).toBe(true)
    expect(isComplete('MULTIPLE', ['A'])).toBe(true)
  })

  it('选择题提交时去重', () => {
    expect(toSubmitAnswer('MULTIPLE', ['A', 'A', 'C'])).toEqual(['A', 'C'])
  })

  it('每一项都是字符串（后端严格校验）', () => {
    expect(toSubmitAnswer('FILL', [5 as unknown as string, null as unknown as string], 2)).toEqual(['5', ''])
    expect(toSubmitAnswer('SINGLE', [1 as unknown as string])).toEqual(['1'])
  })
})

describe('展示格式', () => {
  it('选择、判断、填空', () => {
    expect(formatAnswer('MULTIPLE', ['A', 'C'])).toBe('A、C')
    expect(formatAnswer('JUDGE', ['T'])).toBe('正确')
    expect(formatAnswer('JUDGE', ['F'])).toBe('错误')
    expect(formatAnswer('FILL', ['int'])).toBe('int')
    expect(formatAnswer('FILL', ['int', ''])).toBe('第1空 int；第2空 （空）')
    expect(formatAnswer('SINGLE', [])).toBe('未作答')
    expect(formatAnswer('SINGLE', null)).toBe('未作答')
  })

  it('填空标准答案列出每空的可接受答案', () => {
    expect(formatAccepted([['int', 'Integer'], ['5']])).toBe('第1空 int / Integer；第2空 5')
    expect(formatAccepted([['Java']])).toBe('Java')
    expect(formatAccepted(null)).toBe('')
  })

  it('formatCorrect 按题型取字段', () => {
    expect(formatCorrect('SINGLE', ['B'], null)).toBe('B')
    expect(formatCorrect('FILL', null, [['5']])).toBe('5')
  })
})

describe('答题卡状态', () => {
  it('练习：提交后按后端判定，未提交为未答', () => {
    expect(cardStatus({ submitted: true, correct: true })).toBe('correct')
    expect(cardStatus({ submitted: true, correct: false })).toBe('wrong')
    expect(cardStatus({ submitted: false, correct: null })).toBe('unanswered')
  })

  it('考试：没有判定，有作答即已答', () => {
    expect(cardStatus({ type: 'SINGLE', userAnswer: ['A'] })).toBe('answered')
    expect(cardStatus({ type: 'FILL', userAnswer: [' '] })).toBe('unanswered')
  })
})
