<template>
  <view class="page-root stats" :class="themeClass">
    <!-- 每一块都只显示当前目标考试的数据；某一块失败只影响那一块 -->
    <view class="card">
      <view class="card-title">{{ examName }} · 学习总览</view>
      <view v-if="overview.data.value" class="grid">
        <view v-for="cell in cells" :key="cell.label" class="cell">
          <text class="num">{{ cell.value }}</text>
          <text class="muted">{{ cell.label }}</text>
        </view>
      </view>
      <ModuleError v-else-if="overview.status.value === 'error'" :title="`${examName}的学习总览没加载出来`" :error="overview.error.value" @retry="overview.refresh" />
      <view v-else class="muted">加载中…</view>
    </view>

    <view class="card link-card" @tap="go(PAGES.leaderboard)">
      <uni-icons type="medal" size="22" color="#f0a020" />
      <text class="link-text">本周排行榜</text>
      <uni-icons type="right" size="16" color="#98a0ad" />
    </view>

    <!-- 近 30 天趋势 -->
    <view class="card">
      <view class="card-title">近 30 天趋势</view>
      <template v-if="daily.data.value">
        <view v-if="!trend.total" class="muted">近 30 天还没有作答记录</view>
        <UChart v-else type="line" :categories="trend.categories" :series="trendSeries" :opts="trendOpts" />
      </template>
      <ModuleError v-else-if="daily.status.value === 'error'" :title="`${examName}的趋势没加载出来`" :error="daily.error.value" @retry="daily.refresh" />
      <view v-else class="muted">加载中…</view>
    </view>

    <!-- 章节正确率 -->
    <view class="card">
      <view class="card-title">章节正确率</view>
      <template v-if="categoryStats.data.value">
        <view v-if="!chapters.categories.length" class="muted">还没有做过题的章节</view>
        <UChart
          v-else
          type="bar"
          :categories="chapters.categories"
          :series="[{ name: '正确率 %', data: chapters.rates }]"
          :height="Math.max(160, chapters.categories.length * 34 + 40)"
          :opts="barOpts"
        />
        <view v-if="chapters.skipped" class="muted note">另有 {{ chapters.skipped }} 个章节还没做过，未画出</view>
      </template>
      <ModuleError v-else-if="categoryStats.status.value === 'error'" :title="`${examName}的章节正确率没加载出来`" :error="categoryStats.error.value" @retry="categoryStats.refresh" />
      <view v-else class="muted">加载中…</view>
    </view>

    <!-- 薄弱点 -->
    <view class="card">
      <view class="card-title">薄弱点 <text class="muted">（近 30 天，至少做过 5 道不同题的章节）</text></view>
      <view class="muted note weak-note">每道题只看最近一次作答，所以这里的正确率和上面的章节正确率可能不同。</view>
      <template v-if="weak.data.value">
        <view v-if="!weak.data.value.length" class="muted">暂时没有：多做几道题再来看</view>
        <view v-for="w in weak.data.value" :key="w.categoryId" class="weak" @tap="practiceWeak(w)">
          <view class="weak-main">
            <text class="weak-name">{{ lastSegment(w.categoryPath) }}</text>
            <text class="muted">{{ w.categoryPath }} · {{ w.sampleCount }} 题</text>
          </view>
          <text class="weak-rate">{{ formatRate(w.correctRate) }}</text>
          <text class="weak-go">专项练</text>
        </view>
      </template>
      <ModuleError v-else-if="weak.status.value === 'error'" :title="`${examName}的薄弱点没加载出来`" :error="weak.error.value" @retry="weak.refresh" />
      <view v-else class="muted">加载中…</view>
    </view>

    <!-- 考试分数 -->
    <view class="card">
      <view class="card-title">考试分数</view>
      <template v-if="examStats.data.value">
        <view v-if="!papers.length" class="muted">还没有考试成绩</view>
        <template v-else>
          <scroll-view scroll-x class="papers">
            <text v-for="p in papers" :key="p.paperId" class="paper" :class="{ on: p.paperId === currentPaperId }" @tap="paperId = p.paperId">
              {{ p.title }}
            </text>
          </scroll-view>
          <UChart type="line" :categories="scoreData.categories" :series="[{ name: '得分', data: scoreData.scores }]" :opts="scoreOpts" />
          <view class="muted note">虚线为及格线 {{ scoreData.passScore }} 分（满分 {{ scoreData.totalScore }}）</view>
        </template>
      </template>
      <ModuleError v-else-if="examStats.status.value === 'error'" :title="`${examName}的考试分数没加载出来`" :error="examStats.error.value" @retry="examStats.refresh" />
      <view v-else class="muted">加载中…</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { practiceApi, statsApi } from '@/api'
import type { WeakPoint } from '@/api/types'
import UChart from '@/components/UChart.vue'
import ModuleError from '@/components/ModuleError.vue'
import { useTheme } from '@/composables/useTheme'
import { useTargetData } from '@/composables/useTargetData'
import { useUserStore } from '@/stores/user'
import { formatRate } from '@/logic/format'
import { chapterData, examScoreData, integerSplit, lastSegment, paperOptions, trendData } from '@/logic/stats'
import { PAGES, ensureReady } from '@/utils/nav'

