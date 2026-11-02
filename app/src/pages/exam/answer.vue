<template>
  <view class="page-root exam" :class="themeClass">
    <view v-if="state === 'loading'" class="empty">加载中…</view>
    <view v-else-if="state === 'error'" class="empty">
      <view>考试没加载出来</view>
      <button class="btn-plain retry" size="mini" @tap="load">重试</button>
    </view>

    <template v-else-if="sheet">
      <!-- 顶栏：倒计时、未同步数、答题卡、交卷 -->
      <view class="topbar">
        <view class="clock" :class="{ urgent: remaining <= 300, over: timeUp }">
          <uni-icons type="clock" size="16" color="inherit" />
          <text>{{ timeUp ? '时间到' : formatClock(remaining) }}</text>
        </view>
        <text class="sync" :class="{ warn: pendingCount > 0 }">
          {{ pendingCount > 0 ? `${pendingCount} 题未同步` : '答案已保存' }}
        </text>
        <text class="top-btn" @tap="openCard">答题卡</text>
        <text class="top-btn strong" @tap="submit(false)">交卷</text>
      </view>

      <view v-if="locked" class="locked-bar">考试时间已到，不能再作答</view>

      <swiper class="swiper" :style="{ height: swiperHeight - (locked ? 32 : 0) + 'px' }" :current="current" :duration="250" @change="onSwipe">
        <swiper-item v-for="(q, i) in sheet.questions" :key="q.questionId">
          <scroll-view scroll-y class="scroll">
            <view v-if="Math.abs(i - current) <= 1" class="qwrap">
              <view class="qmeta">
                <text class="score">{{ q.score }} 分</text>
                <text class="state" :class="saveState(q.questionId)">{{ SAVE_LABEL[saveState(q.questionId)] }}</text>
              </view>
              <QuestionBody
                :question="q"
                :model-value="answerOf(q.questionId, q.type, q.blankCount)"
                :locked="locked"
                :index-text="`第 ${i + 1} 题`"
                @update:model-value="(v: string[]) => change(q.questionId, v)"
              />
              <view class="scroll-space" />
            </view>
          </scroll-view>
        </swiper-item>
      </swiper>

      <view class="bottombar">
        <view class="bb-item" :class="{ disabled: current === 0 }" @tap="go(current - 1)">
          <uni-icons type="left" size="20" color="inherit" />
          <text>上一题</text>
        </view>
        <view class="bb-item" :class="{ on: isMarked(currentQ?.questionId) }" @tap="toggleMark">
          <uni-icons :type="isMarked(currentQ?.questionId) ? 'flag-filled' : 'flag'" size="20" color="inherit" />
          <text>稍后再看</text>
        </view>
        <view class="bb-item" @tap="openCard">
          <uni-icons type="list" size="20" color="inherit" />
          <text>答题卡</text>
        </view>
        <view class="bb-item" :class="{ disabled: current >= sheet.questions.length - 1 }" @tap="go(current + 1)">
          <uni-icons type="right" size="20" color="inherit" />
          <text>下一题</text>
        </view>
      </view>

      <uni-popup ref="cardPopup" type="bottom" background-color="transparent">
        <AnswerCard :statuses="statuses" :marked="markedList" :current="current" @jump="jump">
          <view class="card-foot">
            <text class="muted">已答 {{ answeredCount }} / {{ sheet.questions.length }}</text>
            <button class="btn-primary card-submit" size="mini" @tap="submit(false)">交卷</button>
          </view>
        </AnswerCard>
      </uni-popup>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef } from 'vue'
