import { describe, expect, it } from 'vitest'
import { ApiError, describeError, formatRate, formatTime, interpret, resolveAssetUrl } from '@/logic/http'

describe('接口返回解释', () => {
  it('成功、401、业务错误、网关错误、没连上', () => {
    expect(interpret(201, { code: 'OK', message: 'ok', data: { id: 1 } })).toEqual({ ok: true, data: { id: 1 } })
    const unauth = interpret(401, { code: 'UNAUTHORIZED', message: '用户名或密码错误' })
    expect(!unauth.ok && unauth.error.message).toBe('用户名或密码错误')
    const gap = interpret(409, { code: 'POOL_INSUFFICIENT', message: '题不够', data: { gaps: [] } })
    expect(!gap.ok && gap.error.code).toBe('POOL_INSUFFICIENT')
    const bad = interpret(502, '<html>')
    expect(!bad.ok && bad.error.message).toBe('服务器开小差了，稍后重试')
    const none = interpret(0, null)
    expect(!none.ok && none.error.code).toBe('NETWORK')
  })

  it('字段校验错误带字段名', () => {
    const e = new ApiError('VALIDATION_FAILED', '参数不合法', 400, { fields: [{ field: 'options', message: '至少 2 项' }] })
    expect(describeError(e)).toBe('options：至少 2 项')
    expect(describeError(new Error('x'))).toBe('网络不好，稍后重试')
  })

  it('上传地址、比率、北京时间', () => {
    expect(resolveAssetUrl('/quiz/api', '/uploads/a.png')).toBe('/quiz/uploads/a.png')
    expect(resolveAssetUrl('/api', '/uploads/a.png')).toBe('/uploads/a.png')
    expect(formatRate(null)).toBe('—')
    expect(formatRate(0.5)).toBe('50%')
    expect(formatTime('2026-11-01T16:30:00.000Z')).toBe('2026-11-02 00:30')
    expect(formatTime(null)).toBe('—')
  })
})