const { themeClass } = useTheme()
const user = useUserStore()
const examName = computed(() => user.targetCategoryName || '当前考试')

// 每块数据按目标考试分开存（PM-M3-05）
const overview = useTargetData((id) => statsApi.overview(id))
const daily = useTargetData((id) => statsApi.daily(id, 30))
const categoryStats = useTargetData((id) => statsApi.categories(id))
const weak = useTargetData((id) => statsApi.weak(id, 30, 5))
const examStats = useTargetData((id) => statsApi.exams(id))
const paperId = ref<number | null>(null)

const cells = computed(() => {
  const o = overview.data.value
  if (!o) return []
  return [
    { label: '累计作答', value: o.answerCount },
    { label: '正确率', value: formatRate(o.correctRate) },
    { label: '今日作答', value: o.todayAnswerCount },
    { label: '学习天数', value: o.studyDays },
    { label: '连续打卡', value: `${o.streakDays} 天` },
    { label: '考试次数', value: o.examCount },
  ]
})

const trend = computed(() => trendData(daily.data.value ?? []))
const trendSeries = computed(() => [
  { name: '作答次数', data: trend.value.answers, index: 0 },
  { name: '正确率 %', data: trend.value.rates, index: 1 },
])
const trendOpts = {
  xAxis: { labelCount: 6 },
  yAxis: { data: [{ min: 0, title: '次' }, { min: 0, max: 100, position: 'right', title: '%' }] },
}

const chapters = computed(() => chapterData(categoryStats.data.value ?? []))
/** 条形图的横轴是数值轴：两端对齐、每个刻度都显示（labelCount 为 0 表示不抽稀） */
const barOpts = {
  // 标出数值：正确率为 0 的章节没有条，也能看出是 0 而不是没数据
  dataLabel: true,
  // 右侧留出数值标签的位置（100 不被截掉）
  padding: [12, 30, 4, 4],
  xAxis: { min: 0, max: 100, boundaryGap: 'justify', labelCount: 0, disableGrid: false, gridType: 'dash' },
  extra: { bar: { width: 14 } },
}
const papers = computed(() => paperOptions(examStats.data.value ?? []))
/** 选中的试卷不在当前考试的成绩里（如刚切换考试）时默认第一份 */
const currentPaperId = computed(() =>
  papers.value.some((p) => p.paperId === paperId.value) ? paperId.value : papers.value[0]?.paperId ?? null,
)
const scoreData = computed(() => examScoreData(examStats.data.value ?? [], currentPaperId.value ?? -1))
const scoreOpts = computed(() => ({
  yAxis: {
    splitNumber: integerSplit(scoreData.value.totalScore),
    data: [{ min: 0, max: scoreData.value.totalScore ?? 100, formatter: (v: number) => String(Math.round(v)) }],
  },
  extra: {
    // 及格线；文字说明放在图下方，避免和纵轴刻度挤在一起
    markLine: { type: 'dash', data: [{ value: scoreData.value.passScore ?? 0, lineColor: '#d03050' }] },
  },
}))

onShow(() => {
  if (ensureReady()) load()
})

onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})

function load() {
  return Promise.all([overview.refresh(), daily.refresh(), categoryStats.refresh(), weak.refresh(), examStats.refresh()])
}

function go(url: string) {
  uni.navigateTo({ url })
}

/** 薄弱点：点一下开这一章的随机练习（10 道，不够就按实际数量） */
async function practiceWeak(w: WeakPoint) {
  uni.showLoading({ title: '准备题目' })
  try {
    const session = await practiceApi.start(
      { mode: 'RANDOM', categoryIds: [w.categoryId], count: 10, scope: 'ALL', acceptFewer: true },
      ['POOL_INSUFFICIENT'],
    )
    uni.hideLoading()
    uni.navigateTo({ url: `${PAGES.practiceAnswer}?id=${session.id}` })
  } catch (e) {
    uni.hideLoading()
    if ((e as { code?: string }).code === 'POOL_INSUFFICIENT') uni.showToast({ title: '这一章没有可练的题', icon: 'none' })
  }
}
</script>

<style lang="scss" scoped>
.stats {
  padding-bottom: 12px;
}

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

.link-card {
  display: flex;
  align-items: center;
  gap: 8px;
}

.link-text {
  flex: 1;
  font-weight: 600;
}

.note {
  margin-top: 6px;
}

.weak-note {
  margin: -4px 0 6px;
}

.weak {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-top: 1px solid var(--c-border);
}

.weak-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.weak-name {
  font-weight: 600;
}

.weak-rate {
  font-weight: 700;
  color: var(--c-wrong);
}

.weak-go {
  font-size: var(--fs-xs);
  color: var(--c-primary);
  border: 1px solid var(--c-primary);
  border-radius: 12px;
  padding: 0 8px;
  line-height: 22px;
}

.papers {
  white-space: nowrap;
  margin-bottom: 8px;
}

.paper {
  display: inline-block;
  margin-right: 8px;
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

.retry {
  margin-top: 12px;
}
</style>
