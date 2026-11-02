/**
 * 考试答题的核心规则（纯逻辑，不碰 uni API，方便单元测试）：
 * - 每题每次改答案版本号 +1，先存本地，再同步；服务器只收比已存版本大的
 * - 收到 accepted:false 以服务器为准；收到 EXAM_EXPIRED 停止同步
 * - 断网时留在队列里，退避重试，网络恢复后立即补传
 * - 重新打开 App：本地草稿和服务器答案按题取版本大的
 * - 倒计时按服务器时间走：用一次 serverTime 和本机单调时钟做锚点，改手机时间不影响
 */

export interface ServerAnswer {
  questionId: number
  userAnswer: string[]
  version: number
  marked: boolean
  savedAt?: string
}

export interface DraftEntry {
  questionId: number
  userAnswer: string[]
  marked: boolean
  /** 本地最新版本 */
  version: number
  /** 服务器已确认的版本，0 表示从没同步过 */
  syncedVersion: number
}

export interface ExamDraft {
  recordId: number
  entries: Record<number, DraftEntry>
}

export interface SaveResult {
  accepted: boolean
  version: number
}

export function emptyDraft(recordId: number): ExamDraft {
  return { recordId, entries: {} }
}

export function isPending(entry: DraftEntry): boolean {
  return entry.version > entry.syncedVersion
}

export function pendingEntries(draft: ExamDraft): DraftEntry[] {
  return Object.values(draft.entries).filter(isPending)
}

/**
 * 重新进入考试时合并：每题取版本大的。
 * - 服务器版本 ≥ 本地：用服务器的，记为已同步
 * - 本地版本更大（离线改过还没传上去）：保留本地，等待补传
 * 草稿属于别的考试记录时整份丢弃。
 */
export function mergeDraft(recordId: number, server: readonly ServerAnswer[], local: ExamDraft | null | undefined): ExamDraft {
  const own = local && local.recordId === recordId ? local.entries : {}
  const entries: Record<number, DraftEntry> = {}
  const serverById = new Map(server.map((a) => [a.questionId, a]))
  const ids = new Set<number>([...serverById.keys(), ...Object.keys(own).map(Number)])
  for (const id of ids) {
    const s = serverById.get(id)
    const l = own[id]
    if (s && (!l || s.version >= l.version)) {
      entries[id] = { questionId: id, userAnswer: [...s.userAnswer], marked: s.marked, version: s.version, syncedVersion: s.version }
    } else if (l) {
      // 服务器没有这题，或者版本比本地旧：本地的还需要补传
      entries[id] = { ...l, userAnswer: [...l.userAnswer], syncedVersion: s ? s.version : 0 }
    }
  }
  return { recordId, entries }
}

/** 改答案或改「稍后再看」：版本 +1，标记为待同步。返回新对象，不改原草稿 */
export function applyChange(
  draft: ExamDraft,
  questionId: number,
  patch: { userAnswer?: string[]; marked?: boolean },
): ExamDraft {
  const prev = draft.entries[questionId] ?? { questionId, userAnswer: [], marked: false, version: 0, syncedVersion: 0 }
  const next: DraftEntry = {
    ...prev,
    userAnswer: patch.userAnswer ? [...patch.userAnswer] : prev.userAnswer,
    marked: patch.marked ?? prev.marked,
    version: prev.version + 1,
  }
  return { ...draft, entries: { ...draft.entries, [questionId]: next } }
}

export type SaveOutcome =
  /** 服务器收下了，本地没有更新的改动 */
  | 'synced'
  /** 服务器收下了，但发出后本地又改过，需要再传一次 */
  | 'resend'
  /** 服务器已有同样或更新的版本：以服务器为准，需要取回服务器上的答案 */
  | 'server-newer'

