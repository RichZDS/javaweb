import { describe, expect, it } from 'vitest'
import { compareVersion, updateKind } from '@/logic/version'
import { parseTime, upcomingReminders } from '@/logic/reminder'

describe('版本比较', () => {
  it('按段比较数字，不按字符串', () => {
    expect(compareVersion('1.2.10', '1.2.9')).toBe(1)
    expect(compareVersion('1.0', '1.0.0')).toBe(0)
    expect(compareVersion('0.9.9', '1.0.0')).toBe(-1)
    expect(compareVersion('1.0.0-beta', '1.0.0')).toBe(0)
  })

  it('低于最低版本强制升级，低于最新版本提示，否则不提示', () => {
    expect(updateKind('1.0.0', '1.2.0', '1.1.0')).toBe('force')
    expect(updateKind('1.1.0', '1.2.0', '1.1.0')).toBe('optional')
    expect(updateKind('1.2.0', '1.2.0', '1.0.0')).toBe('none')
    expect(updateKind('1.3.0', '1.2.0', null)).toBe('none')
  })
})

describe('学习提醒时间', () => {
  it('解析 HH:mm', () => {
    expect(parseTime('20:30')).toEqual({ h: 20, m: 30 })
    expect(parseTime('7:05')).toEqual({ h: 7, m: 5 })
    expect(parseTime('24:00')).toBeNull()
    expect(parseTime('abc')).toBeNull()
  })

  it('今天还没到就从今天开始，过了从明天开始，连续排 N 天', () => {
    const before = upcomingReminders(new Date(2026, 10, 2, 19, 0), '20:30', 3)
    expect(before.map((d) => [d.getDate(), d.getHours(), d.getMinutes()])).toEqual([
      [2, 20, 30],
      [3, 20, 30],
      [4, 20, 30],
    ])
    const after = upcomingReminders(new Date(2026, 10, 2, 21, 0), '20:30', 2)
    expect(after.map((d) => d.getDate())).toEqual([3, 4])
  })

  it('跨月', () => {
    const r = upcomingReminders(new Date(2026, 10, 30, 22, 0), '08:00', 2)
    expect(r.map((d) => [d.getMonth() + 1, d.getDate()])).toEqual([
      [12, 1],
      [12, 2],
    ])
  })
})
