<template>
  <view class="page-root result" :class="themeClass">
    <view v-if="state === 'loading'" class="empty">加载中…</view>
    <view v-else-if="state === 'error'" class="empty">
      <view>结果没加载出来</view>
      <button class="btn-plain retry" size="mini" @tap="load">重试</button>
    </view>

    <template v-else-if="result">
      <view class="summary">
        <view class="rate-ring">
          <text class="rate-num">{{ formatRate(result.correctRate) }}</text>
          <text class="rate-cap">正确率</text>
        </view>
        <view class="sum-grid">
          <view class="sum-item">
            <text class="sum-num">{{ formatDuration(result.durationSec) }}</text>
            <text class="sum-cap">用时</text>
          </view>
          <view class="sum-item">
            <text class="sum-num">{{ result.answeredCount }}<text class="sum-small"> / {{ totalCount }}</text></text>
            <text class="sum-cap">已作答</text>
          </view>
          <view class="sum-item">
            <text class="sum-num ok">{{ result.correctCount }}</text>
            <text class="sum-cap">答对</text>
          </view>
          <view class="sum-item">
            <text class="sum-num bad">{{ result.wrongQuestionIds.length }}</text>
            <text class="sum-cap">答错</text>
          </view>
        </view>
        <view v-if="modeText" class="mode-text">{{ modeText }}</view>
        <!-- 每日一练：全部提交才算打卡 -->
        <view v-if="daily" class="daily" :class="daily.done ? 'done' : 'todo'">
          <text v-if="daily.done" class="daily-title">✓ 今日已打卡{{ daily.streak !== null ? `，连续 ${daily.streak} 天` : '' }}</text>
          <template v-else>
            <text class="daily-title">今日未打卡，还差 {{ daily.left }} 题</text>
            <text class="daily-sub">10 道题全部提交才算完成今天的每日一练</text>
          </template>
        </view>
      </view>

      <view class="card">
        <view class="card-title">本次错题</view>
        <view v-if="!wrongItems.length" class="muted">这次没有错题 🎉</view>
        <view v-for="item in wrongItems" :key="item.q.id" class="wrong" @tap="review(item.index)">
          <view class="wrong-top">
            <text class="wrong-no">第 {{ item.index + 1 }} 题</text>
            <text class="tag">{{ TYPE_LABEL[item.q.type] }}</text>
          </view>
          <text class="wrong-stem">{{ htmlToPreview(item.q.stemHtml, 60) }}</text>
          <text class="muted">
            你的答案 {{ formatAnswer(item.q.type, item.q.userAnswer) }} · 正确答案
            {{ formatCorrect(item.q.type, item.q.correctAnswer, item.q.acceptedAnswers) }}
          </text>
        </view>
      </view>

      <view class="btns">
        <button v-if="daily && !daily.done" class="btn-primary" @tap="continueDaily">继续今日练习</button>
        <button class="btn-primary" :disabled="!result.wrongQuestionIds.length || retrying" :loading="retrying" @tap="retryWrong">
          一键重做错题
        </button>
        <button class="btn-plain" @tap="review(0)">查看全部解析</button>
        <button class="btn-plain" @tap="goHome">返回首页</button>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { practiceApi, statsApi } from '@/api'
import type { FinishResult, PracticeSession } from '@/api/types'
import { useTheme } from '@/composables/useTheme'
import { usePracticeStore } from '@/stores/practice'
import { useUserStore } from '@/stores/user'
import { TYPE_LABEL, formatAnswer, formatCorrect } from '@/logic/answer'
import { formatDuration, formatRate, htmlToPreview } from '@/logic/format'
import { ApiError } from '@/logic/http'
import { MODE_LABEL } from '@/utils/labels'
import { PAGES } from '@/utils/nav'

const { themeClass } = useTheme()
const practiceStore = usePracticeStore()
const user = useUserStore()

const sessionId = ref(0)
const state = ref<'loading' | 'ready' | 'error'>('loading')
const result = ref<FinishResult | null>(null)
const session = ref<PracticeSession | null>(null)
const retrying = ref(false)

const totalCount = computed(() => session.value?.questionCount ?? result.value?.answeredCount ?? 0)
const modeText = computed(() => (session.value ? MODE_LABEL[session.value.mode] : ''))
const streak = ref<number | null>(null)

/** 每日一练的打卡状态：以后端是否写了 completedAt 为准，还差几题 = 题数 − 已提交数 */
const daily = computed(() => {
  const s = session.value
  if (!s || s.mode !== 'DAILY') return null
  const done = !!s.completedAt
  return { done, left: Math.max(0, s.questionCount - (result.value?.answeredCount ?? s.answeredCount)), streak: streak.value }
})

