<template>
  <view class="page-root" :class="themeClass">
    <view v-if="state === 'error' && !list.length" class="empty">
      <view>考试记录没加载出来</view>
      <button class="btn-plain retry" size="mini" @tap="fetchPage(1)">重试</button>
    </view>
    <view v-else-if="state === 'ready' && !list.length" class="empty">还没有考试记录</view>

    <view v-for="r in list" :key="r.recordId" class="card rec" @tap="open(r)">
      <view class="rec-top">
        <text class="rec-title">{{ r.title }}</text>
        <text v-if="r.status === 'IN_PROGRESS'" class="tag">进行中</text>
        <text v-else class="rec-score" :class="r.passed ? 'ok' : 'bad'">{{ r.score ?? '—' }}<text class="muted"> / {{ r.totalScore }}</text></text>
      </view>
      <text class="muted">
        {{ formatDateTime(r.startedAt) }} 开始
        <text v-if="r.submittedAt"> · {{ formatDateTime(r.submittedAt) }} 交卷</text>
        <text v-if="r.status !== 'IN_PROGRESS'"> · {{ r.passed ? '及格' : '未及格' }}</text>
      </text>
    </view>

    <view v-if="list.length" class="muted more">{{ state === 'loading' ? '加载中…' : hasMore ? '上拉加载更多' : '没有更多了' }}</view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { examApi } from '@/api'
import type { ExamRecordSummary } from '@/api/types'
import { useTheme } from '@/composables/useTheme'
import { formatDateTime } from '@/logic/format'
import { PAGES } from '@/utils/nav'

const { themeClass } = useTheme()

const list = ref<ExamRecordSummary[]>([])
const page = ref(1)
const total = ref(0)
const state = ref<'loading' | 'ready' | 'error'>('loading')
const hasMore = computed(() => list.value.length < total.value)

onShow(() => fetchPage(1))
onPullDownRefresh(async () => {
  await fetchPage(1)
  uni.stopPullDownRefresh()
})
onReachBottom(() => {
  if (hasMore.value && state.value !== 'loading') fetchPage(page.value + 1)
})

async function fetchPage(p: number) {
  state.value = 'loading'
  try {
    const res = await examApi.records({ page: p, size: 20 })
    list.value = p === 1 ? res.items : [...list.value, ...res.items]
    total.value = res.total
    page.value = p
    state.value = 'ready'
  } catch {
    state.value = 'error'
  }
}

function open(r: ExamRecordSummary) {
  const url = r.status === 'IN_PROGRESS' ? PAGES.examAnswer : PAGES.examResult
  uni.navigateTo({ url: `${url}?recordId=${r.recordId}` })
}
</script>

<style lang="scss" scoped>
.rec {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.rec-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.rec-title {
  font-weight: 600;
}

.rec-score {
  font-weight: 700;
  font-size: var(--fs-lg);

  &.ok {
    color: var(--c-correct);
  }

  &.bad {
    color: var(--c-wrong);
  }
}

.more {
  text-align: center;
  padding: 8px 0 16px;
}

.retry {
  margin-top: 12px;
}
</style>
