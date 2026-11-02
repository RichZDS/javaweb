/**
 * 单调时钟（毫秒）：不受改手机时间影响，用来推算考试的服务器时间。
 * H5 用 performance.now()；App 逻辑层没有 performance 时退回 Date.now()，
 * 这时页面每次回到前台都会重新向服务器取一次时间来校正。
 */
export function monoNow(): number {
  const perf = (globalThis as { performance?: { now?: () => number } }).performance
  return perf && typeof perf.now === 'function' ? perf.now() : Date.now()
}
