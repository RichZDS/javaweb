<template>
  <view class="page-root board" :class="themeClass">
    <view class="head">
      <text class="head-title">{{ user.targetCategoryName }} · 本周作答排行</text>
      <text class="muted">周一 00:00（北京时间）起，按正式作答次数</text>
    </view>

    <ModuleError v-if="!board && status === 'error'" :title="`${user.targetCategoryName}的排行榜没加载出来`" :error="error" @retry="refresh" />
    <view v-else-if="!board" class="empty">加载中…</view>

    <template v-else>
      <view class="card me">
        <text>我的排名</text>
        <text class="me-rank">{{ board.me.rank ? `第 ${board.me.rank} 名` : '未上榜' }}</text>
        <text class="muted">本周 {{ board.me.answerCount }} 次</text>
      </view>

      <view class="card list">
        <view v-if="!board.items.length" class="muted">本周还没有人作答</view>
        <view v-for="it in board.items" :key="it.rank" class="row">
          <text class="rank" :class="`r${it.rank}`">{{ it.rank }}</text>
          <image v-if="it.avatarUrl" class="avatar" :src="asset(it.avatarUrl)" mode="aspectFill" />
          <view v-else class="avatar avatar-text">{{ (it.nickname || '?').slice(0, 1) }}</view>
          <text class="name">{{ it.nickname }}</text>
          <text class="count">{{ it.answerCount }} 次</text>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { statsApi } from '@/api'
import { API_BASE } from '@/api/http'
import ModuleError from '@/components/ModuleError.vue'
import { useTheme } from '@/composables/useTheme'
import { useTargetData } from '@/composables/useTargetData'
import { useUserStore } from '@/stores/user'
import { resolveAssetUrl } from '@/logic/http'

const { themeClass } = useTheme()
const user = useUserStore()
// 排行榜按目标考试
const { data: board, status, error, refresh } = useTargetData((id) => statsApi.leaderboard(id))

onShow(refresh)
onPullDownRefresh(async () => {
  await refresh()
  uni.stopPullDownRefresh()
})

function asset(url: string) {
  return resolveAssetUrl(API_BASE, url)
}
</script>

<style lang="scss" scoped>
.head {
  padding: 16px 16px 0;
  display: flex;
  flex-direction: column;
}

.head-title {
  font-size: var(--fs-xl);
  font-weight: 700;
}

.me {
  display: flex;
  align-items: center;
  gap: 10px;
}

.me-rank {
  flex: 1;
  font-weight: 700;
  color: var(--c-primary);
}

.row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid var(--c-border);

  &:last-child {
    border-bottom: none;
  }
}

.rank {
  width: 24px;
  text-align: center;
  font-weight: 700;
  color: var(--c-text-3);

  &.r1 {
    color: #e6a700;
  }

  &.r2 {
    color: #8a96a8;
  }

  &.r3 {
    color: #c07a3c;
  }
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 16px;
  flex: none;
}

.avatar-text {
  background: var(--c-primary-weak);
  color: var(--c-primary);
  text-align: center;
  line-height: 32px;
  font-weight: 600;
}

.name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.count {
  font-weight: 600;
}

.retry {
  margin-top: 12px;
}
</style>
