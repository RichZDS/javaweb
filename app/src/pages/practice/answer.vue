<template>
  <view class="page-root answer" :class="themeClass">
    <view v-if="state === 'loading'" class="empty">加载中…</view>
    <view v-else-if="state === 'error'" class="empty">
      <view>练习没加载出来</view>
      <button class="btn-plain retry" size="mini" @tap="load">重试</button>
    </view>

    <template v-else-if="session">
      <!-- 顶栏：进度、答题卡、结束 -->
      <view class="topbar">
        <text class="progress-text">{{ current + 1 }} / {{ questions.length }}</text>
        <view class="progress-bar"><view class="progress-fill" :style="{ width: doneRate + '%' }" /></view>
        <text class="top-btn" @tap="openCard">答题卡</text>
        <text class="top-btn strong" @tap="finish">{{ finishLabel }}</text>
      </view>

      <view v-if="ended" class="ended-bar">这次练习已结束，正在回看，不能再作答</view>

      <swiper class="swiper" :style="{ height: swiperHeight - (ended ? ENDED_BAR_HEIGHT : 0) + 'px' }" :current="current" :duration="250" @change="onSwipe">
        <swiper-item v-for="(q, i) in questions" :key="q.id">
          <scroll-view scroll-y class="scroll">
            <!-- 只渲染当前题和左右两题，题多时也不卡 -->
            <view v-if="Math.abs(i - current) <= 1" class="qwrap">
              <QuestionBody
                :question="q"
                :model-value="shownAnswer(q)"
                :locked="isLocked(q)"
                :correct-answer="isLocked(q) && q.type !== 'FILL' ? q.correctAnswer ?? null : null"
                :index-text="`第 ${i + 1} 题`"
                @update:model-value="(v: string[]) => (drafts[q.id] = v)"
              />

              <button
                v-if="!isLocked(q)"
                class="btn-primary submit"
                :disabled="!canSubmit(q) || submittingId === q.id"
                :loading="submittingId === q.id"
                @tap="submit(q)"
              >
                提交答案
              </button>

              <!-- 已结束的练习里没交的题：没有答案数据，明确标为未作答 -->
              <view v-if="ended && !q.submitted && !session.reciteMode" class="unanswered">
                <text class="unanswered-title">未作答</text>
                <text class="muted">这题在练习结束前没有提交，不计入作答和正确率。</text>
              </view>

              <AnswerReveal
                v-else-if="isLocked(q)"
                :type="q.type"
                :correct="q.correct"
                :user-answer="q.userAnswer"
                :correct-answer="q.correctAnswer ?? null"
                :accepted-answers="q.acceptedAnswers ?? null"
                :analysis-html="q.analysisHtml ?? null"
                :recite="session.reciteMode"
              />

              <view v-if="q.note" class="note">
                <text class="note-label">我的笔记</text>
                <text class="note-text">{{ q.note }}</text>
              </view>

              <button v-if="(finished || ended) && i === questions.length - 1" class="btn-plain submit" @tap="finish">
                查看本次结果
              </button>
              <view class="scroll-space" />
            </view>
          </scroll-view>
        </swiper-item>
      </swiper>

      <!-- 底栏：上一题、收藏、笔记、纠错、下一题 -->
      <view class="bottombar">
        <view class="bb-item" :class="{ disabled: current === 0 }" @tap="go(current - 1)">
          <uni-icons type="left" size="20" color="inherit" />
          <text>上一题</text>
        </view>
        <view class="bb-item" @tap="toggleFavorite">
          <uni-icons :type="currentQ?.favorited ? 'star-filled' : 'star'" size="20" :color="currentQ?.favorited ? '#f0a020' : 'inherit'" />
          <text>{{ currentQ?.favorited ? '已收藏' : '收藏' }}</text>
        </view>
        <view class="bb-item" @tap="openNote">
          <uni-icons type="compose" size="20" color="inherit" />
          <text>笔记</text>
        </view>
        <view class="bb-item" @tap="openFeedback">
          <uni-icons type="help" size="20" color="inherit" />
          <text>纠错</text>
        </view>
        <view class="bb-item" @tap="next">
          <uni-icons type="right" size="20" color="inherit" />
          <text>下一题</text>
        </view>
      </view>

      <!-- 答题卡 -->
      <uni-popup ref="cardPopup" type="bottom" background-color="transparent">
        <AnswerCard :statuses="statuses" :current="current" :labels="cardLabels" @jump="jump">
          <view class="card-foot">
            <text class="muted">已提交 {{ submittedCount }} / {{ questions.length }}</text>
            <button class="btn-primary card-finish" size="mini" @tap="finish">{{ finishLabel === '结束' ? '结束练习' : finishLabel }}</button>
          </view>
        </AnswerCard>
      </uni-popup>

      <!-- 笔记 -->
      <uni-popup ref="notePopup" type="center">
        <view class="dialog">
          <view class="dialog-title">笔记</view>
          <textarea v-model="noteDraft" class="dialog-input" maxlength="200" placeholder="写点什么，最多 200 字（保存笔记会同时收藏这道题）" />
          <view class="dialog-count muted">{{ noteDraft.length }} / 200</view>
          <view class="dialog-btns">
            <button class="btn-plain" size="mini" @tap="closePopup(notePopup)">取消</button>
            <button class="btn-primary" size="mini" :loading="savingNote" @tap="saveNote">保存</button>
          </view>
        </view>
      </uni-popup>

      <!-- 纠错 -->
      <uni-popup ref="feedbackPopup" type="center">
        <view class="dialog">
          <view class="dialog-title">题目纠错</view>
          <view class="fb-types">
            <view
              v-for="(label, key) in FEEDBACK_LABEL"
              :key="key"
              class="fb-type"
              :class="{ on: feedbackType === key }"
              @tap="feedbackType = key"
            >
              {{ label }}
            </view>
          </view>
          <textarea v-model="feedbackContent" class="dialog-input" maxlength="500" placeholder="说说哪里有问题（1–500 字）" />
          <view class="dialog-count muted">{{ feedbackContent.length }} / 500</view>
          <view class="dialog-btns">
            <button class="btn-plain" size="mini" @tap="closePopup(feedbackPopup)">取消</button>
            <button class="btn-primary" size="mini" :loading="sendingFeedback" @tap="sendFeedback">提交</button>
          </view>
        </view>
      </uni-popup>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { onHide, onLoad, onUnload } from '@dcloudio/uni-app'
