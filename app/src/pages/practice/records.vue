<template>
  <view class="page-root" :class="themeClass">
    <RecordScopeBar :scope="scope" :has-target="hasTarget" :target-name="user.targetCategoryName" @change="setScope" />

    <view v-if="state === 'error' && !list.length" class="empty">
      <view>练习记录没加载出来</view>
      <button class="btn-plain retry" size="mini" @tap="reload">重试</button>
    </view>
    <view v-else-if="state === 'loading' && !list.length" class="empty">加载中…</view>
    <view v-else-if="state === 'ready' && !list.length" class="empty">
      {{ scope === 'target' ? '这个考试还没有练习记录' : '还没有练习记录' }}
    </view>

    <view v-for="r in list" :key="r.id" class="card rec" @tap="open(r)">
      <view class="rec-top">
        <text class="rec-title">{{ MODE_LABEL[r.mode] || '练习' }}</text>
        <text v-if="!r.completedAt" class="tag">未完成</text>
        <text v-else class="muted">已完成</text>
      </view>
      <text v-if="scope === 'all'" class="src">来源：{{ sourceName(r.rootCategoryName) }}</text>
      <text class="muted">
        已答 {{ r.answeredCount }} / {{ r.questionCount }} · 答对 {{ r.correctCount }} · {{ formatDateTime(r.startedAt) }}
      </text>
    </view>

    <view v-if="list.length" class="muted more">{{ state === 'loading' ? '加载中…' : hasMore ? '上拉加载更多' : '没有更多了' }}</view>
  </view>
</template>

<script setup lang="ts">
import { practiceApi } from '@/api'
import type { PracticeSummary } from '@/api/types'
import RecordScopeBar from '@/components/RecordScopeBar.vue'
import { useRecordList } from '@/composables/useRecordList'
import { useTheme } from '@/composables/useTheme'
import { formatDateTime } from '@/logic/format'
import { sourceName } from '@/logic/records'
import { useUserStore } from '@/stores/user'
import { MODE_LABEL } from '@/utils/labels'
import { PAGES } from '@/utils/nav'

const { themeClass } = useTheme()
const user = useUserStore()
// 默认只看目标考试（不传参数），可切到全部考试（all=true）
const { scope, hasTarget, list, state, hasMore, setScope, reload } = useRecordList<PracticeSummary>((q) => practiceApi.list(q))

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

.src {
  font-size: var(--fs-xs);
  color: var(--c-primary);
}

.more {
  text-align: center;
  padding: 8px 0 16px;
}

.retry {
  margin-top: 12px;
}
</style>
