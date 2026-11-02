<template>
  <view class="page-root" :class="themeClass">
    <RecordScopeBar :scope="scope" :has-target="hasTarget" :target-name="user.targetCategoryName" @change="setScope" />

    <view v-if="state === 'error' && !list.length" class="empty">
      <view>考试记录没加载出来</view>
      <button class="btn-plain retry" size="mini" @tap="reload">重试</button>
    </view>
    <view v-else-if="state === 'loading' && !list.length" class="empty">加载中…</view>
    <view v-else-if="state === 'ready' && !list.length" class="empty">
      {{ scope === 'target' ? '这个考试还没有考试记录' : '还没有考试记录' }}
    </view>

    <view v-for="r in list" :key="r.recordId" class="card rec" @tap="open(r)">
      <view class="rec-top">
        <text class="rec-title">{{ r.title }}</text>
        <text v-if="r.status === 'IN_PROGRESS'" class="tag">进行中</text>
        <text v-else class="rec-score" :class="r.passed ? 'ok' : 'bad'">{{ r.score ?? '—' }}<text class="muted"> / {{ r.totalScore }}</text></text>
      </view>
      <text v-if="scope === 'all'" class="src">来源：{{ sourceName(r.rootCategoryName) }}</text>
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
import { examApi } from '@/api'
import type { ExamRecordSummary } from '@/api/types'
import RecordScopeBar from '@/components/RecordScopeBar.vue'
import { useRecordList } from '@/composables/useRecordList'
import { useTheme } from '@/composables/useTheme'
import { formatDateTime } from '@/logic/format'
import { sourceName } from '@/logic/records'
import { useUserStore } from '@/stores/user'
import { PAGES } from '@/utils/nav'

const { themeClass } = useTheme()
const user = useUserStore()
// 默认只看目标考试（不传参数），可切到全部考试（all=true）
const { scope, hasTarget, list, state, hasMore, setScope, reload } = useRecordList<ExamRecordSummary>((q) => examApi.records(q))

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

.src {
  font-size: var(--fs-xs);
  color: var(--c-primary);
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