import { favoriteApi, practiceApi, questionApi } from '@/api'
import type { FeedbackType, PracticeQuestion, PracticeSession } from '@/api/types'
import QuestionBody from '@/components/QuestionBody.vue'
import AnswerReveal from '@/components/AnswerReveal.vue'
import AnswerCard from '@/components/AnswerCard.vue'
import { useTheme } from '@/composables/useTheme'
import { usePracticeStore } from '@/stores/practice'
import { cardStatus, emptyAnswer, hasAnyAnswer, isComplete, toSubmitAnswer, type CardStatus } from '@/logic/answer'
import { FEEDBACK_LABEL, MODE_LABEL } from '@/utils/labels'
import { PAGES } from '@/utils/nav'

/** 「已结束」提示条的高度，和样式里的 .ended-bar 保持一致 */
const ENDED_BAR_HEIGHT = 32

interface PopupRef {
  open: () => void
  close: () => void
}

const { themeClass } = useTheme()
const practiceStore = usePracticeStore()

const sessionId = ref(0)
const session = ref<PracticeSession | null>(null)
const state = ref<'loading' | 'ready' | 'error'>('loading')
const current = ref(0)
const drafts = reactive<Record<number, string[]>>({})
const visited = reactive(new Set<number>())
const submittingId = ref<number | null>(null)
const swiperHeight = ref(500)

const cardPopup = ref<PopupRef | null>(null)
const notePopup = ref<PopupRef | null>(null)
const feedbackPopup = ref<PopupRef | null>(null)
const noteDraft = ref('')
const savingNote = ref(false)
const feedbackType = ref<FeedbackType>('ANSWER_WRONG')
const feedbackContent = ref('')
const sendingFeedback = ref(false)

