/**
 * 试卷编辑表单 ⇄ 接口请求体（docs/api.md 第 9 节、9.1 AdminPaper）。
 * 总分由后端算出；表单里显示的「预计总分」只是给录入的人参考。
 */
import type { AdminPaper, PaperBody, PaperMode, PaperQuestionRef, QuestionType } from '@/api/types'

export interface RuleRow {
  categoryId: number | null
  categoryPath?: string
  type: QuestionType
  count: number
  score: number
}

export interface PaperForm {
  title: string
  categoryId: number | null
  description: string
  mode: PaperMode
  durationMin: number
  passScore: number
  questions: PaperQuestionRef[]
  rules: RuleRow[]
}

export function emptyPaperForm(categoryId: number | null = null): PaperForm {
  return { title: '', categoryId, description: '', mode: 'FIXED', durationMin: 60, passScore: 60, questions: [], rules: [] }
}

export function formFromPaper(p: AdminPaper): PaperForm {
  return {
    title: p.title,
    categoryId: p.categoryId,
    description: p.description ?? '',
    mode: p.mode,
    durationMin: Number(p.durationMin),
    passScore: Number(p.passScore),
    questions: [...(p.questions ?? [])].sort((a, b) => a.sort - b.sort).map((q) => ({ ...q })),
    rules: (p.rules ?? []).map((r) => ({ ...r })),
  }
}

/** 有人考过的试卷只能改标题、描述（上下架在列表里操作） */
export function isStructureLocked(paper: Pick<AdminPaper, 'attempts'> | null | undefined): boolean {
  return !!paper && paper.attempts > 0
}

/** 预计总分：固定卷按每题分值相加，规则卷按 题数 × 分值 相加 */
export function previewTotal(form: PaperForm): number {
  if (form.mode === 'FIXED') return form.questions.reduce((s, q) => s + (Number(q.score) || 0), 0)
  return form.rules.reduce((s, r) => s + (Number(r.count) || 0) * (Number(r.score) || 0), 0)
}

/** 挑题对话框选中的题加进固定卷：已在卷里的跳过，新题用默认分值，排在最后 */
export function addQuestions(
  current: readonly PaperQuestionRef[],
  picked: readonly { id: number; type: QuestionType; stemPreview: string }[],
  defaultScore: number,
): PaperQuestionRef[] {
  const out = current.map((q) => ({ ...q }))
  const have = new Set(out.map((q) => q.questionId))
  for (const p of picked) {
    if (have.has(p.id)) continue
    have.add(p.id)
    out.push({ questionId: p.id, type: p.type, stemPreview: p.stemPreview, score: defaultScore, sort: out.length + 1 })
  }
  return out
}

/** 上移 / 下移一题，返回新数组 */
export function moveQuestion(list: readonly PaperQuestionRef[], index: number, delta: -1 | 1): PaperQuestionRef[] {
  const target = index + delta
  if (target < 0 || target >= list.length) return [...list]
  const out = [...list]
  ;[out[index], out[target]] = [out[target], out[index]]
  return out
}

export function validatePaperForm(form: PaperForm, structureLocked = false): string[] {
  const errors: string[] = []
  if (!form.title.trim()) errors.push('请填写试卷标题')
  if (structureLocked) return errors
  if (!form.categoryId) errors.push('请选择所属考试')
  if (!Number.isInteger(form.durationMin) || form.durationMin < 1 || form.durationMin > 600) errors.push('考试时长为 1–600 分钟的整数')
  if (!Number.isInteger(form.passScore) || form.passScore < 0) errors.push('及格分是不小于 0 的整数')
  // 分值在库里是整数
  if (form.mode === 'FIXED') {
    if (form.questions.some((q) => !Number.isInteger(Number(q.score)) || Number(q.score) < 1)) errors.push('每道题的分值是正整数')
    const ids = form.questions.map((q) => q.questionId)
    if (new Set(ids).size !== ids.length) errors.push('同一道题不能重复加入')
  } else {
    if (!form.rules.length) errors.push('规则卷至少要有 1 条规则')
    form.rules.forEach((r, i) => {
      if (!r.categoryId) errors.push(`第 ${i + 1} 条规则没选分类`)
      if (!Number.isInteger(r.count) || r.count < 1) errors.push(`第 ${i + 1} 条规则的题数要是正整数`)
      if (!Number.isInteger(Number(r.score)) || Number(r.score) < 1) errors.push(`第 ${i + 1} 条规则的每题分值是正整数`)
    })
  }
  const total = previewTotal(form)
  if (total > 0 && form.passScore > total) errors.push(`及格分（${form.passScore}）不能高于预计总分（${total}）`)
  return errors
}

/**
 * 表单 → 请求体，数字一律原生数字，固定卷按当前顺序重排 sort。
 * 有人考过的试卷照样发整份（结构字段是原值），后端只会改标题和描述。
 */
export function buildPaperBody(form: PaperForm): PaperBody {
  const body: PaperBody = {
    title: form.title.trim(),
    categoryId: Number(form.categoryId),
    description: form.description.trim(),
    mode: form.mode,
    durationMin: Number(form.durationMin),
    passScore: Number(form.passScore),
  }
  if (form.mode === 'FIXED') {
    body.questions = form.questions.map((q, i) => ({ questionId: Number(q.questionId), score: Number(q.score), sort: i + 1 }))
  } else {
    body.rules = form.rules.map((r) => ({
      categoryId: Number(r.categoryId),
      type: r.type,
      count: Number(r.count),
      score: Number(r.score),
    }))
  }
  return body
}
