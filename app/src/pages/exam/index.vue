<template>
  <view class="page-root exams" :class="themeClass">
    <view class="head">
      <text class="head-title">{{ user.targetCategoryName }} · 模拟试卷</text>
      <text class="head-link" @tap="go(PAGES.examRecords)">考试记录</text>
    </view>

    <ModuleError v-if="!papers && status === 'error'" :title="`${user.targetCategoryName}的试卷没加载出来`" :error="error" @retry="refresh" />
    <view v-else-if="!papers" class="empty">加载中…</view>
    <view v-else-if="!papers.length" class="empty">这个考试下还没有上架的试卷</view>

    <view v-for="p in papers ?? []" :key="p.paperId" class="card paper">
      <view class="paper-top">
        <text class="paper-title">{{ p.title }}</text>
        <text class="tag">{{ p.mode === 'RULE' ? '随机组卷' : '固定卷' }}</text>
      </view>
      <text class="muted">{{ p.questionCount }} 题 · 满分 {{ p.totalScore }} · 及格 {{ p.passScore }} · {{ p.durationMin }} 分钟</text>
      <view class="paper-foot">
        <text class="muted">
          {{ p.myAttempts ? `已考 ${p.myAttempts} 次 · 最高 ${p.myBestScore ?? '—'} 分` : '还没考过' }}
        </text>
        <button v-if="p.inProgressRecordId" class="btn-primary" size="mini" @tap="resume(p.inProgressRecordId)">继续考试</button>
        <button v-else class="btn-plain" size="mini" @tap="openNotice(p.paperId)">考前须知</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { examApi } from '@/api'
import ModuleError from '@/components/ModuleError.vue'
import { useTheme } from '@/composables/useTheme'
import { useTargetData } from '@/composables/useTargetData'
import { useUserStore } from '@/stores/user'
import { PAGES, ensureReady } from '@/utils/nav'

const { themeClass } = useTheme()
const user = useUserStore()

// 试卷列表按目标考试分开存：切换考试立即清空，失败只显示当前考试的失败
const { data: papers, status, error, refresh } = useTargetData((id) => examApi.list(id))

onShow(() => {
  if (ensureReady()) refresh()
})

onPullDownRefresh(async () => {
  await refresh()
  uni.stopPullDownRefresh()
})

function go(url: string) {
  uni.navigateTo({ url })
}

function openNotice(paperId: number) {
  uni.navigateTo({ url: `${PAGES.examNotice}?paperId=${paperId}` })
}

function resume(recordId: number) {
  uni.navigateTo({ url: `${PAGES.examAnswer}?recordId=${recordId}` })
}
</script>

<style lang="scss" scoped>
.exams {
  padding-bottom: 12px;
}

.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px 0;
}

.head-title {
  font-weight: 600;
  font-size: var(--fs-lg);
}

.head-link {
  color: var(--c-primary);
  font-size: var(--fs-sm);
}

.paper {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.paper-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.paper-title {
  font-weight: 600;
  font-size: var(--fs-lg);
}

.paper-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;

  button {
    margin: 0;
  }
}

.retry {
  margin-top: 12px;
}
</style>