const questions = computed<PracticeQuestion[]>(() => session.value?.questions ?? [])
const currentQ = computed(() => questions.value[current.value])
const submittedCount = computed(() => questions.value.filter((q) => q.submitted).length)
/** 练习已结束（主动结束或全部提交后自动完成）：只读回看 */
const ended = computed(() => !!session.value && session.value.status !== 'IN_PROGRESS')
const finishLabel = computed(() => {
  if (ended.value) return session.value?.reciteMode ? '退出' : '返回结果'
  return finished.value ? '看结果' : '结束'
})
const finished = computed(
  () => !!session.value && !session.value.reciteMode && questions.value.length > 0 && submittedCount.value === questions.value.length,
)
const doneRate = computed(() => {
  const total = questions.value.length || 1
  const done = session.value?.reciteMode ? visited.size : submittedCount.value
  return Math.round((done / total) * 100)
})

/** 背题模式没有对错，答题卡改成「已看 / 未看」 */
const cardLabels = computed<Partial<Record<CardStatus, string>> | undefined>(() =>
  session.value?.reciteMode ? { answered: '已看', unanswered: '未看' } : undefined,
)
const statuses = computed<CardStatus[]>(() =>
  questions.value.map((q, i) => {
    if (session.value?.reciteMode) return visited.has(i) ? 'answered' : 'unanswered'
    return cardStatus({ submitted: q.submitted, correct: q.correct })
  }),
)

// ---------- 加载 ----------

onLoad((query) => {
  sessionId.value = Number(query?.id)
  const info = uni.getSystemInfoSync()
  // 顶栏 44 + 底栏 56 + 底部安全区
  swiperHeight.value = info.windowHeight - 44 - 56 - (info.safeAreaInsets?.bottom ?? 0)
  load(query?.index !== undefined ? Number(query.index) : null)
})

async function load(jumpTo: number | null = null) {
  state.value = 'loading'
  try {
    const data = await practiceApi.get(sessionId.value)
    session.value = data
    uni.setNavigationBarTitle({ title: data.reciteMode ? '背题模式' : MODE_LABEL[data.mode] || '练习' })
    const max = Math.max(data.questions.length - 1, 0)
    const start = jumpTo ?? data.currentIndex ?? 0
    current.value = Math.min(Math.max(start, 0), max)
    lastSavedIndex = data.currentIndex
    visited.add(current.value)
    enteredAt = Date.now()
    state.value = 'ready'
  } catch {
    state.value = 'error'
  }
}

// ---------- 作答 ----------

function isLocked(q: PracticeQuestion): boolean {
  return q.submitted || !!session.value?.reciteMode || ended.value
}

function shownAnswer(q: PracticeQuestion): string[] {
  if (q.submitted) return q.userAnswer ?? []
  return drafts[q.id] ?? emptyAnswer(q.type, q.blankCount)
}

function canSubmit(q: PracticeQuestion): boolean {
  return hasAnyAnswer(q.type, drafts[q.id])
}

/** 每题用时：停留在这题上的累计秒数 */
const spentMs: Record<number, number> = {}
let enteredAt = Date.now()

function leaveQuestion(index: number) {
  const q = questions.value[index]
  if (q) spentMs[q.id] = (spentMs[q.id] ?? 0) + (Date.now() - enteredAt)
  enteredAt = Date.now()
}

function confirmModal(content: string, confirmText = '确定'): Promise<boolean> {
  return new Promise((resolve) => {
    uni.showModal({ title: '提示', content, confirmText, success: (r) => resolve(r.confirm), fail: () => resolve(false) })
  })
}

