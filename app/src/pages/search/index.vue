<template>
  <view class="page-root search" :class="themeClass">
    <view class="bar">
      <!-- 搜索范围：默认整个目标考试，可缩到科目或章节（PM-M3-13） -->
      <view class="scope-btn" :class="{ on: scopeId !== null }" @tap="openScope">
        <text class="scope-btn-text">{{ scopeShort }}</text>
        <text class="caret">▾</text>
      </view>
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
    <view class="scope-line">
      <text class="scope-label">范围：{{ currentLabel }}</text>
      <text v-if="scopeId !== null" class="scope-clear" @tap="pick(null)">清空</text>
    </view>

    <view v-if="state === 'idle'" class="empty">按题干关键词搜索</view>
    <view v-else-if="state === 'loading' && !items.length" class="empty">搜索中…</view>
    <view v-else-if="state === 'error' && !items.length" class="empty">
      <view>搜索失败</view>
      <button class="btn-plain retry" size="mini" @tap="search(1)">重试</button>
    </view>
    <view v-else-if="state === 'ready' && !items.length" class="empty">
      在「{{ lastLabel }}」里没有找到包含「{{ lastKeyword }}」的题，换个词或放大范围试试
    </view>

    <view v-if="items.length" class="muted count">共 {{ total }} 道题</view>
    <view v-for="hit in items" :key="hit.id" class="card hit" @tap="open(hit.id)">
      <view class="hit-top">
        <text class="tag">{{ TYPE_LABEL[hit.type] }}</text>
        <text class="muted path">{{ hit.categoryPath }}</text>
      </view>
      <text>{{ hit.stemPreview }}</text>
    </view>
    <view v-if="items.length" class="muted more">{{ state === 'loading' ? '加载中…' : hasMore ? '上拉加载更多' : '没有更多了' }}</view>

    <uni-popup ref="scopePopup" type="bottom" background-color="transparent">
      <view class="sheet">
        <view class="sheet-head">
          <text class="sheet-title">搜索范围</text>
          <text class="muted" @tap="scopePopup?.close()">关闭</text>
        </view>
        <scroll-view scroll-y class="sheet-body">
          <view class="opt" :class="{ on: scopeId === null }" @tap="pick(null)">整个考试 · {{ user.targetCategoryName }}</view>
          <view v-if="treeState === 'loading'" class="muted sheet-tip">科目和章节加载中…</view>
          <view v-else-if="treeState === 'error'" class="sheet-tip">
            <text class="muted">科目和章节没加载出来</text>
            <button class="btn-plain retry" size="mini" @tap="loadTree">重试</button>
          </view>
          <view v-for="s in tree?.children ?? []" :key="s.id" class="subject">
            <view class="opt" :class="{ on: scopeId === s.id }" @tap="pick(s.id)">
              {{ s.name }}<text class="opt-count">{{ s.questionCount }} 题</text>
            </view>
            <view class="chips">
              <view v-for="ch in s.children ?? []" :key="ch.id" class="chip" :class="{ on: scopeId === ch.id }" @tap="pick(ch.id)">
                {{ ch.name }}<text class="chip-count">{{ ch.questionCount }}</text>
              </view>
            </view>
          </view>
        </scroll-view>
      </view>
    </uni-popup>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onReachBottom, onShow } from '@dcloudio/uni-app'
import { categoryApi, questionApi } from '@/api'
import type { CategoryNode, SearchHit } from '@/api/types'
import { useTheme } from '@/composables/useTheme'
import { useUserStore } from '@/stores/user'
import { TYPE_LABEL } from '@/logic/answer'
import { keepScope, scopeLabel, scopeOptions, searchCategoryId } from '@/logic/searchScope'
import { PAGES } from '@/utils/nav'

const PAGE_SIZE = 20

interface PopupRef {
  open: () => void
  close: () => void
}

const { themeClass } = useTheme()
const user = useUserStore()

const keyword = ref('')
const lastKeyword = ref('')
const lastLabel = ref('')
const items = ref<SearchHit[]>([])
const total = ref(0)
const page = ref(1)
const state = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
const hasMore = computed(() => items.value.length < total.value)

/** 选中的科目或章节 id；null = 整个目标考试 */
const scopeId = ref<number | null>(null)
const tree = ref<CategoryNode | null>(null)
const treeState = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
const scopePopup = ref<PopupRef | null>(null)
/** 分类树属于哪个考试；目标考试变了要重新拉 */
let treeFor: number | null = null
/** 本次搜索（含翻页）用的分类，翻页时沿用 */
let lastCategoryId: number | undefined
let seq = 0

