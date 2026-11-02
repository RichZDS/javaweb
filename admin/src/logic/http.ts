/** 接口返回的解释，与 axios 无关的纯函数部分。 */

export const NETWORK_ERROR_MESSAGE = '网络不好，稍后重试'
export const SERVER_ERROR_MESSAGE = '服务器开小差了，稍后重试'

export class ApiError extends Error {
  readonly code: string
  readonly status: number
  readonly data: unknown

  constructor(code: string, message: string, status: number, data: unknown = null) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.data = data
  }
}

interface Envelope {
  code: string
  message?: string
  data?: unknown
}

function isEnvelope(body: unknown): body is Envelope {
  return typeof body === 'object' && body !== null && typeof (body as Envelope).code === 'string'
}

/** 把一次 HTTP 返回解释成成功数据或 ApiError；status 为 0 表示没连上 */
export function interpret<T>(status: number, body: unknown): { ok: true; data: T } | { ok: false; error: ApiError } {
  if (status === 0) return { ok: false, error: new ApiError('NETWORK', NETWORK_ERROR_MESSAGE, 0) }
  if (status >= 200 && status < 300 && isEnvelope(body) && body.code === 'OK') {
    return { ok: true, data: (body.data ?? null) as T }
  }
  if (status === 401) {
    const message = isEnvelope(body) && body.message ? body.message : '登录已过期，请重新登录'
    return { ok: false, error: new ApiError('UNAUTHORIZED', message, 401) }
  }
  if (isEnvelope(body)) {
    const message = body.message || (status >= 500 ? SERVER_ERROR_MESSAGE : `请求失败（${status}）`)
    return { ok: false, error: new ApiError(body.code, message, status, body.data ?? null) }
  }
  return {
    ok: false,
    error: new ApiError(status >= 500 ? 'INTERNAL_ERROR' : `HTTP_${status}`, status >= 500 ? SERVER_ERROR_MESSAGE : `请求失败（${status}）`, status),
  }
}

/** 参数校验错误时拼出字段提示 */
export function describeError(error: unknown): string {
  if (!(error instanceof ApiError)) return NETWORK_ERROR_MESSAGE
  if (error.code === 'VALIDATION_FAILED') {
    const fields = (error.data as { fields?: { field: string; message: string }[] } | null)?.fields
    if (Array.isArray(fields) && fields.length) return fields.map((f) => `${f.field}：${f.message}`).join('；')
  }
  return error.message
}

/** 上传文件的站内路径 `/uploads/x.png` → 站点地址（接口前缀去掉末尾 /api） */
export function resolveAssetUrl(apiBase: string, url: string | null | undefined): string {
  if (!url) return ''
  if (/^(https?:)?\/\//.test(url) || url.startsWith('data:') || url.startsWith('blob:')) return url
  const site = apiBase.replace(/\/+$/, '').replace(/\/api$/, '')
  return `${site}${url.startsWith('/') ? url : `/${url}`}`
}

/** 0~1 → 67%；null 显示 — */
export function formatRate(rate: number | null | undefined, digits = 0): string {
  if (rate === null || rate === undefined || Number.isNaN(rate)) return '—'
  return `${(rate * 100).toFixed(digits)}%`
}

/** UTC 时间 → 北京时间 YYYY-MM-DD HH:mm */
export function formatTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  const t = Date.parse(iso)
  if (Number.isNaN(t)) return '—'
  const d = new Date(t + 8 * 3600 * 1000)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())} ${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}`
}
