<template>
  <view class="page-root search" :class="themeClass">
    <view class="bar">
      <input
        v-model.trim="keyword"
        class="bar-input"
        confirm-type="search"
        placeholder="输入题干里的关键词"
        maxlength="50"
        focus
        @confirm="search(1)"
      />
      <button class="btn-primary bar-btn" size="mini" @tap="search(1)">搜索</button>
    </view>

    <view v-if="state === 'idle'" class="empty">按题干关键词搜索，当前范围：{{ user.targetCategoryName || '全部考试' }}</view>
    <view v-else-if="state === 'loading' && !items.length" class="empty">搜索中…</view>
    <view v-else-if="state === 'error' && !items.length" class="empty">
      <view>搜索失败</view>
      <button class="btn-plain retry" size="mini" @tap="search(1)">重试</button>
    </view>
    <view v-else-if="state === 'ready' && !items.length" class="empty">没有找到包含「{{ lastKeyword }}」的题，换个词试试</view>

    <view v-if="items.length" class="muted count">共 {{ total }} 道题</view>
    <view v-for="hit in items" :key="hit.id" class="card hit" @tap="open(hit.id)">
      <view class="hit-top">
        <text class="tag">{{ TYPE_LABEL[hit.type] }}</text>
        <text class="muted path">{{ hit.categoryPath }}</text>
      </view>
      <text>{{ hit.stemPreview }}</text>
    </view>
    <view v-if="items.length" class="muted more">{{ state === 'loading' ? '加载中…' : hasMore ? '上拉加载更多' : '没有更多了' }}</view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onReachBottom } from '@dcloudio/uni-app'
import { questionApi } from '@/api'
import type { SearchHit } from '@/api/types'
import { useTheme } from '@/composables/useTheme'
import { useUserStore } from '@/stores/user'
import { TYPE_LABEL } from '@/logic/answer'
import { PAGES } from '@/utils/nav'

const PAGE_SIZE = 20

const { themeClass } = useTheme()
const user = useUserStore()

const keyword = ref('')
const lastKeyword = ref('')
const items = ref<SearchHit[]>([])
const total = ref(0)
const page = ref(1)
const state = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')

const hasMore = computed(() => items.value.length < total.value)

onReachBottom(() => {
  if (hasMore.value && state.value !== 'loading') search(page.value + 1)
})

async function search(p: number) {
  const kw = p === 1 ? keyword.value : lastKeyword.value
  if (!kw) {
    uni.showToast({ title: '先输入关键词', icon: 'none' })
    return
  }
  state.value = 'loading'
  if (p === 1) {
    items.value = []
    lastKeyword.value = kw
  }
  try {
    const res = await questionApi.search({
      keyword: kw,
      categoryId: user.targetCategoryId ?? undefined,
      page: p,
      size: PAGE_SIZE,
    })
    items.value = p === 1 ? res.items : [...items.value, ...res.items]
    total.value = res.total
    page.value = p
    state.value = 'ready'
  } catch {
    state.value = 'error'
  }
}

function open(id: number) {
  uni.navigateTo({ url: `${PAGES.question}?id=${id}` })
}
</script>

<style lang="scss" scoped>
.bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  background: var(--c-card);
}

.bar-input {
  flex: 1;
  height: 38px;
  padding: 0 14px;
  border-radius: 19px;
  background: var(--c-bg);
  color: var(--c-text);
  font-size: var(--fs-sm);
}

.bar-btn {
  margin: 0;
}

.count {
  padding: 10px 16px 0;
}

.hit {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.hit-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.path {
  font-size: var(--fs-xs);
}

.more {
  text-align: center;
  padding: 8px 0 16px;
}

.retry {
  margin-top: 12px;
}
</style>
