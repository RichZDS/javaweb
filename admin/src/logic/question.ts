/**
 * 题目编辑表单 ⇄ 接口请求体（docs/api.md 9.1）。
 * - 单选、多选：options 2–6 项，key 按 A、B、C… 顺序；单选 1 个答案，多选至少 2 个
 * - 判断：options 不传，答案 T / F
 * - 填空：options 不传，acceptedAnswers 每空一个非空数组，fillOptions 默认两项都是 false
 * - 不适用的答案字段传 null；发布（PUBLISHED）时必须有答案和解析
 */
import type { AdminQuestion, QuestionBody, QuestionType } from '@/api/types'

export const OPTION_KEYS = ['A', 'B', 'C', 'D', 'E', 'F'] as const
export const MIN_OPTIONS = 2
export const MAX_OPTIONS = 6

export const TYPE_LABEL: Record<QuestionType, string> = { SINGLE: '单选', MULTIPLE: '多选', JUDGE: '判断', FILL: '填空' }

export interface QuestionForm {
  categoryId: number | null
  type: QuestionType
  stem: string
  /** 单选、多选的选项；key 总是按顺序 A、B、C… */
  options: { key: string; text: string }[]
  /** 单选、多选、判断的正确答案 key */
  correct: string[]
  /** 填空：每空的可接受答案列表 */
  blanks: string[][]
  ignoreCase: boolean
  ignoreSpaces: boolean
  analysis: string
  difficulty: number
  tags: string[]
  imageUrl: string | null
  source: string
  /** null：新建时不传，后端排到章节末尾 */
  sort: number | null
  status: 'DRAFT' | 'PUBLISHED'
}

const isChoice = (t: QuestionType) => t === 'SINGLE' || t === 'MULTIPLE'

function rekey(options: { text: string }[]): { key: string; text: string }[] {
  return options.map((o, i) => ({ key: OPTION_KEYS[i], text: o.text }))
}

export function emptyQuestionForm(categoryId: number | null = null): QuestionForm {
  return {
    categoryId,
    type: 'SINGLE',
    stem: '',
    options: rekey([{ text: '' }, { text: '' }, { text: '' }, { text: '' }]),
    correct: [],
    blanks: [[]],
    ignoreCase: false,
    ignoreSpaces: false,
    analysis: '',
    difficulty: 3,
    tags: [],
    imageUrl: null,
    source: '原创',
    sort: null,
    status: 'DRAFT',
  }
}

/** 编辑已有题目时把接口数据填进表单 */
export function formFromQuestion(q: AdminQuestion): QuestionForm {
  const form = emptyQuestionForm(q.categoryId)
  form.type = q.type
  form.stem = q.stem ?? ''
  form.options = isChoice(q.type) ? rekey((q.options ?? []).map((o) => ({ text: o.text ?? '' }))) : []
  form.correct = q.type === 'FILL' ? [] : [...(q.correctAnswer ?? [])]
  form.blanks = q.type === 'FILL' ? (q.acceptedAnswers ?? [[]]).map((alts) => [...alts]) : [[]]
  form.ignoreCase = q.fillOptions?.ignoreCase === true
  form.ignoreSpaces = q.fillOptions?.ignoreSpaces === true
  form.analysis = q.analysis ?? ''
  form.difficulty = Number(q.difficulty) || 3
  form.tags = [...(q.tags ?? [])]
  form.imageUrl = q.imageUrl ?? null
  form.source = q.source ?? '原创'
  form.sort = Number(q.sort) || 0
  form.status = q.status === 'PUBLISHED' ? 'PUBLISHED' : 'DRAFT'
  return form
}

/** 切换题型：清掉不适用的答案，选项不够时补齐 */
export function changeType(form: QuestionForm, type: QuestionType): QuestionForm {
  const next: QuestionForm = { ...form, type }
  if (isChoice(type)) {
    next.options = form.options.length >= MIN_OPTIONS ? rekey(form.options) : rekey([{ text: '' }, { text: '' }, { text: '' }, { text: '' }])
    const keys = new Set(next.options.map((o) => o.key))
    const kept = isChoice(form.type) ? form.correct.filter((k) => keys.has(k)) : []
    next.correct = type === 'SINGLE' ? kept.slice(0, 1) : kept
  } else if (type === 'JUDGE') {
    next.correct = form.type === 'JUDGE' ? form.correct.slice(0, 1) : []
  } else {
    next.correct = []
    if (!form.blanks.length) next.blanks = [[]]
  }
  return next
}

export function addOption(form: QuestionForm): QuestionForm {
  if (form.options.length >= MAX_OPTIONS) return form
  return { ...form, options: rekey([...form.options, { text: '' }]) }
}

