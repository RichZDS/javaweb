/**
 * 老系统兼容的小工具。
 *
 * App 的逻辑层在 iOS 上跑在系统自带的 JavaScriptCore 里，版本跟着 iOS 走；构建只把新语法改写成旧语法，
 * 不会补上缺的内置函数。所以 src 里只用 iOS 12 就有的内置函数，更新的用这里的写法代替
 * （tests/compat.test.ts 会扫一遍源码）。
 */

export type Settled<T> = { status: 'fulfilled'; value: T } | { status: 'rejected'; reason: unknown }

/**
 * 等所有 promise 都有结果，成功失败都收下，顺序和传入一致。
 * 用法和 ES2020 的同名内置函数一样，那个要 iOS 13 才有。
 */
export function settleAll<T extends readonly unknown[] | []>(
  promises: T,
): Promise<{ -readonly [K in keyof T]: Settled<Awaited<T[K]>> }> {
  const wrapped = promises.map((p) =>
    Promise.resolve(p).then(
      (value): Settled<unknown> => ({ status: 'fulfilled', value }),
      (reason): Settled<unknown> => ({ status: 'rejected', reason }),
    ),
  )
  return Promise.all(wrapped) as Promise<{ -readonly [K in keyof T]: Settled<Awaited<T[K]>> }>
}

/** 复制一份对象，去掉指定的键（浅拷贝，只看自身可枚举的键） */
export function omitKeys<T extends Record<string, unknown>>(obj: T, keys: readonly string[]): Partial<T> {
  const out: Record<string, unknown> = {}
  Object.keys(obj).forEach((k) => {
    if (keys.indexOf(k) < 0) out[k] = obj[k]
  })
  return out as Partial<T>
}