/** 错题按题号顺序列出，内容取自本次练习的题目 */
const wrongItems = computed(() => {
  const ids = new Set(result.value?.wrongQuestionIds ?? [])
  return (session.value?.questions ?? [])
    .map((q, index) => ({ q, index }))
    .filter((item) => ids.has(item.q.id))
})

onLoad((query) => {
  sessionId.value = Number(query?.id)
  load()
})

async function load() {
  state.value = 'loading'
  try {
    // 从答题页过来时已经有结束结果；直接打开（刷新）时再调一次 finish，后端对已完成的练习返回同样结果
    const cached = practiceStore.takeResult(sessionId.value)
    const [finishRes, sessionRes] = await Promise.all([
      cached ? Promise.resolve(cached) : practiceApi.finish(sessionId.value),
      practiceApi.get(sessionId.value),
    ])
    result.value = finishRes
    session.value = sessionRes
    state.value = 'ready'
    // 打卡完成时显示连续天数（统计接口失败就只显示「已打卡」）
    if (sessionRes.mode === 'DAILY' && sessionRes.completedAt) {
      statsApi
        .overview(user.targetCategoryId ?? undefined)
        .then((o) => (streak.value = o.streakDays))
        .catch(() => {})
    }
  } catch {
    state.value = 'error'
  }
}

function review(index: number) {
  uni.navigateTo({ url: `${PAGES.practiceAnswer}?id=${sessionId.value}&index=${index}` })
}

/** 重做本次错题：RETRY 模式，后端按这次练习挑出提交了但没全对的题 */
async function retryWrong() {
  retrying.value = true
  try {
    const next = await practiceApi.start({ mode: 'RETRY', fromSessionId: sessionId.value }, ['POOL_INSUFFICIENT'])
    uni.redirectTo({ url: `${PAGES.practiceAnswer}?id=${next.id}` })
  } catch (e) {
    if (e instanceof ApiError && e.code === 'POOL_INSUFFICIENT') {
      uni.showToast({ title: '这次没有需要重做的错题', icon: 'none' })
    }
  } finally {
    retrying.value = false
  }
}

/** 每日一练没做完：回到同一组接着做（后端保持进行中） */
function continueDaily() {
  uni.redirectTo({ url: `${PAGES.practiceAnswer}?id=${sessionId.value}` })
}

function goHome() {
  uni.switchTab({ url: PAGES.home })
}
</script>

<style lang="scss" scoped>
.result {
  padding-bottom: 24px;
}

.summary {
  margin: 12px;
  padding: 20px 16px;
  border-radius: 14px;
  background: var(--c-card);
  text-align: center;
}

.rate-ring {
  width: 120px;
  height: 120px;
  margin: 0 auto 16px;
  border-radius: 60px;
  border: 8px solid var(--c-primary);
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.rate-num {
  font-size: 26px;
  font-weight: 700;
  color: var(--c-primary);
  line-height: 1.2;
}

.rate-cap {
  font-size: var(--fs-xs);
  color: var(--c-text-3);
}

.sum-grid {
  display: flex;
}

.sum-item {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.sum-num {
  font-size: var(--fs-lg);
  font-weight: 700;

  &.ok {
    color: var(--c-correct);
  }

  &.bad {
    color: var(--c-wrong);
  }
}

.sum-small {
  font-size: var(--fs-sm);
  font-weight: 400;
  color: var(--c-text-3);
}

.sum-cap {
  font-size: var(--fs-xs);
  color: var(--c-text-3);
}

.daily {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  gap: 2px;

  &.done {
    background: var(--c-correct-weak);
    color: var(--c-correct);
  }

  &.todo {
    background: var(--c-warn-weak);
    color: var(--c-warn);
  }
}

.daily-title {
  font-weight: 600;
}

.daily-sub {
  font-size: var(--fs-xs);
}

.mode-text {
  margin-top: 12px;
  font-size: var(--fs-xs);
  color: var(--c-text-3);
}

.wrong {
  display: flex;
  flex-direction: column;
  padding: 10px 0;
  border-top: 1px solid var(--c-border);
}

.wrong-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.wrong-no {
  font-weight: 600;
  font-size: var(--fs-sm);
}

.wrong-stem {
  margin: 4px 0;
}

.btns {
  padding: 0 16px;

  button {
    margin-top: 12px;
  }
}

.retry {
  margin-top: 12px;
}
</style>