import { onBackPress, onHide, onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import { examApi, isExamSheet } from '@/api'
import type { ExamSheet, QuestionType } from '@/api/types'
import QuestionBody from '@/components/QuestionBody.vue'
import AnswerCard from '@/components/AnswerCard.vue'
import { useTheme } from '@/composables/useTheme'
import { cardStatus, emptyAnswer, hasAnyAnswer, type CardStatus } from '@/logic/answer'
import {
  ExamSyncer,
  isPending,
  makeAnchor,
  mergeDraft,
  remainingSeconds,
  type ClockAnchor,
  type ExamDraft,
} from '@/logic/exam'
import { formatClock } from '@/logic/format'
import { monoNow } from '@/utils/clock'
import { PAGES } from '@/utils/nav'

interface PopupRef {
  open: () => void
  close: () => void
}

type SaveState = 'none' | 'saved' | 'pending'
const SAVE_LABEL: Record<SaveState, string> = { none: '未作答', saved: '已保存', pending: '未同步' }

const { themeClass } = useTheme()

const recordId = ref(0)
const sheet = ref<ExamSheet | null>(null)
const draft = shallowRef<ExamDraft | null>(null)
const state = ref<'loading' | 'ready' | 'error'>('loading')
const current = ref(0)
const remaining = ref(0)
const timeUp = ref(false)
const locked = ref(false)
const submitting = ref(false)
const swiperHeight = ref(500)
const cardPopup = ref<PopupRef | null>(null)

let syncer: ExamSyncer | null = null
let anchor: ClockAnchor | null = null
let ticker: ReturnType<typeof setInterval> | null = null
let firstShow = true
let leaving = false

const storageKey = () => `quiz.exam.${recordId.value}`
const currentQ = computed(() => sheet.value?.questions[current.value])
const pendingCount = computed(() => Object.values(draft.value?.entries ?? {}).filter(isPending).length)
const answeredCount = computed(
  () => sheet.value?.questions.filter((q) => hasAnyAnswer(q.type, draft.value?.entries[q.questionId]?.userAnswer)).length ?? 0,
)
const statuses = computed<CardStatus[]>(
  () =>
    sheet.value?.questions.map((q) => cardStatus({ type: q.type, userAnswer: draft.value?.entries[q.questionId]?.userAnswer })) ??
    [],
)
const markedList = computed(() => sheet.value?.questions.map((q) => isMarked(q.questionId)) ?? [])

// ---------- 加载与恢复 ----------

onLoad((query) => {
  recordId.value = Number(query?.recordId)
  const info = uni.getSystemInfoSync()
  swiperHeight.value = info.windowHeight - 44 - 56 - (info.safeAreaInsets?.bottom ?? 0)
  load()
  uni.onNetworkStatusChange(onNetwork)
})

/** 回到前台：重新向服务器取一次时间校正倒计时（防止 App 端没有单调时钟时改了手机时间） */
onShow(() => {
  if (firstShow) {
    firstShow = false
    return
  }
  if (sheet.value && !locked.value) refreshClock()
  syncer?.online()
})

onHide(() => {
  if (draft.value) persist(draft.value)
})

onUnload(() => {
  stopTicker()
  syncer?.stop()
  uni.offNetworkStatusChange?.(onNetwork)
})

/** App 上按返回键 / 导航栏返回时提醒：计时不会暂停（代码里调用 navigateBack 不拦） */
onBackPress((e) => {
  if (leaving || locked.value || e.from !== 'backbutton') return false
  uni.showModal({
    title: '退出考试？',
    content: '退出后计时不会暂停，答案已保存在本机并会自动上传。可以从考试页「继续考试」回来。',
    confirmText: '退出',
    success: (r) => {
      if (r.confirm) {
        leaving = true
        uni.navigateBack()
      }
    },
  })
  return true
})

async function load() {
  state.value = 'loading'
  try {
    const before = monoNow()
    const data = await examApi.record(recordId.value)
    const after = monoNow()
    if (!isExamSheet(data)) {
      // 已经结算（交过卷或已过截止时间）
      clearLocal()
      uni.redirectTo({ url: `${PAGES.examResult}?recordId=${recordId.value}` })
      return
    }
    sheet.value = data
    uni.setNavigationBarTitle({ title: data.title })
    anchor = makeAnchor(data.serverTime, (before + after) / 2)

    // 本地草稿与服务器已存答案按题取版本大的
    let local: ExamDraft | null = null
    try {
      local = (uni.getStorageSync(storageKey()) as ExamDraft | '') || null
    } catch {
      local = null
    }
    const merged = mergeDraft(recordId.value, data.answers, local)
    draft.value = merged
    persist(merged)
    syncer = new ExamSyncer(merged, {
      send: (e) =>
        examApi.saveAnswer(recordId.value, e.questionId, {
          userAnswer: e.userAnswer.map(String),
          version: Number(e.version),
          marked: e.marked === true,
        }),
      fetchServer: async (questionId) => {
        const fresh = await examApi.record(recordId.value)
        return isExamSheet(fresh) ? fresh.answers.find((a) => a.questionId === questionId) ?? null : null
      },
      onDraft: (d) => {
        draft.value = d
        persist(d)
      },
      onExpired,
    })
    syncer.syncAll() // 离线时改过、还没传上去的答案
    state.value = 'ready'
    startTicker()
  } catch {
    state.value = 'error'
  }
}

async function refreshClock() {
  try {
    const before = monoNow()
    const data = await examApi.record(recordId.value)
    const after = monoNow()
    if (!isExamSheet(data)) {
      onExpired()
      return
    }
    anchor = makeAnchor(data.serverTime, (before + after) / 2)
    tick()
  } catch {
    /* 网络不好就继续用原来的锚点 */
  }
}

function persist(d: ExamDraft) {
  try {
    uni.setStorageSync(storageKey(), d)
  } catch {
    /* 存储满了也不影响在线同步 */
  }
}

function clearLocal() {
  try {
    uni.removeStorageSync(storageKey())
  } catch {
    /* 忽略 */
  }
}

// ---------- 倒计时 ----------

function startTicker() {
  stopTicker()
  tick()
  ticker = setInterval(tick, 1000)
}

function stopTicker() {
  if (ticker) clearInterval(ticker)
  ticker = null
}

function tick() {
  if (!sheet.value || !anchor) return
  remaining.value = remainingSeconds(sheet.value.deadlineAt, anchor, monoNow())
  if (remaining.value <= 0 && !timeUp.value) onTimeUp()
}

/** 到截止时间：锁定作答，尽量把已改的答案传上去，然后自动交卷 */
function onTimeUp() {
  timeUp.value = true
  locked.value = true
  syncer?.lock()
  stopTicker()
  uni.showToast({ title: '考试时间到，正在交卷', icon: 'none' })
  submit(true)
}

/** 保存接口返回 EXAM_EXPIRED：停止同步，提示已到时间，去成绩单 */
function onExpired() {
  if (leaving) return
  timeUp.value = true
  locked.value = true
  syncer?.stop()
  stopTicker()
  leaving = true
  uni.showModal({
    title: '已到考试时间',
    content: '截止时间已过，之后的答案不再接收，成绩以服务器收到的答案为准。',
    showCancel: false,
    confirmText: '看成绩',
    success: () => {
      clearLocal()
      uni.redirectTo({ url: `${PAGES.examResult}?recordId=${recordId.value}` })
    },
  })
}

// ---------- 作答 ----------

function answerOf(questionId: number, type: QuestionType, blankCount: number): string[] {
  return draft.value?.entries[questionId]?.userAnswer ?? emptyAnswer(type, blankCount)
}

function change(questionId: number, userAnswer: string[]) {
  if (!syncer?.change(questionId, { userAnswer })) {
    uni.showToast({ title: '考试时间已到，不能再作答', icon: 'none' })
  }
}

function isMarked(questionId?: number): boolean {
  return !!(questionId && draft.value?.entries[questionId]?.marked)
}

function toggleMark() {
  const q = currentQ.value
  if (!q || !syncer) return
  syncer.change(q.questionId, { marked: !isMarked(q.questionId) })
}

function saveState(questionId: number): SaveState {
  const e = draft.value?.entries[questionId]
  if (!e) return 'none'
  return isPending(e) ? 'pending' : 'saved'
}

function onNetwork(res: { isConnected: boolean }) {
  if (res.isConnected) syncer?.online()
}

// ---------- 切题 ----------

function onSwipe(e: { detail: { current: number } }) {
  current.value = e.detail.current
}

function go(index: number) {
  if (!sheet.value || index < 0 || index >= sheet.value.questions.length) return
  current.value = index
}

function jump(index: number) {
  cardPopup.value?.close()
  go(index)
}

function openCard() {
  cardPopup.value?.open()
}

// ---------- 交卷 ----------

function confirm(content: string, confirmText = '交卷'): Promise<boolean> {
  return new Promise((resolve) => {
    uni.showModal({ title: '交卷', content, confirmText, success: (r) => resolve(r.confirm), fail: () => resolve(false) })
  })
}

/**
 * 交卷：先把队列里的答案同步完；同步不完（断网）不交、不显示成功。
 * auto=true 是到时间自动交卷：不再询问未答题。
 */
async function submit(auto: boolean) {
  if (!sheet.value || !syncer || submitting.value) return
  cardPopup.value?.close()
  if (!auto) {
    const left = sheet.value.questions.length - answeredCount.value
    const marked = markedList.value.filter(Boolean).length
    const tips = [left > 0 ? `还有 ${left} 题没答` : '', marked > 0 ? `${marked} 题标了稍后再看` : ''].filter(Boolean).join('，')
    if (!(await confirm(tips ? `${tips}，确定交卷吗？` : '确定交卷吗？交卷后不能再修改。'))) return
  }
  submitting.value = true
  uni.showLoading({ title: '正在同步答案', mask: true })
  try {
    const allSynced = await syncer.flush()
    if (syncer.isStopped && !allSynced) return // EXAM_EXPIRED 已由 onExpired 处理
    if (!allSynced) {
      uni.hideLoading()
      if (auto) {
        uni.showModal({
          title: '考试时间已到',
          content: `有 ${pendingCount.value} 题答案没能传到服务器（网络不好）。成绩由服务器按截止前收到的答案自动结算，联网后在考试记录里查看。`,
          showCancel: false,
        })
      } else {
        uni.showModal({
          title: '还不能交卷',
          content: `有 ${pendingCount.value} 题答案还没同步到服务器（网络不好）。答案已存在本机，联网后会自动上传，请稍后再交卷。`,
          showCancel: false,
        })
      }
      return
    }
    uni.showLoading({ title: '正在交卷', mask: true })
    await examApi.submit(recordId.value)
    uni.hideLoading()
    syncer.stop()
    clearLocal()
    leaving = true
    uni.redirectTo({ url: `${PAGES.examResult}?recordId=${recordId.value}` })
  } catch {
    // 交卷请求失败（网络等）：请求层已提示，不显示成功，可以再点交卷（后端只结算一次）
    uni.hideLoading()
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.exam {
  min-height: auto;
}

.topbar {
  height: 44px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  background: var(--c-card);
  border-bottom: 1px solid var(--c-border);
  box-sizing: border-box;
}

.clock {
  display: flex;
  align-items: center;
  gap: 4px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--c-text);

  &.urgent {
    color: var(--c-warn);
  }

  &.over {
    color: var(--c-wrong);
  }
}

.sync {
  flex: 1;
  font-size: var(--fs-xs);
  color: var(--c-correct);

  &.warn {
    color: var(--c-warn);
  }
}

.top-btn {
  font-size: var(--fs-sm);
  color: var(--c-text-2);
  padding: 4px 6px;

  &.strong {
    color: var(--c-primary);
    font-weight: 600;
  }
}

.locked-bar {
  /* 高度 32px，模板里 swiper 高度会相应减掉 */
  height: 32px;
  line-height: 32px;
  box-sizing: border-box;
  padding: 0 12px;
  background: var(--c-wrong-weak);
  color: var(--c-wrong);
  font-size: var(--fs-sm);
  text-align: center;
}

.scroll {
  height: 100%;
}

.qwrap {
  padding: 12px 16px 0;
}

.qmeta {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
  font-size: var(--fs-xs);
}

.score {
  color: var(--c-text-3);
}

.state {
  &.saved {
    color: var(--c-correct);
  }

  &.pending {
    color: var(--c-warn);
  }

  &.none {
    color: var(--c-text-3);
  }
}

.scroll-space {
  height: 24px;
}

.bottombar {
  height: 56px;
  padding-bottom: env(safe-area-inset-bottom);
  display: flex;
  background: var(--c-card);
  border-top: 1px solid var(--c-border);
}

.bb-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-size: var(--fs-xs);
  color: var(--c-text-2);

  &.disabled {
    opacity: 0.35;
  }

  &.on {
    color: var(--c-warn);
  }
}

.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
  padding-bottom: env(safe-area-inset-bottom);
}

.card-submit {
  margin: 0;
}

.retry {
  margin-top: 12px;
}
</style>
