/**
 * axios 封装。
 * - 自动带 `Authorization: Bearer <token>`
 * - 401：清登录状态，回登录页
 * - 提示规则同 App：用户操作（新建、保存、删除、上下架、导入）失败弹 ElMessage；
 *   页面加载（GET）默认不弹，由页面显示错误和「重试」
 */
import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { ApiError, describeError, interpret } from '@/logic/http'

export const API_BASE: string = import.meta.env.VITE_API_BASE

const client = axios.create({ baseURL: API_BASE, timeout: 30000, validateStatus: () => true })

let getToken: () => string = () => ''
let onUnauthorized: () => void = () => {}

export function configureHttp(options: { getToken: () => string; onUnauthorized: () => void }): void {
  getToken = options.getToken
  onUnauthorized = options.onUnauthorized
}

client.interceptors.request.use((config) => {
  const token = getToken()
  if (token) config.headers.set('Authorization', `Bearer ${token}`)
  return config
})

export interface CallOptions {
  /** true：不弹提示；字符串数组：这些错误码不弹提示 */
  silent?: boolean | string[]
  /** 登录接口：401 不跳转 */
  anonymous?: boolean
}

async function call<T>(config: AxiosRequestConfig, options: CallOptions = {}): Promise<T> {
  let status = 0
  let body: unknown = null
  try {
    const res = await client.request(config)
    status = res.status
    body = res.data
    // 下载接口出错时返回的是 Blob 里的 JSON
    if (body instanceof Blob && (status < 200 || status >= 300)) {
      try {
        body = JSON.parse(await body.text())
      } catch {
        body = null
      }
    }
  } catch {
    status = 0
  }
  if (config.responseType === 'blob' && status >= 200 && status < 300) return body as T
  const result = interpret<T>(status, body)
  if (result.ok) return result.data
  const error = result.error
  if (error.code === 'UNAUTHORIZED' && !options.anonymous) {
    onUnauthorized()
    throw error
  }
  const silent = options.silent
  if (!(silent === true || (Array.isArray(silent) && silent.includes(error.code)))) {
    ElMessage.error(describeError(error))
  }
  throw error
}

export const http = {
  /** 页面加载数据：默认不弹提示 */
  get: <T>(url: string, params?: Record<string, unknown>, options: CallOptions = {}) =>
    call<T>({ method: 'GET', url, params: clean(params) }, { ...options, silent: options.silent ?? true }),
  post: <T>(url: string, data?: unknown, options?: CallOptions) => call<T>({ method: 'POST', url, data }, options),
  put: <T>(url: string, data?: unknown, options?: CallOptions) => call<T>({ method: 'PUT', url, data }, options),
  del: <T>(url: string, options?: CallOptions) => call<T>({ method: 'DELETE', url }, options),
  /** multipart 上传，字段名 file */
  upload: <T>(url: string, file: File, options?: CallOptions) => {
    const form = new FormData()
    form.append('file', file)
    return call<T>({ method: 'POST', url, data: form }, options)
  },
  /** 下载文件（带令牌），返回 Blob */
  download: (url: string, options?: CallOptions) => call<Blob>({ method: 'GET', url, responseType: 'blob' }, options),
}

/** 去掉 undefined / null / 空串的查询参数 */
function clean(params?: Record<string, unknown>): Record<string, unknown> | undefined {
  if (!params) return undefined
  return Object.fromEntries(Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== ''))
}

export { ApiError }
