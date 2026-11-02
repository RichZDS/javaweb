<template>
  <view class="page-root wrongbook" :class="themeClass">
    <!-- 范围：只看当前目标考试的错题（列表、分组计数、重练都用同一范围） -->
    <view class="scope">
      <text>当前考试：<text class="scope-name">{{ user.targetCategoryName || '未选择' }}</text></text>
      <text class="scope-link" @tap="goTarget">切换</text>
    </view>

    <!-- 按章节分组筛选 -->
    <scroll-view scroll-x class="groups">
      <view class="group" :class="{ on: categoryId === null }" @tap="pick(null)">全部 {{ totalCount }}</view>
      <view
        v-for="g in groups"
        :key="g.categoryId"
        class="group"
        :class="{ on: categoryId === g.categoryId }"
        @tap="pick(g.categoryId)"
      >
        {{ shortPath(g.categoryPath) }} {{ g.count }}
      </view>
    </scroll-view>

    <view class="toolbar">
      <view class="sort">
        <text class="sort-item" :class="{ on: sort === 'RECENT' }" @tap="changeSort('RECENT')">最近做错</text>
        <text class="sort-item" :class="{ on: sort === 'MOST' }" @tap="changeSort('MOST')">错得最多</text>
      </view>
      <button class="btn-primary practice" size="mini" :disabled="!list.length" @tap="practiceGroup">重练{{ categoryId ? '本章' : '全部' }}</button>
    </view>

    <ModuleError v-if="state === 'error' && !list.length" :title="`${user.targetCategoryName}的错题没加载出来`" :error="loadError" @retry="reload()" />
    <view v-else-if="state === 'ready' && !list.length" class="empty">错题本是空的，继续保持 👍</view>

    <view v-for="item in list" :key="item.questionId" class="card item" @tap="openDetail(item)">
      <view class="item-top">
        <text class="tag">{{ TYPE_LABEL[item.type] }}</text>
        <text class="muted path">{{ item.categoryPath }}</text>
      </view>
      <text class="stem">{{ item.stemPreview }}</text>
      <view class="meta">
        <text class="bad">错 {{ item.wrongCount }} 次</text>
        <text class="muted">错题练习连对 {{ item.correctStreak }}/{{ threshold }}</text>
        <text class="muted">{{ formatDateTime(item.lastWrongAt) }}</text>
      </view>
      <view class="last">
        <text class="muted">当时答案：</text>
        <text class="bad">{{ formatAnswer(item.type, item.lastWrongAnswer) }}</text>
      </view>
      <view class="ops">
        <text class="op" @tap.stop="remove(item)">移出错题本</text>
      </view>
    </view>

    <view v-if="list.length" class="muted more">{{ state === 'loading' ? '加载中…' : hasMore ? '上拉加载更多' : '没有更多了' }}</view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { categoryApi, practiceApi, wrongApi } from '@/api'
import ModuleError from '@/components/ModuleError.vue'
import type { CategoryNode, WrongGroup, WrongQuestion } from '@/api/types'
import { useTheme } from '@/composables/useTheme'
import { useUserStore } from '@/stores/user'
import { TYPE_LABEL, formatAnswer } from '@/logic/answer'
import { formatDateTime } from '@/logic/format'
import { PAGES } from '@/utils/nav'

const PAGE_SIZE = 20

const { themeClass } = useTheme()
const user = useUserStore()

const groups = ref<WrongGroup[]>([])
const list = ref<WrongQuestion[]>([])
const categoryId = ref<number | null>(null)
const sort = ref<'RECENT' | 'MOST'>('RECENT')
const page = ref(1)
const total = ref(0)
const state = ref<'loading' | 'ready' | 'error'>('loading')
const loadError = ref<unknown>(null)
let firstShow = true

/** 连续答对几次自动移出，按本人设置（默认 3） */
const threshold = computed(() => user.user?.wrongRemoveThreshold ?? 3)
const totalCount = computed(() => groups.value.reduce((s, g) => s + g.count, 0))
/** 目标考试下所有分类 id：分组只保留属于当前考试的章节（后端默认已按目标考试，这里再兜一层） */
const targetIds = ref<Set<number> | null>(null)
let loadedFor: number | null = null
const hasMore = computed(() => list.value.length < total.value)

onLoad((query) => {
  if (query?.categoryId) categoryId.value = Number(query.categoryId)
})

// 从错题练习回来时数量会变，每次显示都刷新；切换了目标考试则清掉旧范围重新加载
onShow(() => {
  if (loadedFor !== user.targetCategoryId) {
    if (loadedFor !== null) categoryId.value = null
    list.value = []
    groups.value = []
    targetIds.value = null
    loadedFor = user.targetCategoryId
    reload()
  } else {
    reload(!firstShow)
  }
  firstShow = false
})

onPullDownRefresh(async () => {
  await reload()
  uni.stopPullDownRefresh()
})

onReachBottom(() => {
  if (hasMore.value && state.value !== 'loading') fetchPage(page.value + 1)
})

async function reload(quiet = false) {
  loadGroups()
  await fetchPage(1, quiet)
}

