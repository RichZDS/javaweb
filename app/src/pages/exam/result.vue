<template>
  <view class="page-root report" :class="themeClass">
    <view v-if="state === 'loading'" class="empty">成绩加载中…</view>
    <view v-else-if="state === 'error'" class="empty">
      <view>成绩没加载出来</view>
      <button class="btn-plain retry" size="mini" @tap="load">重试</button>
    </view>

    <template v-else-if="result">
      <view class="summary" :class="result.passed ? 'pass' : 'fail'">
        <text class="paper">{{ result.title }}</text>
        <view class="score-line">
          <text class="score">{{ result.score }}</text>
          <text class="total">/ {{ result.totalScore }} 分</text>
        </view>
        <text class="verdict">{{ result.passed ? '✓ 及格' : '✗ 未及格' }}（及格线 {{ result.passScore }} 分）</text>
        <view class="counts">
          <view class="count"><text class="n">{{ result.correctCount }}</text><text class="c">答对</text></view>
          <view class="count"><text class="n">{{ result.wrongCount }}</text><text class="c">答错</text></view>
          <view class="count"><text class="n">{{ result.unansweredCount }}</text><text class="c">未答</text></view>
          <view class="count"><text class="n">{{ formatDuration(result.durationSec) }}</text><text class="c">用时</text></view>
        </view>
        <text class="time">交卷时间 {{ formatDateTime(result.submittedAt) || '—' }}</text>
      </view>

      <view v-if="result.byType.length" class="card">
        <view class="card-title">分题型得分</view>
        <view v-for="t in result.byType" :key="t.type" class="row">
          <text class="row-name">{{ TYPE_LABEL[t.type] }}</text>
          <view class="bar"><view class="bar-fill" :style="{ width: pct(t.rate) }" /></view>
          <text class="row-val">{{ t.score }}/{{ t.total }} · {{ formatRate(t.rate) }}</text>
        </view>
      </view>

      <view v-if="result.byCategory.length" class="card">
        <view class="card-title">分章节得分</view>
        <view v-for="c in result.byCategory" :key="c.categoryId" class="row">
          <text class="row-name path">{{ lastPart(c.categoryPath) }}</text>
          <view class="bar"><view class="bar-fill" :style="{ width: pct(c.rate) }" /></view>
          <text class="row-val">{{ c.score }}/{{ c.total }} · {{ formatRate(c.rate) }}</text>
        </view>
      </view>

      <view class="card">
        <view class="card-title">答题详情</view>
        <view class="filters">
          <text v-for="f in FILTERS" :key="f.key" class="filter" :class="{ on: filter === f.key }" @tap="filter = f.key">
            {{ f.label }}
          </text>
        </view>
        <view v-if="!shownItems.length" class="muted">没有符合条件的题</view>
        <view v-for="item in shownItems" :key="item.questionId" class="item">
          <view class="item-head" @tap="toggle(item.questionId)">
            <text class="item-no">第 {{ item.index }} 题</text>
            <text class="item-tag" :class="itemClass(item)">{{ itemLabel(item) }}</text>
            <text class="muted item-score">{{ item.score }}/{{ item.maxScore }} 分</text>
            <uni-icons :type="open[item.questionId] ? 'up' : 'down'" size="14" color="#98a0ad" />
          </view>
          <view v-if="open[item.questionId]" class="item-body">
            <QuestionBody
              :question="{ ...item, blankCount: item.blankCount ?? item.acceptedAnswers?.length ?? 0 }"
              :model-value="item.userAnswer ?? []"
              locked
              :correct-answer="item.type !== 'FILL' ? item.correctAnswer : null"
            />
            <AnswerReveal
              :type="item.type"
              :correct="item.correct"
              :user-answer="item.userAnswer"
              :correct-answer="item.correctAnswer"
              :accepted-answers="item.acceptedAnswers"
              :analysis-html="item.analysisHtml"
            />
          </view>
        </view>
      </view>

      <view class="btns">
        <button class="btn-primary" :disabled="!result.wrongCount || retrying" :loading="retrying" @tap="retryWrong">
          重练本场错题
        </button>
        <button class="btn-plain" @tap="goExams">返回考试列表</button>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { examApi, isExamSheet, practiceApi } from '@/api'
import type { ExamResult, ExamResultItem } from '@/api/types'
import QuestionBody from '@/components/QuestionBody.vue'
import AnswerReveal from '@/components/AnswerReveal.vue'
import { useTheme } from '@/composables/useTheme'
import { TYPE_LABEL, hasAnyAnswer } from '@/logic/answer'
import { formatDateTime, formatDuration, formatRate } from '@/logic/format'
import { ApiError } from '@/logic/http'
import { PAGES } from '@/utils/nav'

type Filter = 'ALL' | 'WRONG' | 'UNANSWERED'
const FILTERS: { key: Filter; label: string }[] = [
  { key: 'ALL', label: '全部' },
  { key: 'WRONG', label: '答错' },
  { key: 'UNANSWERED', label: '未答' },
]

const { themeClass } = useTheme()

