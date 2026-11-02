/**
 * 按目标考试分开存的数据（PM-M3-03 / PM-M3-05）。
 * 规则：
 * - 一份数据只属于发起请求时的那个考试；当前考试变了，旧数据一律不显示
 * - 请求回来时当前考试已经换了：丢弃，不覆盖
 * - 同一个考试刷新失败：保留这个考试已有的数据并标出错误；没有数据时显示失败和重试
 */

export type SlotStatus = 'loading' | 'ready' | 'error'

export interface Slot<T> {
  /** 这份数据属于哪个考试 */
  target: number
  status: SlotStatus
  data: T | null
  error: unknown
}

/** 开始为 target 加载：考试没变就保留原数据，变了就从空开始 */
export function startLoad<T>(slot: Slot<T> | null, target: number): Slot<T> {
  if (slot && slot.target === target) return { ...slot, status: 'loading', error: null }
  return { target, status: 'loading', data: null, error: null }
}

/** 请求回来：只接受当前 slot 所属考试的结果，其余丢弃 */
export function finishLoad<T>(
  slot: Slot<T> | null,
  target: number,
  result: { ok: true; data: T } | { ok: false; error: unknown },
): Slot<T> | null {
  if (!slot || slot.target !== target) return slot
  if (result.ok) return { target, status: 'ready', data: result.data, error: null }
  return { ...slot, status: 'error', error: result.error }
}

/** 页面上该显示什么：slot 不属于当前考试时，当作还没加载 */
export function viewOf<T>(slot: Slot<T> | null, current: number | null): { status: SlotStatus; data: T | null; error: unknown } {
  if (!slot || current === null || slot.target !== current) return { status: 'loading', data: null, error: null }
  return { status: slot.status, data: slot.data, error: slot.error }
}

/** 分类树里所有节点 id（用来判断一份数据属于哪个考试） */
export function collectCategoryIds(root: { id: number; children?: unknown[] } | null | undefined): Set<number> {
  const ids = new Set<number>()
  const walk = (n: { id: number; children?: unknown[] }) => {
    ids.add(n.id)
    ;(n.children as { id: number; children?: unknown[] }[] | undefined)?.forEach(walk)
  }
  if (root) walk(root)
  return ids
}

/**
 * 一次练习是否属于当前考试：题目都在这个考试的分类树里。
 * 「继续练习」接口不按目标考试过滤，首页要自己挡住别的考试的练习。
 */
export function sessionInExam(
  session: { questions: { categoryId: number }[] } | null | undefined,
  examCategoryIds: ReadonlySet<number>,
): boolean {
  if (!session || !session.questions.length || !examCategoryIds.size) return false
  return session.questions.every((q) => examCategoryIds.has(q.categoryId))
}
