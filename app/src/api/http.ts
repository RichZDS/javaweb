/**
 * 统一请求封装（uni.request / uni.uploadFile）。
 * - 自动带 `Authorization: Bearer <token>`
 * - 401：清令牌、跳登录（不管是否静默）
 * - 提示规则：用户主动操作（提交、开考、保存、删除，即 POST / PUT / DELETE / 上传）失败时，
 *   按后端 message 弹提示，没连上服务器提示「网络不好，稍后重试」；
 *   页面加载数据（GET）默认不弹，由页面自己显示「—」、空状态或「重试」按钮。
 *   GET 也属于用户主动操作时（如点「继续练习」）传 `silent: false`。
 * 调用方需要自己处理某个错误码时，用 `silent: ['错误码']` 关掉对应提示，再 catch ApiError。
 */
import { ApiError, describeError, interpretResponse, joinUrl, networkError } from '@/logic/http'

/** 接口前缀：只在 .env.* 的 VITE_API_BASE 一处配置 */
export const API_BASE: string = import.meta.env.VITE_API_BASE

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE'

export interface RequestOptions {
  query?: Record<string, unknown>
  data?: unknown
  /** true：不弹任何错误提示；字符串数组：这些错误码不弹提示 */
  silent?: boolean | string[]
  /** 登录、注册这类不带令牌、401 不跳登录页的接口 */
  anonymous?: boolean
}

let tokenProvider: () => string = () => ''
let onUnauthorized: (error: ApiError) => void = () => {}

/** main.ts 启动时注入，避免这里直接依赖 store */
export function configureHttp(options: { getToken: () => string; onUnauthorized: (error: ApiError) => void }): void {
  tokenProvider = options.getToken
  onUnauthorized = options.onUnauthorized
}

function headers(anonymous?: boolean): Record<string, string> {
  const h: Record<string, string> = { 'Content-Type': 'application/json' }
  const token = anonymous ? '' : tokenProvider()
  if (token) h.Authorization = `Bearer ${token}`
  return h
}

function shouldToast(error: ApiError, silent: RequestOptions['silent']): boolean {
  if (silent === true) return false
  if (Array.isArray(silent) && silent.includes(error.code)) return false
  return true
}

function fail(error: ApiError, options: RequestOptions): ApiError {
  if (error.code === 'UNAUTHORIZED' && !options.anonymous) {
    onUnauthorized(error)
    return error
  }
  if (shouldToast(error, options.silent)) {
    uni.showToast({ title: describeError(error), icon: 'none', duration: 2500 })
  }
  return error
}

export function request<T>(method: Method, path: string, options: RequestOptions = {}): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    uni.request({
      url: joinUrl(API_BASE, path, options.query),
      method,
      data: options.data as UniApp.RequestOptions['data'],
      header: headers(options.anonymous),
      timeout: 15000,
      success: (res) => {
        const result = interpretResponse<T>(res.statusCode, res.data)
        if (result.ok) resolve(result.data)
        else reject(fail(result.error, options))
      },
      fail: () => reject(fail(networkError(), options)),
    })
  })
}

/** multipart 上传，字段名 file；返回 data（如 `{url}`） */
export function upload<T>(path: string, filePath: string, options: RequestOptions = {}): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const header = headers(options.anonymous)
    delete header['Content-Type'] // 让 uni 自己带 multipart boundary
    uni.uploadFile({
      url: joinUrl(API_BASE, path),
      filePath,
      name: 'file',
      header,
      success: (res) => {
        const result = interpretResponse<T>(res.statusCode, res.data)
        if (result.ok) resolve(result.data)
        else reject(fail(result.error, options))
      },
      fail: () => reject(fail(networkError(), options)),
    })
  })
}

export const http = {
  /** 页面加载数据：默认不弹错误提示 */
  get: <T>(path: string, query?: Record<string, unknown>, options?: Omit<RequestOptions, 'query'>) =>
    request<T>('GET', path, { ...options, silent: options?.silent ?? true, query }),
  post: <T>(path: string, data?: unknown, options?: Omit<RequestOptions, 'data'>) =>
    request<T>('POST', path, { ...options, data }),
  put: <T>(path: string, data?: unknown, options?: Omit<RequestOptions, 'data'>) =>
    request<T>('PUT', path, { ...options, data }),
  del: <T>(path: string, options?: RequestOptions) => request<T>('DELETE', path, options),
}

export { ApiError }
