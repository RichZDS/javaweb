/**
 * 请求封装里与平台无关的部分：拼地址、解释返回。
 * 真正发请求的 uni.request 在 src/api/http.ts，这里保持纯函数，方便测试。
 */

export const NETWORK_ERROR_MESSAGE = '网络不好，稍后重试'
export const SERVER_ERROR_MESSAGE = '服务器开小差了，稍后重试'

/** 后端错误码，见 docs/api.md 第 0 节；NETWORK 是前端自己定义的「没连上」 */
export type ErrorCode =
  | 'VALIDATION_FAILED'
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  | 'EXAM_IN_PROGRESS'
  | 'NOT_FOUND'
  | 'USERNAME_TAKEN'
  | 'POOL_INSUFFICIENT'
  | 'EXAM_EXPIRED'
  | 'CONFLICT'
  | 'INTERNAL_ERROR'
  | 'NETWORK'
  | string

export class ApiError extends Error {
  readonly code: ErrorCode
  /** HTTP 状态码；没连上服务器时为 0 */
  readonly status: number
  readonly data: unknown

  constructor(code: ErrorCode, message: string, status: number, data: unknown = null) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.data = data
  }

  get isNetwork(): boolean {
    return this.code === 'NETWORK'
  }
}

export function networkError(): ApiError {
  return new ApiError('NETWORK', NETWORK_ERROR_MESSAGE, 0)
}

/** 把 query 对象拼成 `?a=1&b=2`，跳过 undefined / null / 空串；数组按逗号拼接 */
export function buildQuery(query?: Record<string, unknown>): string {
  if (!query) return ''
  const parts: string[] = []
  for (const [key, raw] of Object.entries(query)) {
    if (raw === undefined || raw === null || raw === '') continue
    const value = Array.isArray(raw) ? raw.join(',') : String(raw)
    parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
  }
  return parts.length ? `?${parts.join('&')}` : ''
}

/** base 形如 `http://host/api` 或 `/quiz/api`，path 形如 `/me`，拼接时去掉重复的斜杠 */
export function joinUrl(base: string, path: string, query?: Record<string, unknown>): string {
  const left = base.replace(/\/+$/, '')
  const right = path.startsWith('/') ? path : `/${path}`
  return `${left}${right}${buildQuery(query)}`
}

interface Envelope {
  code: string
  message?: string
  data?: unknown
}

function isEnvelope(body: unknown): body is Envelope {
  return typeof body === 'object' && body !== null && typeof (body as Envelope).code === 'string'
}

export type Interpreted<T> = { ok: true; data: T; status: number } | { ok: false; error: ApiError }

/**
 * 解释一次 HTTP 返回。
 * - 2xx 且 code=OK → 成功，取 data
 * - 401 一律当 UNAUTHORIZED（令牌过期、没登录）
 * - 其他按后端 code / message；后端没给 JSON（网关 502 之类）时给通用提示
 */
export function interpretResponse<T>(status: number, body: unknown): Interpreted<T> {
  // uni.uploadFile 的返回体是字符串，这里统一尝试解析一次
  let payload = body
  if (typeof payload === 'string') {
    try {
      payload = JSON.parse(payload)
    } catch {
      payload = null
    }
  }

  if (status >= 200 && status < 300 && isEnvelope(payload) && payload.code === 'OK') {
    return { ok: true, data: (payload.data ?? null) as T, status }
  }

  if (status === 401 || (isEnvelope(payload) && payload.code === 'UNAUTHORIZED')) {
    const message = isEnvelope(payload) && payload.message ? payload.message : '登录已过期，请重新登录'
    return { ok: false, error: new ApiError('UNAUTHORIZED', message, 401, isEnvelope(payload) ? payload.data : null) }
  }

  if (isEnvelope(payload)) {
    const message = payload.message || (status >= 500 ? SERVER_ERROR_MESSAGE : `请求失败（${status}）`)
    return { ok: false, error: new ApiError(payload.code, message, status, payload.data ?? null) }
  }

  const code = status >= 500 ? 'INTERNAL_ERROR' : `HTTP_${status}`
  const message = status >= 500 ? SERVER_ERROR_MESSAGE : `请求失败（${status}）`
  return { ok: false, error: new ApiError(code, message, status) }
}

/** 400 VALIDATION_FAILED 时把 data.fields 拼成一句话，优先展示字段级提示 */
export function describeError(error: ApiError): string {
  if (error.code === 'VALIDATION_FAILED') {
    const fields = (error.data as { fields?: { field: string; message: string }[] } | null)?.fields
    if (Array.isArray(fields) && fields.length) {
      return fields.map((f) => f.message).join('；')
    }
  }
  return error.message
}

/**
 * 头像、题图等上传文件的地址：后端返回 `/uploads/xx.png` 这样的站内路径，
 * 静态文件和接口在同一个站点下，所以用接口前缀去掉末尾的 `/api` 作为站点前缀。
 */
export function resolveAssetUrl(apiBase: string, url: string | null | undefined): string {
  if (!url) return ''
  if (/^(https?:)?\/\//.test(url) || url.startsWith('data:') || url.startsWith('blob:')) return url
  const site = apiBase.replace(/\/+$/, '').replace(/\/api$/, '')
  return `${site}${url.startsWith('/') ? url : `/${url}`}`
}
