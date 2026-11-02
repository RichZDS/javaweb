<template>
  <view class="page-root" :class="themeClass">
    <!-- M1 只放页签入口，试卷列表、考前须知、考试答题、成绩单在 M2 实现 -->
    <view class="empty">模拟考试功能开发中</view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { useTheme } from '@/composables/useTheme'
import { ensureReady } from '@/utils/nav'

const { themeClass } = useTheme()

onShow(() => {
  ensureReady()
})
</script>
