import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  ExamSyncer,
  adoptServer,
  applyChange,
  applySaveResult,
  emptyDraft,
  isOverdue,
  makeAnchor,
  mergeDraft,
  pendingEntries,
  remainingSeconds,
  retryDelay,
  type DraftEntry,
  type ExamDraft,
  type SaveResult,
  type ServerAnswer,
} from '@/logic/exam'

const srv = (questionId: number, version: number, userAnswer: string[] = ['A'], marked = false): ServerAnswer => ({
  questionId,
  version,
  userAnswer,
  marked,
})

describe('版本递增', () => {
  it('每次改答案版本 +1，标为待同步，不改原草稿', () => {
    const d0 = emptyDraft(5)
    const d1 = applyChange(d0, 101, { userAnswer: ['A'] })
    const d2 = applyChange(d1, 101, { userAnswer: ['B'] })
    expect(d0.entries[101]).toBeUndefined()
    expect(d1.entries[101].version).toBe(1)
    expect(d2.entries[101]).toMatchObject({ version: 2, syncedVersion: 0, userAnswer: ['B'] })
    expect(pendingEntries(d2).map((e) => e.questionId)).toEqual([101])
  })

  it('只改「稍后再看」也算一次新版本，答案保持不变', () => {
    const d1 = applyChange(emptyDraft(5), 101, { userAnswer: ['C'] })
    const d2 = applyChange(d1, 101, { marked: true })
    expect(d2.entries[101]).toMatchObject({ version: 2, marked: true, userAnswer: ['C'] })
  })
})

describe('回包处理', () => {
  it('accepted:true 且之后没再改：已同步', () => {
    const d = applyChange(emptyDraft(5), 101, { userAnswer: ['A'] })
    const r = applySaveResult(d, 101, 1, { accepted: true, version: 1 })
    expect(r.outcome).toBe('synced')
    expect(pendingEntries(r.draft)).toHaveLength(0)
  })

  it('accepted:true 但发出后又改过：需要再发', () => {
    let d = applyChange(emptyDraft(5), 101, { userAnswer: ['A'] })
    d = applyChange(d, 101, { userAnswer: ['B'] }) // 版本 2 还在本地
    const r = applySaveResult(d, 101, 1, { accepted: true, version: 1 })
    expect(r.outcome).toBe('resend')
    expect(r.draft.entries[101]).toMatchObject({ version: 2, syncedVersion: 1 })
  })

  it('旧版本回包 accepted:false：以服务器为准，不再重发', () => {
    const d = applyChange(emptyDraft(5), 101, { userAnswer: ['A'] })
    const r = applySaveResult(d, 101, 1, { accepted: false, version: 4 })
    expect(r.outcome).toBe('server-newer')
    expect(r.draft.entries[101]).toMatchObject({ version: 4, syncedVersion: 4 })
    expect(pendingEntries(r.draft)).toHaveLength(0)
    const adopted = adoptServer(r.draft, srv(101, 4, ['D'], true))
    expect(adopted.entries[101]).toMatchObject({ userAnswer: ['D'], marked: true, version: 4, syncedVersion: 4 })
  })
})

describe('草稿与服务器合并', () => {
  const local: ExamDraft = {
    recordId: 5,
    entries: {
      101: { questionId: 101, userAnswer: ['B'], marked: false, version: 3, syncedVersion: 2 }, // 本地更新
      102: { questionId: 102, userAnswer: ['A'], marked: false, version: 1, syncedVersion: 1 }, // 服务器更新
      103: { questionId: 103, userAnswer: ['x'], marked: true, version: 1, syncedVersion: 0 }, // 服务器没有
    },
  }

  it('每题取版本大的；本地更新的保留为待同步', () => {
    const merged = mergeDraft(5, [srv(101, 2, ['A']), srv(102, 3, ['C']), srv(104, 1, ['T'])], local)
    expect(merged.entries[101]).toMatchObject({ userAnswer: ['B'], version: 3, syncedVersion: 2 })
    expect(merged.entries[102]).toMatchObject({ userAnswer: ['C'], version: 3, syncedVersion: 3 })
    expect(merged.entries[103]).toMatchObject({ userAnswer: ['x'], version: 1, syncedVersion: 0 })
    expect(merged.entries[104]).toMatchObject({ userAnswer: ['T'], version: 1, syncedVersion: 1 })
    expect(pendingEntries(merged).map((e) => e.questionId).sort()).toEqual([101, 103])
  })

  it('版本相同用服务器的', () => {
    const merged = mergeDraft(5, [srv(101, 3, ['A'])], local)
    expect(merged.entries[101]).toMatchObject({ userAnswer: ['A'], syncedVersion: 3 })
  })

  it('别的考试记录的草稿整份丢弃', () => {
    const merged = mergeDraft(6, [srv(101, 1)], local)
    expect(Object.keys(merged.entries)).toEqual(['101'])
    expect(merged.recordId).toBe(6)
  })

  it('没有本地草稿时完全按服务器', () => {
    const merged = mergeDraft(5, [srv(101, 2)], null)
    expect(pendingEntries(merged)).toHaveLength(0)
  })
})

