import { describe, expect, it } from 'vitest'
import { examOf, flatten, pathOf, toCascader } from '@/logic/tree'
import type { AdminCategory } from '@/api/types'

const node = (id: number, name: string, level: 1 | 2 | 3, children: AdminCategory[] = []): AdminCategory => ({
  id, parentId: null, name, level, sort: 0, questionCount: 1, children,
})
const tree = [
  node(1, '408', 1, [node(2, '数据结构', 2, [node(3, '线性表', 3)]), node(4, '空科目', 2)]),
  node(5, 'Java', 1, [node(6, '基础', 2, [node(7, '语法', 3)])]),
]

describe('分类树', () => {
  it('录题用：没有子节点的科目置灰，不能当章节选', () => {
    const opts = toCascader(tree, { onlyLevel3: true })
    expect(opts[0].children?.[1]).toMatchObject({ value: 4, disabled: true })
    expect(opts[0].children?.[0].children?.[0]).toMatchObject({ value: 3 })
    expect(opts[0].children?.[0].children?.[0].disabled).toBeUndefined()
  })

  it('只保留某个考试的子树', () => {
    expect(toCascader(tree, { rootId: 5 }).map((o) => o.value)).toEqual([5])
  })

  it('路径、所属考试、拍平', () => {
    expect(pathOf(tree, 3)).toBe('408 / 数据结构 / 线性表')
    expect(pathOf(tree, 99)).toBe('')
    expect(examOf(tree, 7)).toBe(5)
    expect(examOf(tree, null)).toBeNull()
    expect(flatten(tree).map((n) => n.id)).toEqual([1, 2, 3, 4, 5, 6, 7])
  })
})
