<template>
  <view class="page-root home" :class="themeClass">
    <!-- 目标考试和今日数据 -->
    <view class="hero">
      <view class="hero-top">
        <view class="hero-exam-box">
          <text class="hero-label">目标考试</text>
          <text class="hero-exam">{{ user.targetCategoryName || '未选择' }}</text>
        </view>
        <view class="hero-switch" @tap="goTarget">切换</view>
      </view>
      <view class="hero-stats">
        <view class="hero-stat">
          <text class="hero-num">{{ overview?.todayAnswerCount ?? '—' }}</text>
          <text class="hero-cap">今日作答</text>
        </view>
        <view class="hero-stat">
          <text class="hero-num">{{ overview?.answerCount ?? '—' }}</text>
          <text class="hero-cap">累计作答</text>
        </view>
        <view class="hero-stat">
          <text class="hero-num">{{ formatRate(overview?.correctRate) }}</text>
          <text class="hero-cap">正确率</text>
        </view>
      </view>
    </view>

    <view class="search" @tap="go(PAGES.search)">
      <uni-icons type="search" size="18" color="#98a0ad" />
      <text class="search-text">按关键词搜题</text>
    </view>

    <!-- 快捷入口 -->
    <view class="actions">
      <view class="action" @tap="go(PAGES.practiceSetup)">
        <view class="action-icon a-blue"><uni-icons type="loop" size="24" color="#fff" /></view>
        <text class="action-name">随机刷题</text>
      </view>
      <view class="action" @tap="startDaily">
        <view class="action-icon a-green"><uni-icons type="calendar" size="24" color="#fff" /></view>
        <text class="action-name">每日一练</text>
      </view>
      <view class="action" @tap="go(PAGES.wrong)">
        <view class="action-icon a-red"><uni-icons type="closeempty" size="24" color="#fff" /></view>
        <text class="action-name">错题本</text>
        <text v-if="wrongTotal > 0" class="badge">{{ wrongTotal > 99 ? '99+' : wrongTotal }}</text>
      </view>
      <view class="action" @tap="continuePractice">
        <view class="action-icon a-orange"><uni-icons type="redo" size="24" color="#fff" /></view>
        <text class="action-name">继续练习</text>
      </view>
    </view>

    <view v-if="current" class="card continue" @tap="continuePractice">
      <view class="continue-main">
        <text class="continue-title">继续上次的{{ MODE_LABEL[current.mode] || '练习' }}</text>
        <text class="muted">已答 {{ current.answeredCount }} / {{ current.questionCount }} 题 · {{ formatDateTime(current.startedAt) }} 开始</text>
      </view>
      <uni-icons type="right" size="18" color="#98a0ad" />
    </view>

    <!-- 科目与章节 -->
    <view class="section-title">科目与章节</view>
    <view v-if="treeState === 'loading'" class="empty">加载中…</view>
    <view v-else-if="treeState === 'error'" class="empty">
      <view>题库没加载出来</view>
      <button class="btn-plain retry" size="mini" @tap="load">重试</button>
    </view>
    <view v-else-if="!subjects.length" class="empty">这个考试下还没有题目</view>

    <view v-for="subject in subjects" :key="subject.id" class="card subject">
      <view class="subject-head" @tap="toggle(subject.id)">
        <view class="subject-main">
          <text class="subject-name">{{ subject.name }}</text>
          <text class="muted">
            {{ subject.questionCount }} 题 · 已做 {{ subject.doneCount }} · 正确率 {{ formatRate(subject.correctRate) }}
          </text>
        </view>
        <uni-icons :type="expanded[subject.id] ? 'up' : 'down'" size="16" color="#98a0ad" />
      </view>
      <view v-if="expanded[subject.id]" class="chapters">
        <view v-if="!subject.children?.length" class="muted chapter-empty">暂无章节</view>
        <view v-for="ch in subject.children" :key="ch.id" class="chapter" @tap="openChapter(ch)">
          <view class="chapter-top">
            <text class="chapter-name">{{ ch.name }}</text>
            <text class="chapter-rate" :class="rateClass(ch.correctRate)">{{ formatRate(ch.correctRate) }}</text>
          </view>
          <view class="bar">
            <view class="bar-fill" :style="{ width: progress(ch) + '%' }" />
          </view>
          <text class="muted">已做 {{ ch.doneCount }} / {{ ch.questionCount }} 题 · 正确率 {{ formatRate(ch.correctRate) }}</text>
        </view>
      </view>
    </view>
    <view class="bottom-space" />
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { categoryApi, practiceApi, statsApi, wrongApi } from '@/api'
import type { CategoryNode, PracticeSession, StatsOverview } from '@/api/types'
import { useTheme } from '@/composables/useTheme'
import { useUserStore } from '@/stores/user'
import { formatDateTime, formatRate } from '@/logic/format'
import { PAGES, ensureReady } from '@/utils/nav'
import { MODE_LABEL } from '@/utils/labels'

