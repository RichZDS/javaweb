/** 分类树工具：级联选择器选项、找路径、找所属考试。 */
import type { AdminCategory } from '@/api/types'

export interface CascaderOption {
  value: number
  label: string
  disabled?: boolean
  children?: CascaderOption[]
}

/**
 * 树 → el-cascader 选项。
 * - onlyLevel3：只能选到章节（录题用）。没有子节点的考试、科目会置灰，避免被当成叶子选中
 * - rootId：只保留某个考试的子树（规则卷选分类用）
 */
export function toCascader(tree: readonly AdminCategory[], opts: { onlyLevel3?: boolean; rootId?: number | null } = {}): CascaderOption[] {
  const roots = opts.rootId ? tree.filter((n) => n.id === opts.rootId) : tree
  const map = (n: AdminCategory): CascaderOption => {
    const children = (n.children ?? []).map(map)
    const option: CascaderOption = { value: n.id, label: `${n.name}（${n.questionCount}）` }
    if (children.length) option.children = children
    if (opts.onlyLevel3 && n.level < 3 && !children.length) option.disabled = true
    return option
  }
  return roots.map(map)
}

export function flatten(tree: readonly AdminCategory[]): AdminCategory[] {
  const out: AdminCategory[] = []
  const walk = (n: AdminCategory) => {
    out.push(n)
    n.children?.forEach(walk)
  }
  tree.forEach(walk)
  return out
}

/** 从根到该节点的名字，用「 / 」连接；找不到返回空串 */
export function pathOf(tree: readonly AdminCategory[], id: number | null | undefined): string {
  if (!id) return ''
  const trail: string[] = []
  const walk = (nodes: readonly AdminCategory[]): boolean => {
    for (const n of nodes) {
      trail.push(n.name)
      if (n.id === id || walk(n.children ?? [])) return true
      trail.pop()
    }
    return false
  }
  return walk(tree) ? trail.join(' / ') : ''
}

/** 某个分类所属的考试（一级分类）id */
export function examOf(tree: readonly AdminCategory[], id: number | null | undefined): number | null {
  if (!id) return null
  for (const root of tree) {
    if (flatten([root]).some((n) => n.id === id)) return root.id
  }
  return null
}
