<template>
  <view class="page-root fav" :class="themeClass">
    <scroll-view scroll-x class="chips">
      <view class="chip" :class="{ on: categoryId === null }" @tap="pick(null)">全部</view>
      <view v-for="ch in chapters" :key="ch.id" class="chip" :class="{ on: categoryId === ch.id }" @tap="pick(ch.id)">
        {{ ch.name }}
      </view>
    </scroll-view>

    <view class="toolbar">
      <text class="muted">共 {{ total }} 题</text>
      <button class="btn-primary practice" size="mini" :disabled="!list.length" @tap="practice">收藏练习</button>
    </view>

    <view v-if="state === 'error' && !list.length" class="empty">
      <view>收藏没加载出来</view>
      <button class="btn-plain retry" size="mini" @tap="fetchPage(1)">重试</button>
    </view>
    <view v-else-if="state === 'ready' && !list.length" class="empty">还没有收藏的题，做题时点底部「收藏」就会出现在这里</view>

    <view v-for="item in list" :key="item.questionId" class="card item" @tap="openDetail(item.questionId)">
      <view class="item-top">
        <text class="tag">{{ TYPE_LABEL[item.type] }}</text>
        <text class="muted path">{{ item.categoryPath }}</text>
      </view>
      <text>{{ item.stemPreview }}</text>
      <view v-if="item.note" class="note">{{ item.note }}</view>
      <view class="ops">
        <text class="muted">{{ formatDateTime(item.createdAt) }} 收藏</text>
        <text class="op" @tap.stop="editNote(item)">{{ item.note ? '改笔记' : '写笔记' }}</text>
        <text class="op" @tap.stop="remove(item)">取消收藏</text>
      </view>
    </view>

    <view v-if="list.length" class="muted more">{{ state === 'loading' ? '加载中…' : hasMore ? '上拉加载更多' : '没有更多了' }}</view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { categoryApi, favoriteApi, practiceApi } from '@/api'
import type { CategoryNode, FavoriteItem } from '@/api/types'
import { useTheme } from '@/composables/useTheme'
import { useUserStore } from '@/stores/user'
import { TYPE_LABEL } from '@/logic/answer'
import { formatDateTime } from '@/logic/format'
import { PAGES } from '@/utils/nav'

const { themeClass } = useTheme()
const user = useUserStore()

const chapters = ref<CategoryNode[]>([])
const list = ref<FavoriteItem[]>([])
const categoryId = ref<number | null>(null)
const page = ref(1)
const total = ref(0)
const state = ref<'loading' | 'ready' | 'error'>('loading')
const hasMore = computed(() => list.value.length < total.value)

onShow(() => {
  if (!chapters.value.length && user.targetCategoryId) {
    categoryApi
      .tree(user.targetCategoryId)
      .then((root) => (chapters.value = (root.children ?? []).flatMap((s) => s.children ?? [])))
      .catch(() => {})
  }
  fetchPage(1)
})

onPullDownRefresh(async () => {
  await fetchPage(1)
  uni.stopPullDownRefresh()
})

onReachBottom(() => {
  if (hasMore.value && state.value !== 'loading') fetchPage(page.value + 1)
})

async function fetchPage(p: number) {
  state.value = 'loading'
  try {
    const res = await favoriteApi.list({ categoryId: categoryId.value ?? user.targetCategoryId ?? undefined, page: p, size: 20 })
    list.value = p === 1 ? res.items : [...list.value, ...res.items]
    total.value = res.total
    page.value = p
    state.value = 'ready'
  } catch {
    state.value = 'error'
  }
}

function pick(id: number | null) {
  if (categoryId.value === id) return
  categoryId.value = id
  fetchPage(1)
}

function openDetail(id: number) {
  uni.navigateTo({ url: `${PAGES.question}?id=${id}` })
}

function editNote(item: FavoriteItem) {
  uni.showModal({
    title: '笔记',
    editable: true,
    placeholderText: '最多 200 字',
    content: item.note ?? '',
    success: async (res) => {
      if (!res.confirm) return
      const note = (res.content ?? '').trim()
      if (note.length > 200) {
        uni.showToast({ title: '笔记最多 200 字', icon: 'none' })
        return
      }
      try {
        await favoriteApi.save(item.questionId, note)
        item.note = note || null
        uni.showToast({ title: '已保存', icon: 'none' })
      } catch {
        /* 请求层已提示 */
      }
    },
  })
}

function remove(item: FavoriteItem) {
  uni.showModal({
    title: '取消收藏',
    content: item.note ? '取消收藏会同时删除这题的笔记。' : '确定取消收藏这道题吗？',
    success: async (r) => {
      if (!r.confirm) return
      try {
        await favoriteApi.remove(item.questionId)
        list.value = list.value.filter((x) => x.questionId !== item.questionId)
        total.value = Math.max(0, total.value - 1)
      } catch {
        /* 请求层已提示 */
      }
    },
  })
}

/** 收藏练习：选了章节就练本章的收藏，否则练目标考试下的全部收藏（最多 100 道） */
async function practice() {
  const ids = categoryId.value ? [categoryId.value] : user.targetCategoryId ? [user.targetCategoryId] : []
  uni.showLoading({ title: '准备题目' })
  try {
    const session = await practiceApi.start({ mode: 'FAVORITE', categoryIds: ids }, ['POOL_INSUFFICIENT'])
    uni.hideLoading()
    uni.navigateTo({ url: `${PAGES.practiceAnswer}?id=${session.id}` })
  } catch (e) {
    uni.hideLoading()
    if ((e as { code?: string }).code === 'POOL_INSUFFICIENT') uni.showToast({ title: '这里没有收藏的题', icon: 'none' })
  }
}
</script>

<style lang="scss" scoped>
.fav {
  padding-bottom: 16px;
}

.chips {
  white-space: nowrap;
  padding: 12px 12px 0;
  box-sizing: border-box;
}

.chip {
  display: inline-block;
  margin-right: 8px;
  padding: 4px 12px;
  border-radius: 16px;
  background: var(--c-card);
  color: var(--c-text-2);
  font-size: var(--fs-sm);

  &.on {
    background: var(--c-primary);
    color: #fff;
  }
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px 0;
}

.practice {
  margin: 0;
}

.item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.item-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.path {
  font-size: var(--fs-xs);
}

.note {
  padding: 6px 10px;
  border-radius: 8px;
  background: var(--c-warn-weak);
  font-size: var(--fs-sm);
  white-space: pre-wrap;
}

.ops {
  display: flex;
  align-items: center;
  gap: 14px;

  .muted {
    flex: 1;
  }
}

.op {
  font-size: var(--fs-sm);
  color: var(--c-text-2);
}

.more {
  text-align: center;
  padding: 8px 0 16px;
}

.retry {
  margin-top: 12px;
}
</style>
