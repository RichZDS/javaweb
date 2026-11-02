<template>
  <view class="page-root" :class="themeClass">
    <view class="intro">
      <text class="intro-title">{{ first ? '先选一个目标考试' : '切换目标考试' }}</text>
      <text class="muted">首页、考试、统计都按目标考试展示；切换后原来的学习记录仍然保留。</text>
    </view>

    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="loadFailed" class="empty">
      <view>考试列表没加载出来</view>
      <button class="btn-plain retry" size="mini" @tap="load">重试</button>
    </view>
    <view v-else-if="!exams.length" class="empty">还没有可选的考试，请联系管理员</view>

    <view
      v-for="exam in exams"
      :key="exam.id"
      class="card exam"
      :class="{ active: exam.id === user.targetCategoryId }"
      @tap="choose(exam)"
    >
      <view class="exam-main">
        <text class="exam-name">{{ exam.name }}</text>
        <text class="muted">共 {{ exam.questionCount }} 题 · 已做 {{ exam.doneCount }} 题</text>
      </view>
      <text v-if="exam.id === user.targetCategoryId" class="tag">当前</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { categoryApi } from '@/api'
import type { CategoryNode } from '@/api/types'
import { useTheme } from '@/composables/useTheme'
import { useUserStore } from '@/stores/user'
import { PAGES, goLogin } from '@/utils/nav'

const { themeClass } = useTheme()
const user = useUserStore()

const first = ref(false)
const exams = ref<CategoryNode[]>([])
const loading = ref(true)
const loadFailed = ref(false)
const saving = ref(false)

onLoad((query) => {
  first.value = query?.first === '1'
  if (!user.loggedIn) {
    goLogin()
    return
  }
  // 首次选择不能返回（没有可以回去的页面）
  if (first.value) uni.hideHomeButton?.()
  load()
})

async function load() {
  loading.value = true
  loadFailed.value = false
  try {
    // 不传 rootId：全部一级分类（考试）
    exams.value = (await categoryApi.tree()).filter((c) => c.level === 1)
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

async function choose(exam: CategoryNode) {
  if (saving.value) return
  if (exam.id === user.targetCategoryId && !first.value) {
    uni.navigateBack()
    return
  }
  saving.value = true
  uni.showLoading({ title: '保存中' })
  try {
    await user.update({ targetCategoryId: exam.id })
    uni.hideLoading()
    uni.showToast({ title: `已切换到 ${exam.name}`, icon: 'none' })
    // 首次选择或切换后都回首页，让各页签按新目标重新加载
    setTimeout(() => uni.switchTab({ url: PAGES.home }), 300)
  } catch {
    uni.hideLoading()
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss" scoped>
.intro {
  padding: 20px 16px 4px;
}

.intro-title {
  display: block;
  font-size: var(--fs-xl);
  font-weight: 700;
  margin-bottom: 4px;
}

.exam {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: 2px solid transparent;

  &.active {
    border-color: var(--c-primary);
  }
}

.exam-main {
  display: flex;
  flex-direction: column;
}

.exam-name {
  font-size: var(--fs-lg);
  font-weight: 600;
}

.retry {
  margin-top: 12px;
}
</style>
