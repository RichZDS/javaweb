import { describe, expect, it } from 'vitest'
import { omitKeys, settleAll } from '@/utils/compat'

describe('settleAll', () => {
  it('成功和失败都收下，顺序和传入一致', async () => {
    const boom = new Error('网络')
    const res = await settleAll([Promise.resolve(1), Promise.reject(boom), 'x'])
    expect(res).toEqual([
      { status: 'fulfilled', value: 1 },
      { status: 'rejected', reason: boom },
      { status: 'fulfilled', value: 'x' },
    ])
  })

  it('等最慢的那个，不因为一个失败提前返回', async () => {
    let slowDone = false
    const slow = new Promise<string>((resolve) =>
      setTimeout(() => {
        slowDone = true
        resolve('慢')
      }, 20),
    )
    const res = await settleAll([Promise.reject(new Error('快速失败')), slow])
    expect(slowDone).toBe(true)
    expect(res[1]).toEqual({ status: 'fulfilled', value: '慢' })
  })

  it('空数组直接得到空结果', async () => {
    expect(await settleAll([])).toEqual([])
  })
})

describe('omitKeys', () => {
  it('去掉指定的键，其他原样保留，不改原对象', () => {
    const src = { legend: { show: true }, xAxis: {}, dataLabel: true, padding: [1, 2] }
    expect(omitKeys(src, ['legend', 'xAxis'])).toEqual({ dataLabel: true, padding: [1, 2] })
    expect(Object.keys(src)).toEqual(['legend', 'xAxis', 'dataLabel', 'padding'])
  })
})

/**
 * App 逻辑层在 iOS 上用系统的 JavaScriptCore，下面这些内置函数 iOS 12.0 上没有（括号里是开始有的版本），
 * 调用就会抛错；flat / flatMap 是 12.0 刚加的，保守起见也不用。构建不会补，所以源码里不许出现，
 * 改用 src/utils/compat.ts 或普通写法。
 */
const NEWER_THAN_IOS12: [string, RegExp][] = [
  ['Promise.allSettled（iOS 13），用 settleAll', /\bPromise\.allSettled\b/],
  ['Promise.any（iOS 14）', /\bPromise\.any\b/],
  ['Object.fromEntries（iOS 12.2），用 omitKeys 或 forEach', /\bObject\.fromEntries\b/],
  ['Object.hasOwn（iOS 15.4），用 Object.prototype.hasOwnProperty.call', /\bObject\.hasOwn\b/],
  ['Object.groupBy（iOS 17.4）', /\bObject\.groupBy\b/],
  ['globalThis（iOS 12.2），用 typeof 判断全局变量', /\bglobalThis\b/],
  ['structuredClone（iOS 15.4）', /\bstructuredClone\b/],
  ['queueMicrotask（iOS 12.2），用 Promise.resolve().then', /\bqueueMicrotask\b/],
  ['WeakRef（iOS 14.5）', /\bWeakRef\b/],
  ['String.prototype.replaceAll（iOS 13.4），用带 g 的正则', /\.replaceAll\(/],
  ['String.prototype.matchAll（iOS 13）', /\.matchAll\(/],
  ['Array/String.prototype.at（iOS 15.4），用下标', /\.at\(/],
  ['findLast / findLastIndex（iOS 15.4）', /\.findLast(Index)?\(/],
  ['toSorted 等（iOS 16）', /\.(toSorted|toReversed|toSpliced)\(/],
  ['flat / flatMap（iOS 12.0），用 reduce + concat', /\.flatMap\(|\.flat\(/],
  ['正则后行断言（iOS 16.4），整个文件会解析失败', /\(\?<[=!]/],
]

const sources = import.meta.glob('../src/**/*.{ts,vue}', { query: '?raw', import: 'default', eager: true }) as Record<
  string,
  string
>

describe('源码不用 iOS 12 没有的内置函数', () => {
  it('扫到了源码', () => {
    expect(Object.keys(sources).length).toBeGreaterThan(30)
  })

  it.each(NEWER_THAN_IOS12)('不用 %s', (hint, pattern) => {
    const hits = Object.entries(sources).flatMap(([file, text]) =>
      text
        .split('\n')
        .map((line, i) => ({ line, at: `${file}:${i + 1}` }))
        .filter(({ line }) => pattern.test(line))
        .map(({ at, line }) => `${at}  ${line.trim()}`),
    )
    expect(hits, `不能用 ${hint}`).toEqual([])
  })
})