async function submit(q: PracticeQuestion) {
  const draft = drafts[q.id] ?? []
  if (!hasAnyAnswer(q.type, draft)) return
  if (q.type === 'FILL' && !isComplete(q.type, draft, q.blankCount)) {
    if (!(await confirmModal('还有空没填，确定提交吗？提交后不能再改。'))) return
  }
  const timeSpentSec = Math.max(1, Math.round(((spentMs[q.id] ?? 0) + (Date.now() - enteredAt)) / 1000))
  submittingId.value = q.id
  try {
    const res = await practiceApi.submit(sessionId.value, {
      questionId: q.id,
      userAnswer: toSubmitAnswer(q.type, draft, q.blankCount),
      timeSpentSec,
    })
    // 以服务器返回为准（重复提交时是第一次的结果）
    q.submitted = true
    q.userAnswer = res.userAnswer
    q.correct = res.correct
    q.correctAnswer = res.correctAnswer
    q.acceptedAnswers = res.acceptedAnswers
    q.analysisHtml = res.analysisHtml
    if (res.alreadySubmitted) uni.showToast({ title: '这题之前已提交，显示第一次的结果', icon: 'none' })
    if (finished.value) uni.showToast({ title: '全部题目已提交', icon: 'none' })
  } catch {
    // 网络失败：草稿还在，可以再点提交（后端每题只记第一次，重试不会重复记录）
  } finally {
    submittingId.value = null
  }
}

// ---------- 切题 ----------

function onSwipe(e: { detail: { current: number } }) {
  setCurrent(e.detail.current)
}

function setCurrent(index: number) {
  if (index === current.value) return
  leaveQuestion(current.value)
  current.value = index
  visited.add(index)
}

function go(index: number) {
  if (index < 0 || index >= questions.value.length) return
  setCurrent(index)
}

function next() {
  if (current.value < questions.value.length - 1) {
    go(current.value + 1)
    return
  }
  uni.showToast({ title: '已经是最后一题', icon: 'none' })
  openCard()
}

function jump(index: number) {
  closePopup(cardPopup.value)
  go(index)
}

// ---------- 进度保存（退出后从这题接着做） ----------

let lastSavedIndex = -1
let progressTimer: ReturnType<typeof setTimeout> | null = null

function saveProgress() {
  if (progressTimer) clearTimeout(progressTimer)
  progressTimer = null
  if (!session.value || session.value.status !== 'IN_PROGRESS') return
  if (current.value === lastSavedIndex) return
  lastSavedIndex = current.value
  practiceApi.progress(sessionId.value, current.value).catch(() => {
    lastSavedIndex = -1 // 下次再试
  })
}

watch(current, () => {
  if (progressTimer) clearTimeout(progressTimer)
  progressTimer = setTimeout(saveProgress, 800)
})

onHide(saveProgress)
onUnload(saveProgress)

// ---------- 结束 ----------

async function finish() {
  if (!session.value) return
  if (ended.value) {
    // 回看已结束的练习：背题直接退出，普通练习回结果页
    if (session.value.reciteMode) uni.navigateBack({ fail: () => uni.switchTab({ url: PAGES.home }) })
    else uni.redirectTo({ url: `${PAGES.practiceResult}?id=${sessionId.value}` })
    return
  }
  if (session.value.reciteMode) {
    if (!(await confirmModal('结束背题？'))) return
    saveProgress()
    await practiceApi.finish(sessionId.value).catch(() => null)
    uni.navigateBack({ fail: () => uni.switchTab({ url: PAGES.home }) })
    return
  }
  const left = questions.value.length - submittedCount.value
  if (left > 0 && session.value.status === 'IN_PROGRESS') {
    if (!(await confirmModal(`还有 ${left} 题没提交，结束后这些题不算作答。确定结束吗？`, '结束'))) return
  }
  saveProgress()
  uni.showLoading({ title: '结算中' })
  try {
    const result = await practiceApi.finish(sessionId.value)
    practiceStore.setResult(sessionId.value, result)
    uni.hideLoading()
    uni.redirectTo({ url: `${PAGES.practiceResult}?id=${sessionId.value}` })
  } catch {
    uni.hideLoading()
  }
}

// ---------- 弹窗：答题卡、笔记、收藏、纠错 ----------

function openCard() {
  cardPopup.value?.open()
}

function closePopup(p: PopupRef | null) {
  p?.close()
}

async function toggleFavorite() {
  const q = currentQ.value
  if (!q) return
  try {
    if (q.favorited) {
      if (q.note && !(await confirmModal('取消收藏会同时删除这题的笔记，确定吗？'))) return
      await favoriteApi.remove(q.id)
      q.favorited = false
      q.note = null
      uni.showToast({ title: '已取消收藏', icon: 'none' })
    } else {
      await favoriteApi.save(q.id)
      q.favorited = true
      uni.showToast({ title: '已收藏', icon: 'none' })
    }
  } catch {
    /* 请求层已提示 */
  }
}