const { themeClass } = useTheme()
const user = useUserStore()

const tree = ref<CategoryNode | null>(null)
const treeState = ref<'loading' | 'ready' | 'error'>('loading')
const overview = ref<StatsOverview | null>(null)
const current = ref<PracticeSession | null>(null)
const wrongTotal = ref(0)
const expanded = reactive<Record<number, boolean>>({})
let loadedFor: number | null = null

const subjects = computed(() => tree.value?.children ?? [])

onShow(() => {
  if (!ensureReady()) return
  // 切换目标考试后重新加载并清空展开状态；否则静默刷新数字
  if (loadedFor !== user.targetCategoryId) {
    treeState.value = 'loading'
    Object.keys(expanded).forEach((k) => delete expanded[Number(k)])
  }
  load()
})

onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})

async function load() {
  const rootId = user.targetCategoryId
  if (!rootId) return
  const [treeRes, overviewRes, currentRes, groupsRes] = await Promise.allSettled([
    categoryApi.tree(rootId),
    statsApi.overview(),
    practiceApi.current(),
    wrongApi.groups(),
  ])
  if (treeRes.status === 'fulfilled') {
    tree.value = treeRes.value
    treeState.value = 'ready'
    loadedFor = rootId
    const first = subjects.value[0]
    if (first && Object.keys(expanded).length === 0) expanded[first.id] = true
  } else if (!tree.value) {
    treeState.value = 'error'
  }
  if (overviewRes.status === 'fulfilled') overview.value = overviewRes.value
  if (currentRes.status === 'fulfilled') current.value = currentRes.value
  if (groupsRes.status === 'fulfilled') wrongTotal.value = groupsRes.value.reduce((sum, g) => sum + g.count, 0)
}

function toggle(id: number) {
  expanded[id] = !expanded[id]
}

function progress(ch: CategoryNode): number {
  if (!ch.questionCount) return 0
  return Math.min(100, Math.round((ch.doneCount / ch.questionCount) * 100))
}

function rateClass(rate: number | null): string {
  if (rate === null) return ''
  if (rate >= 0.8) return 'good'
  if (rate < 0.6) return 'weak'
  return ''
}

function go(url: string) {
  uni.navigateTo({ url })
}

function goTarget() {
  uni.navigateTo({ url: PAGES.target })
}

/** 点章节：顺序练习（同章有未完成的就接着做）或随机刷题 */
function openChapter(ch: CategoryNode) {
  uni.showActionSheet({
    itemList: ['顺序练习（接着上次做）', '随机刷题'],
    success: async (res) => {
      if (res.tapIndex === 1) {
        uni.navigateTo({ url: `${PAGES.practiceSetup}?categoryId=${ch.id}` })
        return
      }
      uni.showLoading({ title: '准备题目' })
      try {
        const session = await practiceApi.start({ mode: 'SEQUENTIAL', categoryIds: [ch.id] }, ['POOL_INSUFFICIENT'])
        uni.hideLoading()
        uni.navigateTo({ url: `${PAGES.practiceAnswer}?id=${session.id}` })
      } catch (e) {
        uni.hideLoading()
        if ((e as { code?: string }).code === 'POOL_INSUFFICIENT') uni.showToast({ title: '这一章还没有题', icon: 'none' })
      }
    },
  })
}

