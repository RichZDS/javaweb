<template>
  <view class="page-root stats" :class="themeClass">
    <view v-if="failed && !overview" class="empty">
      <view>统计没加载出来</view>
      <button class="btn-plain retry" size="mini" @tap="load">重试</button>
    </view>

    <template v-else>
      <!-- 总览 -->
      <view class="card">
        <view class="card-title">{{ user.targetCategoryName }} · 学习总览</view>
        <view v-if="overview" class="grid">
          <view v-for="cell in cells" :key="cell.label" class="cell">
            <text class="num">{{ cell.value }}</text>
            <text class="muted">{{ cell.label }}</text>
          </view>
        </view>
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
        <view v-if="!trend.total" class="muted">近 30 天还没有作答记录</view>
        <UChart
          v-else
          type="line"
          :categories="trend.categories"
          :series="trendSeries"
          :opts="trendOpts"
        />
      </view>

      <!-- 章节正确率 -->
      <view class="card">
        <view class="card-title">章节正确率</view>
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
      </view>

      <!-- 薄弱点 -->
      <view class="card">
        <view class="card-title">薄弱点 <text class="muted">（近 30 天，至少做过 5 道不同题的章节）</text></view>
        <view v-if="!weak.length" class="muted">暂时没有：多做几道题再来看</view>
        <view v-for="w in weak" :key="w.categoryId" class="weak" @tap="practiceWeak(w)">
          <view class="weak-main">
            <text class="weak-name">{{ lastSegment(w.categoryPath) }}</text>
            <text class="muted">{{ w.categoryPath }} · {{ w.sampleCount }} 题</text>
          </view>
          <text class="weak-rate">{{ formatRate(w.correctRate) }}</text>
          <text class="weak-go">专项练</text>
        </view>
      </view>

      <!-- 考试分数 -->
      <view class="card">
        <view class="card-title">考试分数</view>
        <view v-if="!papers.length" class="muted">还没有考试成绩</view>
        <template v-else>
          <scroll-view scroll-x class="papers">
            <text
              v-for="p in papers"
              :key="p.paperId"
              class="paper"
              :class="{ on: p.paperId === paperId }"
              @tap="paperId = p.paperId"
            >
              {{ p.title }}
            </text>
          </scroll-view>
          <UChart
            type="line"
            :categories="scoreData.categories"
            :series="[{ name: '得分', data: scoreData.scores }]"
            :opts="scoreOpts"
          />
          <view class="muted note">虚线为及格线 {{ scoreData.passScore }} 分（满分 {{ scoreData.totalScore }}）</view>
        </template>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { practiceApi, statsApi } from '@/api'
import type { CategoryStat, DailyStat, ExamScoreStat, StatsOverview, WeakPoint } from '@/api/types'
import UChart from '@/components/UChart.vue'
import { useTheme } from '@/composables/useTheme'
import { useUserStore } from '@/stores/user'
import { formatRate } from '@/logic/format'
import { chapterData, examScoreData, integerSplit, lastSegment, paperOptions, trendData } from '@/logic/stats'
import { PAGES, ensureReady } from '@/utils/nav'

const { themeClass } = useTheme()
const user = useUserStore()

const overview = ref<StatsOverview | null>(null)
const daily = ref<DailyStat[]>([])
const categoryStats = ref<CategoryStat[]>([])
const weak = ref<WeakPoint[]>([])
const examStats = ref<ExamScoreStat[]>([])
const paperId = ref<number | null>(null)
const failed = ref(false)

const cells = computed(() => {
  const o = overview.value
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

const trend = computed(() => trendData(daily.value))
const trendSeries = computed(() => [
  { name: '作答次数', data: trend.value.answers, index: 0 },
  { name: '正确率 %', data: trend.value.rates, index: 1 },
])
const trendOpts = {
  xAxis: { labelCount: 6 },
  yAxis: { data: [{ min: 0, title: '次' }, { min: 0, max: 100, position: 'right', title: '%' }] },
}

const chapters = computed(() => chapterData(categoryStats.value))
/** 条形图的横轴是数值轴：两端对齐、每个刻度都显示（labelCount 为 0 表示不抽稀） */
const barOpts = {
  xAxis: { min: 0, max: 100, boundaryGap: 'justify', labelCount: 0, disableGrid: false, gridType: 'dash' },
  extra: { bar: { width: 14 } },
}
const papers = computed(() => paperOptions(examStats.value))
const scoreData = computed(() => examScoreData(examStats.value, paperId.value ?? -1))
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

async function load() {
  failed.value = false
  const [o, d, c, w, e] = await Promise.allSettled([
    statsApi.overview(),
    statsApi.daily(30),
    statsApi.categories(),
    statsApi.weak(30, 5),
    statsApi.exams(),
  ])
  if (o.status === 'fulfilled') overview.value = o.value
  else failed.value = true
  if (d.status === 'fulfilled') daily.value = d.value
  if (c.status === 'fulfilled') categoryStats.value = c.value
  if (w.status === 'fulfilled') weak.value = w.value
  if (e.status === 'fulfilled') {
    examStats.value = e.value
    if (!papers.value.some((p) => p.paperId === paperId.value)) paperId.value = papers.value[0]?.paperId ?? null
  }
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
