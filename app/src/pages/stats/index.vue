<template>
  <view class="page-root" :class="themeClass">
    <view v-if="overview" class="card">
      <view class="card-title">{{ user.targetCategoryName }} · 学习总览</view>
      <view class="grid">
        <view v-for="cell in cells" :key="cell.label" class="cell">
          <text class="num">{{ cell.value }}</text>
          <text class="muted">{{ cell.label }}</text>
        </view>
      </view>
    </view>
    <view v-else-if="failed" class="empty">
      <view>统计没加载出来</view>
      <button class="btn-plain retry" size="mini" @tap="load">重试</button>
    </view>
    <view v-else class="empty">加载中…</view>
    <!-- M1 只有总览数字；趋势折线、章节柱状图、薄弱点、考试分数、排行榜在 M2 实现 -->
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { statsApi } from '@/api'
import type { StatsOverview } from '@/api/types'
import { useTheme } from '@/composables/useTheme'
import { useUserStore } from '@/stores/user'
import { formatRate } from '@/logic/format'
import { ensureReady } from '@/utils/nav'

const { themeClass } = useTheme()
const user = useUserStore()
const overview = ref<StatsOverview | null>(null)
const failed = ref(false)

const cells = computed(() => {
  const o = overview.value
  if (!o) return []
  return [
    { label: '累计作答', value: o.answerCount },
    { label: '答对', value: o.correctCount },
    { label: '正确率', value: formatRate(o.correctRate) },
    { label: '今日作答', value: o.todayAnswerCount },
    { label: '学习天数', value: o.studyDays },
    { label: '连续打卡', value: o.streakDays },
  ]
})

onShow(() => {
  if (ensureReady()) load()
})

onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})

async function load() {
  failed.value = false
  try {
    overview.value = await statsApi.overview()
  } catch {
    failed.value = !overview.value
  }
}
</script>

<style lang="scss" scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px 0;
}

.cell {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.num {
  font-size: var(--fs-xl);
  font-weight: 700;
}

.retry {
  margin-top: 12px;
}
</style>