function openNote() {
  noteDraft.value = currentQ.value?.note ?? ''
  notePopup.value?.open()
}

async function saveNote() {
  const q = currentQ.value
  if (!q) return
  const note = noteDraft.value.trim()
  if (note.length > 200) {
    uni.showToast({ title: '笔记最多 200 字', icon: 'none' })
    return
  }
  savingNote.value = true
  try {
    await favoriteApi.save(q.id, note)
    q.favorited = true
    q.note = note || null
    closePopup(notePopup.value)
    uni.showToast({ title: '笔记已保存', icon: 'none' })
  } catch {
    /* 请求层已提示 */
  } finally {
    savingNote.value = false
  }
}

function openFeedback() {
  feedbackType.value = 'ANSWER_WRONG'
  feedbackContent.value = ''
  feedbackPopup.value?.open()
}

async function sendFeedback() {
  const q = currentQ.value
  if (!q) return
  const content = feedbackContent.value.trim()
  if (!content) {
    uni.showToast({ title: '请写一下问题在哪', icon: 'none' })
    return
  }
  sendingFeedback.value = true
  try {
    await questionApi.feedback(q.id, { type: feedbackType.value, content })
    closePopup(feedbackPopup.value)
    uni.showToast({ title: '已提交，感谢反馈', icon: 'none' })
  } catch {
    /* 请求层已提示 */
  } finally {
    sendingFeedback.value = false
  }
}
</script>

<style lang="scss" scoped>
.answer {
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

.progress-text {
  font-weight: 600;
  font-size: var(--fs-sm);
  min-width: 52px;
}

.progress-bar {
  flex: 1;
  height: 6px;
  border-radius: 3px;
  background: var(--c-bg);
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: var(--c-primary);
  border-radius: 3px;
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

.scroll {
  height: 100%;
}

.qwrap {
  padding: 14px 16px 0;
}

.submit {
  margin-top: 12px;
}

.note {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--c-warn-weak);
  display: flex;
  flex-direction: column;
}

.note-label {
  font-size: var(--fs-xs);
  color: var(--c-warn);
}

.note-text {
  white-space: pre-wrap;
  font-size: var(--fs-sm);
}

.ended-bar {
  height: 32px;
  line-height: 32px;
  box-sizing: border-box;
  padding: 0 12px;
  background: var(--c-warn-weak);
  color: var(--c-warn);
  font-size: var(--fs-sm);
  text-align: center;
}

.unanswered {
  margin-top: 16px;
  padding: 14px;
  border-radius: 10px;
  border: 1px dashed var(--c-border);
  background: var(--c-card);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.unanswered-title {
  font-weight: 600;
  color: var(--c-text-2);
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
}

.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
  padding-bottom: env(safe-area-inset-bottom);
}

.card-finish {
  margin: 0;
}

.dialog {
  width: 300px;
  padding: 18px;
  border-radius: 14px;
  background: var(--c-card);
  color: var(--c-text);
}

.dialog-title {
  font-weight: 600;
  font-size: var(--fs-lg);
  margin-bottom: 10px;
}

.dialog-input {
  width: 100%;
  height: 110px;
  box-sizing: border-box;
  padding: 10px;
  border-radius: 8px;
  border: 1px solid var(--c-border);
  background: var(--c-bg);
  color: var(--c-text);
  font-size: var(--fs-sm);
}

.dialog-count {
  text-align: right;
  margin-top: 2px;
}

.dialog-btns {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 10px;

  button {
    margin: 0;
  }
}

.fb-types {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.fb-type {
  padding: 4px 10px;
  border-radius: 14px;
  border: 1px solid var(--c-border);
  font-size: var(--fs-sm);
  color: var(--c-text-2);

  &.on {
    border-color: var(--c-primary);
    background: var(--c-primary-weak);
    color: var(--c-primary);
  }
}

.retry {
  margin-top: 12px;
}
</style>