describe('倒计时按服务器时间', () => {
  const deadline = '2026-11-02T09:00:00.000Z'

  it('剩余秒数只取决于服务器时间和单调时钟流逝', () => {
    const anchor = makeAnchor('2026-11-02T08:59:00.000Z', 1000) // 单调时钟 1000ms 时服务器说还剩 60s
    expect(remainingSeconds(deadline, anchor, 1000)).toBe(60)
    expect(remainingSeconds(deadline, anchor, 31_000)).toBe(30)
    expect(remainingSeconds(deadline, anchor, 60_500)).toBe(1)
    expect(remainingSeconds(deadline, anchor, 61_000)).toBe(0)
    expect(isOverdue(deadline, anchor, 60_999)).toBe(false)
    expect(isOverdue(deadline, anchor, 61_000)).toBe(true)
  })

  it('手机时间改了也不影响（计算里不用 Date.now）', () => {
    const spy = vi.spyOn(Date, 'now').mockReturnValue(Date.parse('2030-01-01T00:00:00Z'))
    const anchor = makeAnchor('2026-11-02T08:59:00.000Z', 0)
    expect(remainingSeconds(deadline, anchor, 10_000)).toBe(50)
    spy.mockRestore()
  })

  it('重试等待指数退避，最长 30 秒', () => {
    expect([0, 1, 2, 3, 10].map(retryDelay)).toEqual([1000, 2000, 4000, 8000, 30_000])
  })
})

/** 可控的假接口：每次 send 返回一个由测试决定结果的 promise */
function fakeServer() {
  const calls: { entry: DraftEntry; resolve: (r: SaveResult) => void; reject: (e: unknown) => void }[] = []
  const send = vi.fn(
    (entry: DraftEntry) =>
      new Promise<SaveResult>((resolve, reject) => {
        calls.push({ entry: { ...entry }, resolve, reject })
      }),
  )
  return { calls, send }
}

