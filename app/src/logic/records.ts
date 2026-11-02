/**
 * 练习记录、考试记录的范围（api.md 0.4 节，PM-M3-12）。
 * - 当前考试：不传参数，后端按本人目标考试过滤
 * - 全部考试：传 all=true，每条记录显示来源考试
 */

export type RecordScope = 'target' | 'all'

/** 没有目标考试时后端本来就列全部，页面上也只有「全部考试」这一种 */
export function effectiveScope(scope: RecordScope, hasTarget: boolean): RecordScope {
  return hasTarget ? scope : 'all'
}

/** 记录列表接口的范围参数 */
export function scopeQuery(scope: RecordScope): { all?: boolean } {
  return scope === 'all' ? { all: true } : {}
}

/** 全部考试视图里一条记录的来源考试；没有归属考试的练习（没选目标考试时开的）显示「未分考试」 */
export function sourceName(rootCategoryName: string | null | undefined): string {
  return rootCategoryName || '未分考试'
}
