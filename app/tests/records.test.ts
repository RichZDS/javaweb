import { describe, expect, it } from 'vitest'
import { buildQuery } from '@/logic/http'
import { effectiveScope, scopeQuery, sourceName } from '@/logic/records'
import { keepScope, scopeLabel, scopeOptions, searchCategoryId } from '@/logic/searchScope'

describe('记录范围（PM-M3-12）', () => {
  it('当前考试不传范围参数，后端按目标考试过滤', () => {
    expect(buildQuery({ ...scopeQuery('target'), page: 1, size: 20 })).toBe('?page=1&size=20')
  })

  it('全部考试传 all=true', () => {
    expect(buildQuery({ ...scopeQuery('all'), page: 2, size: 20 })).toBe('?all=true&page=2&size=20')
  })

  it('没有目标考试时只有全部考试', () => {
    expect(effectiveScope('target', false)).toBe('all')
    expect(effectiveScope('target', true)).toBe('target')
    expect(effectiveScope('all', true)).toBe('all')
  })

  it('来源考试为空显示「未分考试」', () => {
    expect(sourceName('数据库原理与 MySQL')).toBe('数据库原理与 MySQL')
    expect(sourceName(null)).toBe('未分考试')
    expect(sourceName(undefined)).toBe('未分考试')
    expect(sourceName('')).toBe('未分考试')
  })
})

const root = {
  id: 1,
  name: '计算机考研 408',
  level: 1,
  children: [
    {
      id: 2,
      name: '数据结构',
      level: 2,
      children: [
        { id: 3, name: '线性表', level: 3 },
        { id: 4, name: '树与二叉树', level: 3 },
      ],
    },
    { id: 5, name: '操作系统', level: 2, children: [{ id: 6, name: '进程管理', level: 3 }] },
    { id: 7, name: '计算机网络', level: 2 },
  ],
}

describe('搜题范围（PM-M3-13）', () => {
  it('可选范围：每个科目后面跟着它的章节', () => {
    expect(scopeOptions(root).map((o) => o.path)).toEqual([
      '数据结构',
      '数据结构 / 线性表',
      '数据结构 / 树与二叉树',
      '操作系统',
      '操作系统 / 进程管理',
      '计算机网络',
    ])
    expect(scopeOptions(null)).toEqual([])
  })

  it('默认搜整个目标考试；选了章节就只搜这个章节', () => {
    expect(searchCategoryId(1, null)).toBe(1)
    expect(searchCategoryId(1, 3)).toBe(3)
    expect(searchCategoryId(1, 2)).toBe(2)
    expect(searchCategoryId(null, null)).toBeUndefined()
  })

  it('页面上显示的范围', () => {
    expect(scopeLabel('计算机考研 408', root, null)).toBe('计算机考研 408')
    expect(scopeLabel('计算机考研 408', root, 2)).toBe('计算机考研 408 · 数据结构')
    expect(scopeLabel('计算机考研 408', root, 4)).toBe('计算机考研 408 · 数据结构 / 树与二叉树')
    expect(scopeLabel('', null, null)).toBe('全部考试')
  })

  it('重新拉到的树里没有选中的范围时回到整个考试', () => {
    expect(keepScope(root, 6)).toBe(6)
    expect(keepScope(root, 99)).toBeNull()
    expect(keepScope(null, 3)).toBeNull()
    expect(keepScope(root, null)).toBeNull()
  })
})