/** 处理一次保存接口的回包 */
export function applySaveResult(
  draft: ExamDraft,
  questionId: number,
  sentVersion: number,
  result: SaveResult,
): { draft: ExamDraft; outcome: SaveOutcome } {
  const entry = draft.entries[questionId]
  if (!entry) return { draft, outcome: 'synced' }
  if (result.accepted) {
    const syncedVersion = Math.max(entry.syncedVersion, sentVersion)
    const next = { ...entry, syncedVersion }
    return {
      draft: { ...draft, entries: { ...draft.entries, [questionId]: next } },
      outcome: next.version > syncedVersion ? 'resend' : 'synced',
    }
  }
  // 旧版本回包：服务器上的版本不比我们发的小。先把版本对齐到服务器，避免反复重发，再由调用方取回内容
  const serverVersion = Math.max(result.version, sentVersion)
  const next = { ...entry, version: Math.max(entry.version, serverVersion), syncedVersion: Math.max(entry.version, serverVersion) }
  return { draft: { ...draft, entries: { ...draft.entries, [questionId]: next } }, outcome: 'server-newer' }
}

/** 用服务器上的答案覆盖本地（accepted:false 之后取回的内容） */
export function adoptServer(draft: ExamDraft, server: ServerAnswer): ExamDraft {
  const entry: DraftEntry = {
    questionId: server.questionId,
    userAnswer: [...server.userAnswer],
    marked: server.marked,
    version: server.version,
    syncedVersion: server.version,
  }
  return { ...draft, entries: { ...draft.entries, [server.questionId]: entry } }
}

/** 断网重试的等待时间：1s、2s、4s… 最长 30s */
export function retryDelay(attempt: number): number {
  return Math.min(30_000, 1000 * 2 ** Math.max(0, attempt))
}

// ---------- 倒计时 ----------

export interface ClockAnchor {
  /** 锚点时刻的服务器时间（毫秒） */
  serverMs: number
  /** 同一时刻本机单调时钟读数（毫秒） */
  monoMs: number
}

/** 用接口返回的 serverTime 建锚点；monoAtResponse 是收到回包时的单调时钟读数 */
export function makeAnchor(serverTimeIso: string, monoAtResponse: number): ClockAnchor {
  return { serverMs: Date.parse(serverTimeIso), monoMs: monoAtResponse }
}

export function serverNow(anchor: ClockAnchor, monoNow: number): number {
  return anchor.serverMs + (monoNow - anchor.monoMs)
}

/** 剩余秒数（向上取整，到点时为 0） */
export function remainingSeconds(deadlineIso: string, anchor: ClockAnchor, monoNow: number): number {
  const ms = Date.parse(deadlineIso) - serverNow(anchor, monoNow)
  return ms <= 0 ? 0 : Math.ceil(ms / 1000)
}

export function isOverdue(deadlineIso: string, anchor: ClockAnchor, monoNow: number): boolean {
  return serverNow(anchor, monoNow) >= Date.parse(deadlineIso)
}

// ---------- 同步队列 ----------

export interface SyncDeps {
  /** 调保存接口；失败时 reject 一个带 code 的错误（EXAM_EXPIRED / NETWORK / …） */
  send: (entry: DraftEntry) => Promise<SaveResult>
  /** 取回某题在服务器上的答案（accepted:false 时用） */
  fetchServer: (questionId: number) => Promise<ServerAnswer | null>
  /** 每次草稿变化都调用：先存本地，再刷新界面 */
  onDraft: (draft: ExamDraft) => void
  /** 服务器说已过截止时间 */
  onExpired: () => void
  setTimer?: (fn: () => void, ms: number) => unknown
  clearTimer?: (handle: unknown) => void
}

/**
 * 每题最多一个请求在路上；发出后又改了就等回包再发最新版本。
 * 失败（非截止）进入重试：退避定时器，或 online() 时立即补传。
 */
export class ExamSyncer {
  private draftValue: ExamDraft
  private readonly inFlight = new Map<number, Promise<void>>()
  private stopped = false
  private lockedValue = false
  private retryAttempt = 0
  private retryHandle: unknown = null
  private readonly setTimer: (fn: () => void, ms: number) => unknown
  private readonly clearTimer: (handle: unknown) => void

