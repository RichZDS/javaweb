<template>
  <view class="page-root notice" :class="themeClass">
    <view v-if="state === 'loading'" class="empty">加载中…</view>
    <view v-else-if="state === 'error'" class="empty">
      <view>考前须知没加载出来</view>
      <button class="btn-plain retry" size="mini" @tap="load">重试</button>
    </view>

    <template v-else-if="paper">
      <view class="card">
        <view class="title">{{ paper.title }}</view>
        <view class="grid">
          <view class="cell"><text class="num">{{ paper.questionCount }}</text><text class="muted">题量</text></view>
          <view class="cell"><text class="num">{{ paper.totalScore }}</text><text class="muted">满分</text></view>
          <view class="cell"><text class="num">{{ paper.passScore }}</text><text class="muted">及格</text></view>
          <view class="cell"><text class="num">{{ paper.durationMin }}</text><text class="muted">分钟</text></view>
        </view>
        <view v-if="paper.description" class="desc">{{ paper.description }}</view>
      </view>

      <view class="card">
        <view class="card-title">判分规则</view>
        <view class="text">{{ paper.scoringRules || '单选、判断完全一致得分；多选全对才得分；填空每空都对才得分。未答 0 分。' }}</view>
      </view>

      <view class="card">
        <view class="card-title">考试须知</view>
        <view class="rule">· 开考后开始计时，中途离开不会暂停，到时间自动交卷。</view>
        <view class="rule">· 每道题的答案先存在本机，再自动上传；每题旁边会显示「已保存」或「未同步」。</view>
        <view class="rule">· 网络断开时可以继续作答，联网后自动补传；截止前没有传到服务器的答案不计分。</view>
        <view class="rule">· App 被关掉后重新打开，可以从「继续考试」接着答。</view>
        <view class="rule">· 交卷后才能看到答案和解析，答错的题会进错题本。</view>
      </view>

      <view class="footer">
        <button class="btn-primary" :loading="starting" :disabled="starting" @tap="start">
          {{ paper.inProgressRecordId ? '继续考试' : '开始考试' }}
        </button>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { examApi } from '@/api'
import type { ExamNotice, PoolGap } from '@/api/types'
import { useTheme } from '@/composables/useTheme'
import { TYPE_LABEL } from '@/logic/answer'
import { ApiError } from '@/logic/http'
import { PAGES } from '@/utils/nav'

const { themeClass } = useTheme()

const paperId = ref(0)
const paper = ref<ExamNotice | null>(null)
const state = ref<'loading' | 'ready' | 'error'>('loading')
const starting = ref(false)

onLoad((query) => {
  paperId.value = Number(query?.paperId)
  load()
})

async function load() {
  state.value = 'loading'
  try {
    paper.value = await examApi.notice(paperId.value)
    state.value = 'ready'
  } catch {
    state.value = 'error'
  }
}

async function start() {
  starting.value = true
  try {
    // 已有进行中的同卷考试时后端直接返回那一场
    const sheet = await examApi.start(paperId.value)
    uni.redirectTo({ url: `${PAGES.examAnswer}?recordId=${sheet.recordId}` })
  } catch (e) {
    if (e instanceof ApiError && e.code === 'POOL_INSUFFICIENT') {
      const gaps = ((e.data as { gaps?: PoolGap[] } | null)?.gaps ?? [])
        .map((g) => `${g.categoryPath} ${TYPE_LABEL[g.type]}：需要 ${g.need}，只有 ${g.available}`)
        .join('\n')
      uni.showModal({ title: '题库不够组卷', content: gaps || '这份试卷暂时组不出来，请联系管理员', showCancel: false })
    }
  } finally {
    starting.value = false
  }
}
</script>

<style lang="scss" scoped>
.notice {
  padding-bottom: 96px;
}

.title {
  font-size: var(--fs-xl);
  font-weight: 700;
  margin-bottom: 12px;
}

.grid {
  display: flex;
}

.cell {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.num {
  font-size: var(--fs-xl);
  font-weight: 700;
  color: var(--c-primary);
}

.desc,
.text {
  margin-top: 10px;
  white-space: pre-wrap;
  color: var(--c-text-2);
  font-size: var(--fs-sm);
}

.rule {
  font-size: var(--fs-sm);
  color: var(--c-text-2);
  margin-bottom: 4px;
}

.footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
  background: var(--c-card);
  border-top: 1px solid var(--c-border);
}

.retry {
  margin-top: 12px;
}
</style>
