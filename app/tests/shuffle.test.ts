import { describe, expect, it } from 'vitest'
import { analysisNote, isShuffled, originalKey, originalKeys, originalNote } from '@/logic/shuffle'

const mapping = { A: 'C', B: 'A', C: 'D', D: 'B' }

describe('选项打乱：新标签 → 原标签', () => {
  it('判断是否真的打乱', () => {
    expect(isShuffled(mapping)).toBe(true)
    expect(isShuffled(null)).toBe(false)
    expect(isShuffled({ A: 'A', B: 'B' })).toBe(false)
  })

  it('单个、多个标签换算，找不到时原样', () => {
    expect(originalKey(mapping, 'A')).toBe('C')
    expect(originalKey(null, 'A')).toBe('A')
    expect(originalKey(mapping, 'Z')).toBe('Z')
    expect(originalKeys(mapping, ['B', 'D'])).toEqual(['A', 'B'])
    expect(originalKeys(null, ['B'])).toEqual(['B'])
  })

  it('选项旁注明原选项；没打乱或字母没变时不注', () => {
    expect(originalNote(mapping, 'A')).toBe('原选项 C')
    expect(originalNote({ A: 'A', B: 'C', C: 'B' }, 'A')).toBe('')
    expect(originalNote(null, 'A')).toBe('')
  })

  it('正确答案旁注明对应解析里的字母（按原字母排序）', () => {
    expect(analysisNote(mapping, ['A', 'C'])).toBe('对应解析里的 C、D')
    expect(analysisNote(mapping, ['D', 'B'])).toBe('对应解析里的 A、B')
    expect(analysisNote(null, ['A'])).toBe('')
    expect(analysisNote({ A: 'A', B: 'C', C: 'B' }, ['A'])).toBe('')
    expect(analysisNote(mapping, [])).toBe('')
  })
})