/** 删掉一个选项：后面的 key 依次前移，正确答案同步改 key */
export function removeOption(form: QuestionForm, index: number): QuestionForm {
  if (form.options.length <= MIN_OPTIONS || index < 0 || index >= form.options.length) return form
  const removedKey = OPTION_KEYS[index]
  const options = rekey(form.options.filter((_, i) => i !== index))
  const correct = form.correct
    .filter((k) => k !== removedKey)
    .map((k) => {
      const i = OPTION_KEYS.indexOf(k as (typeof OPTION_KEYS)[number])
      return i > index ? OPTION_KEYS[i - 1] : k
    })
  return { ...form, options, correct }
}

function cleanList(values: readonly string[]): string[] {
  const out: string[] = []
  for (const v of values) {
    const t = String(v ?? '').trim()
    if (t && !out.includes(t)) out.push(t)
  }
  return out
}

/** 表单校验，返回错误列表（空数组表示可以提交） */
export function validateQuestionForm(form: QuestionForm): string[] {
  const errors: string[] = []
  const publishing = form.status === 'PUBLISHED'
  if (!form.categoryId) errors.push('请选择章节（第三级分类）')
  if (!form.stem.trim()) errors.push('请填写题干')
  if (isChoice(form.type)) {
    if (form.options.length < MIN_OPTIONS || form.options.length > MAX_OPTIONS) errors.push('选项需要 2–6 个')
    form.options.forEach((o, i) => {
      if (!o.text.trim()) errors.push(`选项 ${OPTION_KEYS[i]} 不能为空`)
    })
    const keys = new Set(form.options.map((o) => o.key))
    if (form.correct.some((k) => !keys.has(k))) errors.push('正确答案必须是已有的选项')
    if (form.type === 'SINGLE' && (form.correct.length > 1 || (publishing && form.correct.length !== 1))) {
      errors.push('单选题要选 1 个正确答案')
    }
    if (form.type === 'MULTIPLE' && (publishing || form.correct.length > 0) && form.correct.length < 2) {
      errors.push('多选题至少选 2 个正确答案')
    }
  } else if (form.type === 'JUDGE') {
    if (form.correct.some((k) => k !== 'T' && k !== 'F') || form.correct.length > 1) errors.push('判断题答案只能是「正确」或「错误」')
    if (publishing && form.correct.length !== 1) errors.push('判断题要选择正确或错误')
  } else {
    if (!form.blanks.length) errors.push('填空题至少要有 1 个空')
    const empty = form.blanks.findIndex((alts) => cleanList(alts).length === 0)
    if (empty >= 0 && (publishing || form.blanks.some((alts) => cleanList(alts).length > 0))) {
      errors.push(`第 ${empty + 1} 空还没有可接受的答案`)
    }
  }
  if (!Number.isInteger(form.difficulty) || form.difficulty < 1 || form.difficulty > 5) errors.push('难度为 1–5')
  if (publishing && !form.analysis.trim()) errors.push('发布前请填写解析')
  return errors
}

/** 表单 → 请求体；数字一律原生数字，字符串去首尾空白 */
export function buildQuestionBody(form: QuestionForm): QuestionBody {
  const base = {
    categoryId: Number(form.categoryId),
    type: form.type,
    stem: form.stem.trim(),
    analysis: form.analysis.trim(),
    difficulty: Number(form.difficulty),
    tags: cleanList(form.tags),
    imageUrl: form.imageUrl || null,
    source: form.source.trim() || '原创',
    ...(form.sort === null || form.sort === undefined ? {} : { sort: Number(form.sort) }),
    status: form.status,
  }
  if (isChoice(form.type)) {
    const order = form.options.map((o) => o.key)
    const correct = [...new Set(form.correct)].sort((a, b) => order.indexOf(a) - order.indexOf(b))
    return {
      ...base,
      options: form.options.map((o) => ({ key: o.key, text: o.text.trim() })),
      correctAnswer: correct.length ? correct : null,
      acceptedAnswers: null,
      fillOptions: null,
    }
  }
  if (form.type === 'JUDGE') {
    return { ...base, correctAnswer: form.correct.length ? [form.correct[0]] : null, acceptedAnswers: null, fillOptions: null }
  }
  const blanks = form.blanks.map(cleanList)
  return {
    ...base,
    correctAnswer: null,
    acceptedAnswers: blanks.every((b) => b.length) ? blanks : null,
    fillOptions: { ignoreCase: form.ignoreCase === true, ignoreSpaces: form.ignoreSpaces === true },
  }
}