const recordId = ref(0)
const result = ref<ExamResult | null>(null)
const state = ref<'loading' | 'ready' | 'error'>('loading')
const filter = ref<Filter>('WRONG')
const open = reactive<Record<number, boolean>>({})
const retrying = ref(false)

const answered = (item: ExamResultItem) => hasAnyAnswer(item.type, item.userAnswer)

const shownItems = computed(() => {
  const items = result.value?.items ?? []
  if (filter.value === 'WRONG') return items.filter((i) => !i.correct && answered(i))
  if (filter.value === 'UNANSWERED') return items.filter((i) => !answered(i))
  return items
})

onLoad((query) => {
  recordId.value = Number(query?.recordId)
  load()
})

async function load() {
  state.value = 'loading'
  try {
    const data = await examApi.record(recordId.value)
    if (isExamSheet(data)) {
      // 还在考：回答题页
      uni.redirectTo({ url: `${PAGES.examAnswer}?recordId=${recordId.value}` })
      return
    }
    result.value = data
    // 没有答错的题时默认看全部
    if (!data.items.some((i) => !i.correct && answered(i))) filter.value = 'ALL'
    state.value = 'ready'
  } catch {
    state.value = 'error'
  }
}

function pct(rate: number | null): string {
  return `${Math.round((rate ?? 0) * 100)}%`
}

function lastPart(path: string): string {
  const parts = path.split('/').map((s) => s.trim())
  return parts[parts.length - 1] || path
}

function itemLabel(item: ExamResultItem): string {
  if (!answered(item)) return '未答'
  return item.correct ? '对' : '错'
}

function itemClass(item: ExamResultItem): string {
  if (!answered(item)) return 'none'
  return item.correct ? 'ok' : 'bad'
}

function toggle(id: number) {
  open[id] = !open[id]
}

/** 重做本场错题：RETRY + fromExamRecordId，后端挑出提交了但没全对的题（未答不算） */
async function retryWrong() {
  retrying.value = true
  try {
    const session = await practiceApi.start({ mode: 'RETRY', fromExamRecordId: recordId.value }, ['POOL_INSUFFICIENT'])
    uni.navigateTo({ url: `${PAGES.practiceAnswer}?id=${session.id}` })
  } catch (e) {
    if (e instanceof ApiError && e.code === 'POOL_INSUFFICIENT') uni.showToast({ title: '这场没有需要重练的错题', icon: 'none' })
  } finally {
    retrying.value = false
  }
}

function goExams() {
  uni.switchTab({ url: PAGES.exam })
}
</script>

<style lang="scss" scoped>
.report {
  padding-bottom: 24px;
}

.summary {
  margin: 12px;
  padding: 18px 16px;
  border-radius: 14px;
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;

  &.pass {
    background: linear-gradient(135deg, #18a058, #36c47a);
  }

  &.fail {
    background: linear-gradient(135deg, #d03050, #e8677e);
  }
}

.paper {
  font-size: var(--fs-sm);
  opacity: 0.9;
}

.score-line {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin: 4px 0;
}

.score {
  font-size: 48px;
  font-weight: 700;
  line-height: 1.1;
}

.total {
  font-size: var(--fs-base);
  opacity: 0.9;
}

.verdict {
  font-size: var(--fs-sm);
}

.counts {
  display: flex;
  width: 100%;
  margin-top: 14px;
}

.count {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;

  .n {
    font-weight: 700;
    font-size: var(--fs-lg);
  }

  .c {
    font-size: var(--fs-xs);
    opacity: 0.9;
  }
}

.time {
  margin-top: 10px;
  font-size: var(--fs-xs);
  opacity: 0.85;
}

.row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.row-name {
  width: 72px;
  flex: none;
  font-size: var(--fs-sm);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.bar {
  flex: 1;
  height: 8px;
  border-radius: 4px;
  background: var(--c-bg);
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  background: var(--c-primary);
  border-radius: 4px;
}

.row-val {
  flex: none;
  font-size: var(--fs-xs);
  color: var(--c-text-2);
  min-width: 92px;
  text-align: right;
}

.filters {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.filter {
  padding: 2px 12px;
  border-radius: 14px;
  border: 1px solid var(--c-border);
  font-size: var(--fs-sm);
  color: var(--c-text-2);

  &.on {
    border-color: var(--c-primary);
    color: var(--c-primary);
    background: var(--c-primary-weak);
  }
}

.item {
  border-top: 1px solid var(--c-border);
}

.item-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 0;
}

.item-no {
  font-weight: 600;
  font-size: var(--fs-sm);
}

.item-tag {
  font-size: var(--fs-xs);
  padding: 0 8px;
  border-radius: 10px;
  line-height: 20px;

  &.ok {
    background: var(--c-correct-weak);
    color: var(--c-correct);
  }

  &.bad {
    background: var(--c-wrong-weak);
    color: var(--c-wrong);
  }

  &.none {
    background: var(--c-bg);
    color: var(--c-text-3);
  }
}

.item-score {
  flex: 1;
  text-align: right;
}

.item-body {
  padding-bottom: 12px;
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