describe('ExamSyncer 同步队列', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  function setup(draft: ExamDraft = emptyDraft(5)) {
    const server = fakeServer()
    const onDraft = vi.fn()
    const onExpired = vi.fn()
    const fetchServer = vi.fn(async (qid: number) => srv(qid, 9, ['Z']))
    const syncer = new ExamSyncer(draft, { send: server.send, fetchServer, onDraft, onExpired })
    return { syncer, server, onDraft, onExpired, fetchServer }
  }

  it('先存本地（onDraft）再发保存请求', () => {
    const { syncer, server, onDraft } = setup()
    syncer.change(101, { userAnswer: ['A'] })
    expect(onDraft).toHaveBeenCalledTimes(1)
    expect(onDraft.mock.calls[0][0].entries[101].version).toBe(1)
    expect(server.send).toHaveBeenCalledTimes(1)
  })

  it('请求在路上时又改：等回包后只发最新版本', async () => {
    const { syncer, server } = setup()
    syncer.change(101, { userAnswer: ['A'] })
    syncer.change(101, { userAnswer: ['B'] })
    syncer.change(101, { userAnswer: ['C'] })
    expect(server.send).toHaveBeenCalledTimes(1)
    server.calls[0].resolve({ accepted: true, version: 1 })
    await vi.advanceTimersByTimeAsync(0)
    expect(server.send).toHaveBeenCalledTimes(2)
    expect(server.calls[1].entry).toMatchObject({ version: 3, userAnswer: ['C'] })
    server.calls[1].resolve({ accepted: true, version: 3 })
    await vi.advanceTimersByTimeAsync(0)
    expect(pendingEntries(syncer.draft)).toHaveLength(0)
  })

  it('断网：留在队列，按退避重试，恢复后补传成功', async () => {
    const { syncer, server } = setup()
    syncer.change(101, { userAnswer: ['A'] })
    server.calls[0].reject({ code: 'NETWORK' })
    await vi.advanceTimersByTimeAsync(0)
    expect(pendingEntries(syncer.draft)).toHaveLength(1)
    expect(server.send).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1000) // 第一次重试
    expect(server.send).toHaveBeenCalledTimes(2)
    server.calls[1].reject({ code: 'NETWORK' })
    await vi.advanceTimersByTimeAsync(1999)
    expect(server.send).toHaveBeenCalledTimes(2) // 第二次等 2s
    await vi.advanceTimersByTimeAsync(1)
    expect(server.send).toHaveBeenCalledTimes(3)
    server.calls[2].resolve({ accepted: true, version: 1 })
    await vi.advanceTimersByTimeAsync(0)
    expect(pendingEntries(syncer.draft)).toHaveLength(0)
  })

  it('网络恢复事件：不等退避，立即补传', async () => {
    const { syncer, server } = setup()
    syncer.change(101, { userAnswer: ['A'] })
    server.calls[0].reject({ code: 'NETWORK' })
    await vi.advanceTimersByTimeAsync(0)
    syncer.online()
    expect(server.send).toHaveBeenCalledTimes(2)
  })

  it('旧版本回包：取回服务器答案覆盖本地', async () => {
    const { syncer, server, fetchServer } = setup()
    syncer.change(101, { userAnswer: ['A'] })
    server.calls[0].resolve({ accepted: false, version: 9 })
    await vi.advanceTimersByTimeAsync(0)
    expect(fetchServer).toHaveBeenCalledWith(101)
    expect(syncer.draft.entries[101]).toMatchObject({ userAnswer: ['Z'], version: 9, syncedVersion: 9 })
    // 之后再改从服务器版本往上加
    syncer.change(101, { userAnswer: ['B'] })
    expect(server.calls[1].entry.version).toBe(10)
  })

  it('EXAM_EXPIRED：停止同步并通知，之后不再接受作答', async () => {
    const { syncer, server, onExpired } = setup()
    syncer.change(101, { userAnswer: ['A'] })
    syncer.change(102, { userAnswer: ['B'] })
    server.calls[0].reject({ code: 'EXAM_EXPIRED' })
    await vi.advanceTimersByTimeAsync(0)
    expect(onExpired).toHaveBeenCalledTimes(1)
    expect(syncer.isStopped).toBe(true)
    expect(syncer.change(103, { userAnswer: ['C'] })).toBe(false)
    await vi.advanceTimersByTimeAsync(60_000)
    expect(server.send).toHaveBeenCalledTimes(2) // 不会再重试
  })

  it('截止锁定：不再接受作答，已有的待同步答案仍会上传', async () => {
    const { syncer, server } = setup()
    syncer.change(101, { userAnswer: ['A'] })
    syncer.lock()
    expect(syncer.change(101, { userAnswer: ['B'] })).toBe(false)
    expect(syncer.draft.entries[101].userAnswer).toEqual(['A'])
    server.calls[0].resolve({ accepted: true, version: 1 })
    await vi.advanceTimersByTimeAsync(0)
    expect(pendingEntries(syncer.draft)).toHaveLength(0)
  })

  it('交卷前 flush：全部传上去才返回 true', async () => {
    const { syncer, server } = setup()
    syncer.change(101, { userAnswer: ['A'] })
    syncer.change(102, { userAnswer: ['B'] })
    const done = syncer.flush()
    server.calls.forEach((c) => c.resolve({ accepted: true, version: c.entry.version }))
    await expect(done).resolves.toBe(true)
  })

  it('交卷前 flush：断网时返回 false（不能显示交卷成功）', async () => {
    const { syncer, server } = setup()
    syncer.change(101, { userAnswer: ['A'] })
    server.calls[0].reject({ code: 'NETWORK' })
    await vi.advanceTimersByTimeAsync(0)
    const done = syncer.flush()
    await vi.advanceTimersByTimeAsync(0)
    server.calls[server.calls.length - 1].reject({ code: 'NETWORK' })
    await expect(done).resolves.toBe(false)
  })

  it('重新进入：合并后的本地新答案自动补传', () => {
    const merged = mergeDraft(5, [srv(101, 1, ['A'])], {
      recordId: 5,
      entries: { 101: { questionId: 101, userAnswer: ['C'], marked: false, version: 2, syncedVersion: 1 } },
    })
    const { syncer, server } = setup(merged)
    syncer.syncAll()
    expect(server.calls[0].entry).toMatchObject({ questionId: 101, version: 2, userAnswer: ['C'] })
  })
})