async function startDaily() {
  uni.showLoading({ title: '准备题目' })
  try {
    const session = await practiceApi.start({ mode: 'DAILY' })
    uni.hideLoading()
    uni.navigateTo({ url: `${PAGES.practiceAnswer}?id=${session.id}` })
  } catch {
    uni.hideLoading()
  }
}

async function continuePractice() {
  // 以服务器为准再取一次，避免显示的是旧数据
  try {
    const latest = await practiceApi.current(false)
    current.value = latest
    if (!latest) {
      uni.showToast({ title: '没有未完成的练习', icon: 'none' })
      return
    }
    uni.navigateTo({ url: `${PAGES.practiceAnswer}?id=${latest.id}` })
  } catch {
    /* 请求层已提示 */
  }
}

</script>

<style lang="scss" scoped>
.home {
  padding-bottom: 12px;
}

.hero {
  margin: 12px;
  padding: 16px;
  border-radius: 14px;
  background: linear-gradient(135deg, #3a6cf4, #5b8cff);
  color: #fff;
}

.hero-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.hero-exam-box {
  display: flex;
  flex-direction: column;
}

.hero-label {
  font-size: var(--fs-xs);
  opacity: 0.85;
}

.hero-exam {
  font-size: var(--fs-xl);
  font-weight: 700;
}

.hero-switch {
  font-size: var(--fs-sm);
  padding: 2px 12px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 14px;
}

.hero-stats {
  display: flex;
  margin-top: 14px;
}

.hero-stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.hero-num {
  font-size: var(--fs-xl);
  font-weight: 700;
}

.hero-cap {
  font-size: var(--fs-xs);
  opacity: 0.85;
}

.search {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 12px;
  padding: 0 14px;
  height: 40px;
  border-radius: 20px;
  background: var(--c-card);
}

.search-text {
  color: var(--c-text-3);
  font-size: var(--fs-sm);
}

.actions {
  display: flex;
  justify-content: space-around;
  margin: 12px;
  padding: 14px 0;
  border-radius: 12px;
  background: var(--c-card);
}

.action {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.action-icon {
  width: 46px;
  height: 46px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.a-blue {
  background: #3a6cf4;
}

.a-green {
  background: #18a058;
}

.a-red {
  background: #e05a6f;
}

.a-orange {
  background: #f0a020;
}

.action-name {
  font-size: var(--fs-sm);
}

.badge {
  position: absolute;
  top: -6px;
  right: -8px;
  min-width: 18px;
  padding: 0 5px;
  height: 18px;
  line-height: 18px;
  border-radius: 9px;
  background: var(--c-wrong);
  color: #fff;
  font-size: 11px;
  text-align: center;
}

.continue {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.continue-main {
  display: flex;
  flex-direction: column;
}

.continue-title {
  font-weight: 600;
}

.section-title {
  margin: 18px 16px 0;
  font-weight: 600;
  font-size: var(--fs-lg);
}

.subject {
  padding: 0;
  overflow: hidden;
}

.subject-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
}

.subject-main {
  display: flex;
  flex-direction: column;
}

.subject-name {
  font-weight: 600;
  font-size: var(--fs-lg);
}

.chapters {
  border-top: 1px solid var(--c-border);
}

.chapter-empty {
  padding: 12px 16px;
}

.chapter {
  padding: 12px 16px;
  border-bottom: 1px solid var(--c-border);

  &:last-child {
    border-bottom: none;
  }
}

.chapter-top {
  display: flex;
  justify-content: space-between;
}

.chapter-name {
  font-weight: 500;
}

.chapter-rate {
  font-weight: 600;
  color: var(--c-text-2);

  &.good {
    color: var(--c-correct);
  }

  &.weak {
    color: var(--c-wrong);
  }
}

.bar {
  height: 6px;
  margin: 6px 0 4px;
  border-radius: 3px;
  background: var(--c-bg);
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 3px;
  background: var(--c-primary);
}

.retry {
  margin-top: 12px;
}

.bottom-space {
  height: 8px;
}
</style>
