<template>
  <view class="page-root" :class="themeClass">
    <view v-if="state === 'error' && !list.length" class="empty">
      <view>练习记录没加载出来</view>
      <button class="btn-plain retry" size="mini" @tap="fetchPage(1)">重试</button>
    </view>
    <view v-else-if="state === 'ready' && !list.length" class="empty">还没有练习记录</view>

    <view v-for="r in list" :key="r.id" class="card rec" @tap="open(r)">
      <view class="rec-top">
        <text class="rec-title">{{ MODE_LABEL[r.mode] || '练习' }}</text>
        <text v-if="!r.completedAt" class="tag">未完成</text>
        <text v-else class="muted">已完成</text>
      </view>
      <text class="muted">
        已答 {{ r.answeredCount }} / {{ r.questionCount }} · 答对 {{ r.correctCount }} · {{ formatDateTime(r.startedAt) }}
      </text>
    </view>

    <view v-if="list.length" class="muted more">{{ state === 'loading' ? '加载中…' : hasMore ? '上拉加载更多' : '没有更多了' }}</view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { practiceApi } from '@/api'
import type { PracticeSummary } from '@/api/types'
import { useTheme } from '@/composables/useTheme'
import { formatDateTime } from '@/logic/format'
import { MODE_LABEL } from '@/utils/labels'
import { PAGES } from '@/utils/nav'

const { themeClass } = useTheme()

const list = ref<PracticeSummary[]>([])
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
    const res = await practiceApi.list(p, 20)
    list.value = p === 1 ? res.items : [...list.value, ...res.items]
    total.value = res.total
    page.value = p
    state.value = 'ready'
  } catch {
    state.value = 'error'
  }
}

/** 未完成的接着做；已完成的看结果 */
function open(r: PracticeSummary) {
  const url = r.completedAt ? PAGES.practiceResult : PAGES.practiceAnswer
  uni.navigateTo({ url: `${url}?id=${r.id}` })
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

.more {
  text-align: center;
  padding: 8px 0 16px;
}

.retry {
  margin-top: 12px;
}
</style>
