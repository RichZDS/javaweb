<template>
  <div>
    <LoadError v-if="error && !data" :error="error" title="看板没加载出来" @retry="load" />
    <div v-else v-loading="loading && !data">
      <div class="cards">
        <div v-for="c in cards" :key="c.label" class="page-card stat">
          <div class="stat-num">{{ c.value }}</div>
          <div class="muted">{{ c.label }}</div>
        </div>
      </div>

      <div class="page-card">
        <h3>近 14 天作答与活跃人数</h3>
        <v-chart v-if="data?.daily.length" class="chart" :option="dailyOption" autoresize />
        <el-empty v-else description="近 14 天没有数据" />
      </div>

      <div class="page-card">
        <h3>试卷情况</h3>
        <el-table :data="data?.papers ?? []" empty-text="还没有试卷">
          <el-table-column prop="title" label="试卷" min-width="220" />
          <el-table-column prop="attempts" label="考试次数" width="110" />
          <el-table-column label="平均分" width="110">
            <template #default="{ row }">{{ row.avgScore ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="及格率" width="110">
            <template #default="{ row }">{{ formatRate(row.passRate) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { dashboardApi } from '@/api'
import type { Dashboard } from '@/api/types'
import LoadError from '@/components/LoadError.vue'
import { formatRate } from '@/logic/http'

use([CanvasRenderer, LineChart, GridComponent, LegendComponent, TooltipComponent])

const data = ref<Dashboard | null>(null)
const loading = ref(false)
const error = ref<unknown>(null)

const cards = computed(() => {
  const d = data.value
  return [
    { label: '用户数', value: d?.userCount ?? '—' },
    { label: '题目数', value: d?.questionCount ?? '—' },
    { label: '试卷数', value: d?.paperCount ?? '—' },
    { label: '今日作答', value: d?.todayAnswerCount ?? '—' },
    { label: '待处理纠错', value: d?.pendingFeedbackCount ?? '—' },
  ]
})

// 按天的离散数据：直线连接、每天一个点，不做平滑；两条线颜色区分明显，左右轴各写单位
const ANSWER_COLOR = '#3a6cf4'
const ACTIVE_COLOR = '#f08c00'
const dailyOption = computed(() => ({
  color: [ANSWER_COLOR, ACTIVE_COLOR],
  tooltip: { trigger: 'axis' },
  legend: { data: ['作答次数（次）', '活跃人数（人）'] },
  grid: { left: 64, right: 64, top: 48, bottom: 32 },
  xAxis: { type: 'category', data: data.value?.daily.map((d) => d.date.slice(5)) ?? [] },
  yAxis: [
    { type: 'value', name: '作答（次）', minInterval: 1, axisLine: { show: true, lineStyle: { color: ANSWER_COLOR } } },
    { type: 'value', name: '活跃（人）', minInterval: 1, axisLine: { show: true, lineStyle: { color: ACTIVE_COLOR } } },
  ],
  series: [
    {
      name: '作答次数（次）',
      type: 'line',
      smooth: false,
      symbol: 'circle',
      symbolSize: 6,
      data: data.value?.daily.map((d) => d.answerCount) ?? [],
    },
    {
      name: '活跃人数（人）',
      type: 'line',
      smooth: false,
      symbol: 'rect',
      symbolSize: 6,
      yAxisIndex: 1,
      lineStyle: { type: 'dashed' },
      data: data.value?.daily.map((d) => d.activeUsers) ?? [],
    },
  ],
}))

onMounted(load)

async function load() {
  loading.value = true
  error.value = null
  try {
    data.value = await dashboardApi.get()
  } catch (e) {
    error.value = e
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.cards .page-card {
  margin-bottom: 0;
}

.stat {
  text-align: center;
}

.stat-num {
  font-size: 26px;
  font-weight: 700;
  color: #3a6cf4;
}

h3 {
  margin: 0 0 12px;
  font-size: 15px;
}

.chart {
  height: 320px;
}
</style>