const currentLabel = computed(() => scopeLabel(user.targetCategoryName, tree.value, scopeId.value))
const scopeShort = computed(() => {
  if (scopeId.value === null) return '整个考试'
  return scopeOptions(tree.value).find((o) => o.id === scopeId.value)?.name ?? '整个考试'
})

onShow(() => {
  const target = user.targetCategoryId
  if (target === treeFor) return
  // 换了目标考试：范围回到整个考试，上一个考试的搜索结果不再显示
  treeFor = target
  tree.value = null
  scopeId.value = null
  resetResults()
  if (target !== null) loadTree()
})

onReachBottom(() => {
  if (hasMore.value && state.value !== 'loading') search(page.value + 1)
})

async function loadTree() {
  const target = user.targetCategoryId
  if (target === null) return
  treeState.value = 'loading'
  try {
    const root = await categoryApi.tree(target)
    if (target !== user.targetCategoryId) return
    tree.value = root
    scopeId.value = keepScope(root, scopeId.value)
    treeState.value = 'ready'
  } catch {
    if (target === user.targetCategoryId) treeState.value = 'error'
  }
}

function openScope() {
  if (user.targetCategoryId === null) {
    uni.showToast({ title: '先在首页选目标考试，才能按科目、章节缩小范围', icon: 'none' })
    return
  }
  if (treeState.value === 'error' || treeState.value === 'idle') loadTree()
  scopePopup.value?.open()
}

/** 选范围；已经搜过就按新范围重搜 */
function pick(id: number | null) {
  scopePopup.value?.close()
  if (id === scopeId.value) return
  scopeId.value = id
  if (lastKeyword.value) search(1, lastKeyword.value)
}

function resetResults() {
  seq++
  items.value = []
  total.value = 0
  lastKeyword.value = ''
  state.value = 'idle'
}

async function search(p: number, kwOverride?: string) {
  const kw = p === 1 ? kwOverride ?? keyword.value : lastKeyword.value
  if (!kw) {
    uni.showToast({ title: '先输入关键词', icon: 'none' })
    return
  }
  const mine = ++seq
  state.value = 'loading'
  if (p === 1) {
    items.value = []
    lastKeyword.value = kw
    lastLabel.value = currentLabel.value
    lastCategoryId = searchCategoryId(user.targetCategoryId, scopeId.value)
  }
  try {
    const res = await questionApi.search({ keyword: kw, categoryId: lastCategoryId, page: p, size: PAGE_SIZE })
    if (mine !== seq) return
    items.value = p === 1 ? res.items : items.value.concat(res.items)
    total.value = res.total
    page.value = p
    state.value = 'ready'
  } catch {
    if (mine === seq) state.value = 'error'
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
  padding: 12px 12px 6px;
  background: var(--c-card);
}

.scope-btn {
  display: flex;
  align-items: center;
  max-width: 30%;
  height: 38px;
  padding: 0 10px;
  border-radius: 19px;
  background: var(--c-bg);
  color: var(--c-text-2);
  font-size: var(--fs-sm);

  &.on {
    background: var(--c-primary-weak);
    color: var(--c-primary);
  }
}

.scope-btn-text {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.caret {
  margin-left: 2px;
  font-size: var(--fs-xs);
}

.bar-input {
  flex: 1;
  min-width: 0;
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

.scope-line {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 16px 10px;
  background: var(--c-card);
  font-size: var(--fs-xs);
}

.scope-label {
  flex: 1;
  min-width: 0;
  color: var(--c-text-3);
}

.scope-clear {
  color: var(--c-primary);
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

.sheet {
  border-radius: 16px 16px 0 0;
  background: var(--c-card);
  color: var(--c-text);
  padding-bottom: env(safe-area-inset-bottom);
}

.sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px 8px;
}

.sheet-title {
  font-weight: 600;
}

.sheet-body {
  max-height: 60vh;
  padding: 0 16px 16px;
  box-sizing: border-box;
}

.sheet-tip {
  padding: 8px 0;
}

.subject {
  margin-top: 10px;
}

.opt {
  padding: 8px 12px;
  border-radius: 10px;
  font-weight: 600;

  &.on {
    background: var(--c-primary-weak);
    color: var(--c-primary);
  }
}

.opt-count {
  margin-left: 6px;
  font-weight: 400;
  font-size: var(--fs-xs);
  color: var(--c-text-3);
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 6px 12px 0;
}

.chip {
  padding: 6px 12px;
  border-radius: 16px;
  border: 1px solid var(--c-border);
  font-size: var(--fs-sm);
  color: var(--c-text-2);

  &.on {
    border-color: var(--c-primary);
    background: var(--c-primary-weak);
    color: var(--c-primary);
  }
}

.chip-count {
  margin-left: 4px;
  font-size: var(--fs-xs);
  opacity: 0.7;
}
</style>