async function loadGroups() {
  const rootId = user.targetCategoryId
  try {
    if (rootId && !targetIds.value) targetIds.value = collectIds(await categoryApi.tree(rootId))
    const all = await wrongApi.groups()
    if (rootId !== user.targetCategoryId) return
    groups.value = targetIds.value ? all.filter((g) => targetIds.value!.has(g.categoryId)) : all
  } catch {
    /* 分组是辅助筛选，失败时只显示「全部」 */
  }
}

function collectIds(node: CategoryNode): Set<number> {
  const ids = new Set<number>()
  const walk = (n: CategoryNode) => {
    ids.add(n.id)
    n.children?.forEach(walk)
  }
  walk(node)
  return ids
}

function goTarget() {
  uni.navigateTo({ url: PAGES.target })
}

async function fetchPage(p: number, quiet = false) {
  const target = user.targetCategoryId
  if (!quiet) state.value = 'loading'
  try {
    // 「全部」= 当前目标考试下的全部错题，和「重练全部」同一范围
    const scope = categoryId.value ?? target ?? undefined
    const res = await wrongApi.list({ categoryId: scope, sort: sort.value, page: p, size: PAGE_SIZE })
    if (target !== user.targetCategoryId) return // 请求期间换了考试：丢弃
    list.value = p === 1 ? res.items : [...list.value, ...res.items]
    total.value = res.total
    page.value = p
    state.value = 'ready'
  } catch (e) {
    if (target !== user.targetCategoryId) return
    loadError.value = e
    state.value = 'error'
  }
}

/** 「408 / 数据结构 / 线性表」→「线性表」，分组标签放不下整条路径 */
function shortPath(path: string): string {
  const parts = path.split('/').map((s) => s.trim())
  return parts[parts.length - 1] || path
}

function pick(id: number | null) {
  if (categoryId.value === id) return
  categoryId.value = id
  fetchPage(1)
}

function changeSort(s: 'RECENT' | 'MOST') {
  if (sort.value === s) return
  sort.value = s
  fetchPage(1)
}

function openDetail(item: WrongQuestion) {
  const last = encodeURIComponent(JSON.stringify(item.lastWrongAnswer ?? []))
  uni.navigateTo({ url: `${PAGES.question}?id=${item.questionId}&lastWrong=${last}` })
}

function remove(item: WrongQuestion) {
  uni.showModal({
    title: '移出错题本',
    content: '移出后不再出现在错题本里，作答历史和统计不受影响。以后再答错会重新收录。',
    confirmText: '移出',
    success: async (r) => {
      if (!r.confirm) return
      try {
        await wrongApi.remove(item.questionId)
        list.value = list.value.filter((x) => x.questionId !== item.questionId)
        total.value = Math.max(0, total.value - 1)
        const g = groups.value.find((x) => x.categoryId === item.categoryId)
        if (g) g.count = Math.max(0, g.count - 1)
        uni.showToast({ title: '已移出', icon: 'none' })
      } catch {
        /* 请求层已提示 */
      }
    },
  })
}

/** 错题练习：选了章节就练本章，否则练当前目标考试下的全部错题（最多 100 道） */
async function practiceGroup() {
  const ids = categoryId.value ? [categoryId.value] : user.targetCategoryId ? [user.targetCategoryId] : []
  uni.showLoading({ title: '准备题目' })
  try {
    const session = await practiceApi.start({ mode: 'WRONG', categoryIds: ids }, ['POOL_INSUFFICIENT'])
    uni.hideLoading()
    uni.navigateTo({ url: `${PAGES.practiceAnswer}?id=${session.id}` })
  } catch (e) {
    uni.hideLoading()
    if ((e as { code?: string }).code === 'POOL_INSUFFICIENT') uni.showToast({ title: '这里没有可练的错题', icon: 'none' })
  }
}
</script>

<style lang="scss" scoped>
.wrongbook {
  padding-bottom: 16px;
}

.scope {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px 0;
  font-size: var(--fs-sm);
  color: var(--c-text-2);
}

.scope-name {
  font-weight: 600;
  color: var(--c-text);
}

.scope-link {
  color: var(--c-primary);
}

.groups {
  white-space: nowrap;
  padding: 12px 12px 0;
  box-sizing: border-box;
}

.group {
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

.sort {
  display: flex;
  gap: 14px;
}

.sort-item {
  font-size: var(--fs-sm);
  color: var(--c-text-3);

  &.on {
    color: var(--c-primary);
    font-weight: 600;
  }
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

.stem {
  font-size: var(--fs-base);
}

.meta {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  font-size: var(--fs-sm);
}

.bad {
  color: var(--c-wrong);
  font-size: var(--fs-sm);
}

.last {
  font-size: var(--fs-sm);
}

.ops {
  display: flex;
  justify-content: flex-end;
}

.op {
  font-size: var(--fs-sm);
  color: var(--c-text-2);
  padding: 2px 0 0 12px;
}

.more {
  text-align: center;
  padding: 8px 0 16px;
}

.retry {
  margin-top: 12px;
}
</style>
