import { describe, expect, it } from 'vitest'
import { formatClock, formatDateTime, formatDuration, formatRate, htmlToPreview } from '@/logic/format'
import { isTokenValid, validateNickname, validatePassword, validateUsername } from '@/logic/auth'

describe('formatRate', () => {
  it('没有数据显示「—」而不是 0% 或 NaN', () => {
    expect(formatRate(null)).toBe('—')
    expect(formatRate(undefined)).toBe('—')
    expect(formatRate(Number.NaN)).toBe('—')
    expect(formatRate(0)).toBe('0%')
    expect(formatRate(2 / 3)).toBe('67%')
    expect(formatRate(0.715, 1)).toBe('71.5%')
  })
})

describe('时长', () => {
  it('formatDuration', () => {
    expect(formatDuration(45)).toBe('45秒')
    expect(formatDuration(725)).toBe('12分05秒')
    expect(formatDuration(3720)).toBe('1小时02分')
    expect(formatDuration(-3)).toBe('0秒')
  })

  it('formatClock', () => {
    expect(formatClock(65)).toBe('01:05')
    expect(formatClock(3661)).toBe('1:01:01')
    expect(formatClock(-1)).toBe('00:00')
  })
})

describe('formatDateTime 按北京时间显示', () => {
  const now = new Date('2026-11-02T08:00:00.000Z')
  it('UTC 16:30 是北京时间次日 00:30', () => {
    expect(formatDateTime('2026-11-01T16:30:00.000Z', now)).toBe('11-02 00:30')
  })
  it('跨年带年份', () => {
    expect(formatDateTime('2025-12-31T01:00:00.000Z', now)).toBe('2025-12-31 09:00')
  })
  it('空值和非法值返回空串', () => {
    expect(formatDateTime(null, now)).toBe('')
    expect(formatDateTime('not a date', now)).toBe('')
  })
})

describe('htmlToPreview', () => {
  it('去标签、解实体、压空白、截断', () => {
    expect(htmlToPreview('<p>a &lt; b</p>\n<pre><code>int x;</code></pre>')).toBe('a < b int x;')
    expect(htmlToPreview('<p>一二三四五</p>', 3)).toBe('一二三…')
    expect(htmlToPreview('<script>alert(1)</script>ok')).toBe('ok')
  })
})

describe('账号校验', () => {
  it('用户名 3–20 位字母数字下划线', () => {
    expect(validateUsername('ab')).not.toBe('')
    expect(validateUsername('a'.repeat(21))).not.toBe('')
    expect(validateUsername('ab-c')).not.toBe('')
    expect(validateUsername('abc')).toBe('')
    expect(validateUsername('user_01')).toBe('')
  })

  it('密码 6–32 位', () => {
    expect(validatePassword('12345')).not.toBe('')
    expect(validatePassword('1'.repeat(33))).not.toBe('')
    expect(validatePassword('123456')).toBe('')
  })

  it('昵称最多 30 字', () => {
    expect(validateNickname('a'.repeat(31))).not.toBe('')
    expect(validateNickname('小明')).toBe('')
  })

  it('令牌有效期', () => {
    const now = Date.parse('2026-11-02T00:00:00Z')
    expect(isTokenValid('t', '2026-11-09T00:00:00Z', now)).toBe(true)
    expect(isTokenValid('t', '2026-11-01T00:00:00Z', now)).toBe(false)
    expect(isTokenValid('', '2026-11-09T00:00:00Z', now)).toBe(false)
    expect(isTokenValid('t', 'bad', now)).toBe(false)
  })
})
