/**
 * 作答格式转换：界面上的点选 / 输入 ⇄ 接口里的 `userAnswer: string[]`。
 * 只管格式，不判对错（判分只在后端）。
 */
import type { QuestionOption, QuestionType } from '@/api/types'

export const TYPE_LABEL: Record<QuestionType, string> = {
  SINGLE: '单选',
  MULTIPLE: '多选',
  JUDGE: '判断',
  FILL: '填空',
}

/** 新题的空作答：填空题按空数给出等长的空串数组，其他题型是空数组 */
export function emptyAnswer(type: QuestionType, blankCount = 0): string[] {
  return type === 'FILL' ? Array.from({ length: Math.max(blankCount, 1) }, () => '') : []
}

/**
 * 点了一个选项之后的新作答。
 * 单选、判断：替换；多选：切换，并按选项原顺序排好（A、C 而不是 C、A）。
 */
export function toggleChoice(
  type: QuestionType,
  current: readonly string[],
  key: string,
  options: readonly QuestionOption[] = [],
): string[] {
  if (type === 'SINGLE' || type === 'JUDGE') return [key]
  if (type !== 'MULTIPLE') return [...current]
  const set = new Set(current)
  if (set.has(key)) set.delete(key)
  else set.add(key)
  const order = options.map((o) => o.key)
  return [...set].sort((a, b) => {
    const ia = order.indexOf(a)
    const ib = order.indexOf(b)
    if (ia === -1 || ib === -1) return a.localeCompare(b)
    return ia - ib
  })
}

/** 改第 index 个空，返回新数组；长度不足时补齐 */
export function setBlank(current: readonly string[], index: number, value: string, blankCount: number): string[] {
  const next = Array.from({ length: Math.max(blankCount, index + 1) }, (_, i) => current[i] ?? '')
  next[index] = value
  return next
}

/** 至少填了 / 选了一点东西，才允许提交 */
export function hasAnyAnswer(type: QuestionType, answer: readonly string[] | null | undefined): boolean {
  if (!answer || answer.length === 0) return false
  if (type === 'FILL') return answer.some((s) => s.trim() !== '')
  return true
}

/** 每个空都填了 / 选择题已选；用来在提交前提醒「还有空没填」 */
export function isComplete(type: QuestionType, answer: readonly string[] | null | undefined, blankCount = 0): boolean {
  if (!answer) return false
  if (type === 'FILL') {
    return answer.length >= Math.max(blankCount, 1) && answer.slice(0, Math.max(blankCount, 1)).every((s) => s.trim() !== '')
  }
  if (type === 'MULTIPLE') return answer.length >= 1
  return answer.length === 1
}

/** 提交前整理：选择题去重，填空题截到空数（不 trim，空白处理按后端规则来） */
export function toSubmitAnswer(type: QuestionType, answer: readonly string[], blankCount = 0): string[] {
  if (type === 'FILL') {
    const n = Math.max(blankCount, 1)
    return Array.from({ length: n }, (_, i) => answer[i] ?? '')
  }
  return [...new Set(answer)]
}

/** 展示用：`A、C` / `正确` / `第1空 int；第2空 5`；没作答时返回「未作答」 */
export function formatAnswer(type: QuestionType, answer: readonly string[] | null | undefined): string {
  if (!answer || !hasAnyAnswer(type, answer)) return '未作答'
  if (type === 'JUDGE') return answer.map((k) => (k === 'T' ? '正确' : k === 'F' ? '错误' : k)).join('、')
  if (type === 'FILL') {
    if (answer.length === 1) return answer[0].trim() === '' ? '（空）' : answer[0]
    return answer.map((s, i) => `第${i + 1}空 ${s.trim() === '' ? '（空）' : s}`).join('；')
  }
  return answer.join('、')
}

/** 填空题标准答案：每空的可接受答案用「/」隔开 */
export function formatAccepted(accepted: readonly (readonly string[])[] | null | undefined): string {
  if (!accepted || accepted.length === 0) return ''
  if (accepted.length === 1) return accepted[0].join(' / ')
  return accepted.map((alts, i) => `第${i + 1}空 ${alts.join(' / ')}`).join('；')
}

/** 标准答案的展示文字，按题型选 correctAnswer 或 acceptedAnswers */
export function formatCorrect(
  type: QuestionType,
  correctAnswer: readonly string[] | null | undefined,
  acceptedAnswers: readonly (readonly string[])[] | null | undefined,
): string {
  if (type === 'FILL') return formatAccepted(acceptedAnswers)
  return formatAnswer(type, correctAnswer ?? [])
}

export type CardStatus = 'correct' | 'wrong' | 'unanswered' | 'answered'

/** 答题卡上的文字，颜色之外再用文字区分，色弱也能看清 */
export const CARD_LABEL: Record<CardStatus, string> = {
  correct: '对',
  wrong: '错',
  unanswered: '未答',
  answered: '已答',
}

/**
 * 答题卡格子状态。
 * 练习：提交后后端给了 correct，就是对 / 错；没提交是未答。
 * 考试（没有判定）：有作答记为已答。
 */
export function cardStatus(item: {
  submitted?: boolean
  correct?: boolean | null
  type?: QuestionType
  userAnswer?: readonly string[] | null
}): CardStatus {
  if (item.submitted) {
    if (item.correct === true) return 'correct'
    if (item.correct === false) return 'wrong'
    return 'answered'
  }
  if (item.type && item.userAnswer && hasAnyAnswer(item.type, item.userAnswer)) return 'answered'
  return 'unanswered'
}
