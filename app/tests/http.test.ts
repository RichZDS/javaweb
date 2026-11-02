import { describe, expect, it } from 'vitest'
import {
  ApiError,
  NETWORK_ERROR_MESSAGE,
  buildQuery,
  describeError,
  interpretResponse,
  joinUrl,
  networkError,
  resolveAssetUrl,
} from '@/logic/http'

describe('拼地址', () => {
  it('跳过空参数，数组用逗号', () => {
    expect(buildQuery({ a: 1, b: undefined, c: null, d: '', e: [1, 2] })).toBe('?a=1&e=1%2C2')
    expect(buildQuery({})).toBe('')
    expect(buildQuery()).toBe('')
  })

  it('base 与 path 之间只留一个斜杠', () => {
    expect(joinUrl('http://localhost:8080/api/', '/me')).toBe('http://localhost:8080/api/me')
    expect(joinUrl('/quiz/api', 'categories', { rootId: 3 })).toBe('/quiz/api/categories?rootId=3')
  })
})

describe('interpretResponse', () => {
  it('2xx + code OK 取 data', () => {
    expect(interpretResponse(200, { code: 'OK', message: 'ok', data: { id: 1 } })).toEqual({
      ok: true,
      data: { id: 1 },
      status: 200,
    })
    const created = interpretResponse(201, { code: 'OK', message: 'ok', data: null })
    expect(created.ok && created.status).toBe(201)
  })

  it('401 一律是 UNAUTHORIZED', () => {
    const r = interpretResponse(401, { code: 'UNAUTHORIZED', message: '令牌过期', data: null })
    expect(r.ok).toBe(false)
    if (!r.ok) {
      expect(r.error.code).toBe('UNAUTHORIZED')
      expect(r.error.status).toBe(401)
    }
    const bare = interpretResponse(401, '')
    expect(!bare.ok && bare.error.code).toBe('UNAUTHORIZED')
  })

  it('业务错误保留 code、message、data', () => {
    const r = interpretResponse(409, { code: 'POOL_INSUFFICIENT', message: '题不够', data: { available: 7 } })
    expect(r.ok).toBe(false)
    if (!r.ok) {
      expect(r.error).toBeInstanceOf(ApiError)
      expect(r.error.code).toBe('POOL_INSUFFICIENT')
      expect(r.error.message).toBe('题不够')
      expect(r.error.data).toEqual({ available: 7 })
    }
  })

  it('网关返回非 JSON 时给通用提示，不暴露原文', () => {
    const r = interpretResponse(502, '<html>Bad Gateway</html>')
    expect(!r.ok && r.error.code).toBe('INTERNAL_ERROR')
    expect(!r.ok && r.error.message).toBe('服务器开小差了，稍后重试')
  })

  it('uploadFile 的字符串返回体会先解析', () => {
    const r = interpretResponse(200, JSON.stringify({ code: 'OK', message: 'ok', data: { url: '/uploads/a.png' } }))
    expect(r).toEqual({ ok: true, data: { url: '/uploads/a.png' }, status: 200 })
  })
})

describe('错误文案', () => {
  it('网络失败', () => {
    const e = networkError()
    expect(e.isNetwork).toBe(true)
    expect(e.message).toBe(NETWORK_ERROR_MESSAGE)
  })

  it('参数校验错误拼出字段提示', () => {
    const e = new ApiError('VALIDATION_FAILED', '参数不合法', 400, {
      fields: [
        { field: 'username', message: '用户名为 3–20 位' },
        { field: 'password', message: '密码为 6–32 位' },
      ],
    })
    expect(describeError(e)).toBe('用户名为 3–20 位；密码为 6–32 位')
    expect(describeError(new ApiError('CONFLICT', '冲突', 409))).toBe('冲突')
  })
})

describe('resolveAssetUrl', () => {
  it('站内路径按接口前缀换成站点地址', () => {
    expect(resolveAssetUrl('http://localhost:8080/api', '/uploads/a.png')).toBe('http://localhost:8080/uploads/a.png')
    expect(resolveAssetUrl('/quiz/api', '/uploads/a.png')).toBe('/quiz/uploads/a.png')
    expect(resolveAssetUrl('https://43.142.166.108/quiz/api/', 'uploads/a.png')).toBe(
      'https://43.142.166.108/quiz/uploads/a.png',
    )
  })

  it('绝对地址和空值原样处理', () => {
    expect(resolveAssetUrl('/quiz/api', 'https://cdn.example.com/x.png')).toBe('https://cdn.example.com/x.png')
    expect(resolveAssetUrl('/quiz/api', null)).toBe('')
  })
})
