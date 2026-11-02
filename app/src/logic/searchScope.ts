/**
 * 搜题范围（PM-M3-13）：默认是目标考试整体，可以缩到某个科目或章节。
 * 范围用分类 id 表示，作为 categoryId 传给 /api/questions/search；null 表示整个考试。
 */

interface TreeNode {
  id: number
  name: string
  level: number
  children?: TreeNode[]
}

export interface ScopeOption {
  id: number
  level: number
  name: string
  /** 从科目开始的路径，如「数据结构 / 线性表」 */
  path: string
}

/** 考试下面可选的范围：每个科目，后面跟着它的章节 */
export function scopeOptions(root: TreeNode | null | undefined): ScopeOption[] {
  const out: ScopeOption[] = []
  ;(root?.children ?? []).forEach((subject) => {
    out.push({ id: subject.id, level: subject.level, name: subject.name, path: subject.name })
    ;(subject.children ?? []).forEach((ch) => {
      out.push({ id: ch.id, level: ch.level, name: ch.name, path: `${subject.name} / ${ch.name}` })
    })
  })
  return out
}

/** 选中的范围还在这棵树里时原样返回，否则（换了考试、分类被删）回到整个考试 */
export function keepScope(root: TreeNode | null | undefined, scopeId: number | null): number | null {
  if (scopeId === null) return null
  return scopeOptions(root).some((o) => o.id === scopeId) ? scopeId : null
}

/** 传给搜题接口的 categoryId：选了科目或章节用它，否则用目标考试；都没有就不传 */
export function searchCategoryId(targetId: number | null, scopeId: number | null): number | undefined {
  return scopeId ?? targetId ?? undefined
}

/** 页面上显示的当前范围，如「计算机考研 408 · 数据结构 / 线性表」 */
export function scopeLabel(examName: string, root: TreeNode | null | undefined, scopeId: number | null): string {
  const exam = examName || '全部考试'
  if (scopeId === null) return exam
  const opt = scopeOptions(root).find((o) => o.id === scopeId)
  return opt ? `${exam} · ${opt.path}` : exam
}
