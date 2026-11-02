<template>
  <view class="uchart" :style="{ height: height + 'px' }">
    <canvas
      :id="canvasId"
      :canvas-id="canvasId"
      class="uchart-canvas"
      :style="{ width: width + 'px', height: height + 'px' }"
      @touchstart="onTouch"
    />
  </view>
</template>

<script setup lang="ts">
/**
 * uCharts（@qiun/ucharts）的薄封装：传 type / categories / series / opts，数据变了就重画。
 * 颜色按夜间模式切换；宽度默认是卡片内宽（屏宽减去卡片外边距和内边距）。
 */
import { getCurrentInstance, onMounted, watch } from 'vue'
import uCharts from '@qiun/ucharts'
import { storeToRefs } from 'pinia'
import { useSettingsStore } from '@/stores/settings'

export interface ChartSeries {
  name: string
  data: (number | null)[]
  index?: number
  type?: string
  color?: string
  format?: string
}

const props = withDefaults(
  defineProps<{
    type: 'line' | 'column' | 'bar' | 'area'
    categories: string[]
    series: ChartSeries[]
    height?: number
    /** 额外的 uCharts 配置，会和默认配置浅合并（extra/xAxis/yAxis 各自再合并一层） */
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    opts?: Record<string, any>
  }>(),
  { height: 220, opts: () => ({}) },
)

const PALETTE = ['#3a6cf4', '#18a058', '#f0a020', '#d03050', '#8a5cf6']
const instance = getCurrentInstance()
const canvasId = `uc${Math.random().toString(36).slice(2, 10)}`
const info = uni.getSystemInfoSync()
/** 卡片左右外边距 12 + 内边距 16 */
const width = info.windowWidth - 56
const { dark } = storeToRefs(useSettingsStore())

let chart: InstanceType<typeof uCharts> | null = null

function draw() {
  if (!props.categories.length) return
  // 第二个参数是组件实例：组件里的 canvas 要用它才能找到
  const ctx = uni.createCanvasContext(canvasId, instance?.proxy as unknown as undefined)
  const fontColor = dark.value ? '#a9b0bd' : '#5c6575'
  const gridColor = dark.value ? '#2b303b' : '#e6e9f0'
  const o = props.opts
  chart = new uCharts({
    type: props.type,
    context: ctx,
    width,
    height: props.height,
    pixelRatio: 1,
    categories: props.categories,
    series: props.series,
    color: PALETTE,
    animation: false,
    background: 'transparent',
    fontColor,
    fontSize: 11,
    padding: [12, 12, 4, 4],
    dataLabel: false,
    dataPointShape: true,
    legend: { show: props.series.length > 1, fontColor, position: 'top', float: 'right', ...(o.legend ?? {}) },
    xAxis: { disableGrid: true, fontColor, axisLineColor: gridColor, labelCount: 6, ...(o.xAxis ?? {}) },
    yAxis: { gridType: 'dash', gridColor, dashLength: 4, ...(o.yAxis ?? {}) },
    extra: {
      line: { type: 'straight', width: 2, activeType: 'hollow' },
      column: { width: 14, activeBgColor: '#000000', activeBgOpacity: 0.08 },
      bar: { width: 14, activeBgColor: '#000000', activeBgOpacity: 0.08 },
      tooltip: { showBox: true },
      ...(o.extra ?? {}),
    },
    ...Object.fromEntries(Object.entries(o).filter(([k]) => !['legend', 'xAxis', 'yAxis', 'extra'].includes(k))),
  })
}

function onTouch(e: unknown) {
  chart?.showToolTip?.(e)
}

onMounted(() => {
  // 等 canvas 节点挂上再画
  setTimeout(draw, 50)
})

watch(
  () => [props.categories, props.series, props.opts, dark.value],
  () => setTimeout(draw, 0),
  { deep: true },
)
</script>

<style scoped>
.uchart {
  width: 100%;
  overflow: hidden;
}

.uchart-canvas {
  display: block;
}
</style>