  constructor(draft: ExamDraft, private readonly deps: SyncDeps) {
    this.draftValue = draft
    this.setTimer = deps.setTimer ?? ((fn, ms) => setTimeout(fn, ms))
    this.clearTimer = deps.clearTimer ?? ((h) => clearTimeout(h as ReturnType<typeof setTimeout>))
  }

  get draft(): ExamDraft {
    return this.draftValue
  }

  get locked(): boolean {
    return this.lockedValue
  }

  get isStopped(): boolean {
    return this.stopped
  }

  /** 作答。已锁定（到截止时间）时返回 false，不记录 */
  change(questionId: number, patch: { userAnswer?: string[]; marked?: boolean }): boolean {
    if (this.lockedValue || this.stopped) return false
    this.update(applyChange(this.draftValue, questionId, patch))
    this.kick(questionId)
    return true
  }

  /** 到截止时间：不再接受作答，但已有的待同步答案仍会尝试上传（服务器按截止时间裁决） */
  lock(): void {
    this.lockedValue = true
  }

  /** 停止一切同步（EXAM_EXPIRED、离开页面、已交卷） */
  stop(): void {
    this.stopped = true
    if (this.retryHandle !== null) this.clearTimer(this.retryHandle)
    this.retryHandle = null
  }

  /** 网络恢复：立即补传 */
  online(): void {
    if (this.stopped) return
    this.retryAttempt = 0
    this.syncAll()
  }

  /** 把所有待同步的都发出去 */
  syncAll(): void {
    if (this.stopped) return
    if (this.retryHandle !== null) {
      this.clearTimer(this.retryHandle)
      this.retryHandle = null
    }
    for (const entry of pendingEntries(this.draftValue)) this.kick(entry.questionId)
  }

  /**
   * 交卷前调用：发出全部待同步答案并等它们回来。
   * 返回 true 表示全部已同步；false 表示还有没传上去的（如断网），此时不能显示交卷成功。
   */
  async flush(): Promise<boolean> {
    this.syncAll()
    while (this.inFlight.size > 0) {
      await Promise.all([...this.inFlight.values()])
      // 停止了，或有请求失败进了重试（如断网），说明暂时传不上
      if (this.stopped || this.retryHandle !== null) break
      this.syncAll()
    }
    return pendingEntries(this.draftValue).length === 0
  }

  private update(draft: ExamDraft): void {
    this.draftValue = draft
    this.deps.onDraft(draft)
  }

  private kick(questionId: number): void {
    if (this.stopped || this.inFlight.has(questionId)) return
    const entry = this.draftValue.entries[questionId]
    if (!entry || !isPending(entry)) return
    const release = (again: boolean) => {
      this.inFlight.delete(questionId)
      // 回包前又改过：释放在途标记之后再发最新版本
      if (again) this.kick(questionId)
    }
    const task = this.sendOne(entry).then(release, () => release(false))
    this.inFlight.set(questionId, task)
  }

  /** 发一次；返回 true 表示发出后本地又改过，需要再发 */
  private async sendOne(entry: DraftEntry): Promise<boolean> {
    const sentVersion = entry.version
    let result: SaveResult
    try {
      result = await this.deps.send(entry)
    } catch (err) {
      if ((err as { code?: string })?.code === 'EXAM_EXPIRED') {
        this.stop()
        this.deps.onExpired()
      } else {
        this.scheduleRetry()
      }
      return false
    }
    if (this.stopped) return false
    this.retryAttempt = 0
    const { draft, outcome } = applySaveResult(this.draftValue, entry.questionId, sentVersion, result)
    this.update(draft)
    if (outcome === 'server-newer') {
      try {
        const server = await this.deps.fetchServer(entry.questionId)
        if (server && !this.stopped) this.update(adoptServer(this.draftValue, server))
      } catch {
        /* 取不回来就先用对齐后的版本，下次进入考试时会再合并 */
      }
    }
    return outcome === 'resend'
  }

  private scheduleRetry(): void {
    if (this.stopped || this.retryHandle !== null) return
    const delay = retryDelay(this.retryAttempt++)
    this.retryHandle = this.setTimer(() => {
      this.retryHandle = null
      this.syncAll()
    }, delay)
  }
}
