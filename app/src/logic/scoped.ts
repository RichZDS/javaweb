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
