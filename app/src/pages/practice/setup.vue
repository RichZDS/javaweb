<template>
  <view class="page-root setup" :class="themeClass">
    <view v-if="state === 'loading'" class="empty">加载中…</view>
    <view v-else-if="state === 'error'" class="empty">
      <view>章节没加载出来</view>
      <button class="btn-plain retry" size="mini" @tap="load">重试</button>
    </view>

    <template v-else>
      <view class="card">
        <view class="card-title">选择章节 <text class="muted">（可多选）</text></view>
        <view v-for="subject in subjects" :key="subject.id" class="subject">
          <view class="subject-head" @tap="toggleSubject(subject)">
            <view class="check" :class="subjectState(subject)">{{ subjectState(subject) === 'all' ? '✓' : subjectState(subject) === 'some' ? '–' : '' }}</view>
            <text class="subject-name">{{ subject.name }}</text>
            <text class="muted">{{ subject.questionCount }} 题</text>
          </view>
          <view class="chips">
            <view
              v-for="ch in subject.children"
              :key="ch.id"
              class="chip"
              :class="{ on: selected.has(ch.id) }"
              @tap="toggleChapter(ch.id)"
            >
              {{ ch.name }}<text class="chip-count">{{ ch.questionCount }}</text>
            </view>
          </view>
        </view>
      </view>

      <view class="card">
        <view class="card-title">题量</view>
        <view class="seg">
          <view v-for="n in COUNTS" :key="n" class="seg-item" :class="{ on: count === n }" @tap="count = n">{{ n }} 题</view>
        </view>

        <view class="card-title gap">题型</view>
        <view class="chips">
          <view v-for="t in TYPES" :key="t" class="chip" :class="{ on: types.has(t) }" @tap="toggleType(t)">
            {{ TYPE_LABEL[t] }}
          </view>
        </view>

        <view class="card-title gap">范围</view>
        <view class="seg">
          <view v-for="s in SCOPES" :key="s" class="seg-item" :class="{ on: scope === s }" @tap="scope = s">
            {{ SCOPE_LABEL[s] }}
          </view>
        </view>

        <view class="recite">
          <view class="recite-text">
            <text>选项顺序打乱</text>
            <text class="muted">单选、多选题的选项随机排一次，防止记住位置</text>
          </view>
          <switch :checked="shuffle" color="#3a6cf4" @change="(e: any) => (shuffle = e.detail.value === true)" />
        </view>

        <view class="recite">
          <view class="recite-text">
            <text>背题模式</text>
            <text class="muted">直接看答案和解析，不计入统计和错题本</text>
          </view>
          <switch :checked="recite" color="#3a6cf4" @change="(e: any) => (recite = e.detail.value === true)" />
        </view>
      </view>

      <view class="footer">
        <text class="muted">已选 {{ selected.size }} 个章节，共 {{ selectedQuestionCount }} 题</text>
        <button class="btn-primary start" :disabled="!canStart || starting" :loading="starting" @tap="start(false)">
          开始{{ recite ? '背题' : '刷题' }}
        </button>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { categoryApi, practiceApi } from '@/api'
import type { CategoryNode, PracticeScope, QuestionType } from '@/api/types'
import { useTheme } from '@/composables/useTheme'
import { useUserStore } from '@/stores/user'
import { TYPE_LABEL } from '@/logic/answer'
import { ApiError } from '@/logic/http'
import { SCOPE_LABEL } from '@/utils/labels'
import { PAGES } from '@/utils/nav'

const COUNTS = [10, 20, 50] as const
const TYPES: QuestionType[] = ['SINGLE', 'MULTIPLE', 'JUDGE', 'FILL']
const SCOPES: PracticeScope[] = ['ALL', 'UNDONE', 'WRONG_EVER']

const { themeClass } = useTheme()
const user = useUserStore()

const state = ref<'loading' | 'ready' | 'error'>('loading')
const subjects = ref<CategoryNode[]>([])
const selected = reactive(new Set<number>())
const count = ref<10 | 20 | 50>(10)
const types = reactive(new Set<QuestionType>(TYPES))
const scope = ref<PracticeScope>('ALL')
const recite = ref(false)
const shuffle = ref(false)
const starting = ref(false)
let preselect: number | null = null

const chapterById = computed(() => {
  const map = new Map<number, CategoryNode>()
  subjects.value.forEach((s) => s.children?.forEach((c) => map.set(c.id, c)))
  return map
})

const selectedQuestionCount = computed(() =>
  [...selected].reduce((sum, id) => sum + (chapterById.value.get(id)?.questionCount ?? 0), 0),
)

const canStart = computed(() => selected.size > 0 && types.size > 0)

onLoad((query) => {
  preselect = query?.categoryId ? Number(query.categoryId) : null
  load()
})

async function load() {
  const rootId = user.targetCategoryId
  if (!rootId) return
  state.value = 'loading'
  try {
    subjects.value = (await categoryApi.tree(rootId)).children ?? []
    applyPreselect()
    state.value = 'ready'
  } catch {
    state.value = 'error'
  }
}

/** 从首页点某章 / 某科进来时，预先勾上 */
function applyPreselect() {
  if (selected.size > 0) return
  if (preselect !== null) {
    const subject = subjects.value.find((s) => s.id === preselect)
    if (subject) subject.children?.forEach((c) => selected.add(c.id))
    else if (chapterById.value.has(preselect)) selected.add(preselect)
  }
}

function toggleChapter(id: number) {
  if (selected.has(id)) selected.delete(id)
  else selected.add(id)
}

function subjectState(subject: CategoryNode): 'all' | 'some' | 'none' {
  const ids = subject.children?.map((c) => c.id) ?? []
  const n = ids.filter((id) => selected.has(id)).length
  if (n === 0) return 'none'
  return n === ids.length ? 'all' : 'some'
}

function toggleSubject(subject: CategoryNode) {
  const ids = subject.children?.map((c) => c.id) ?? []
  if (subjectState(subject) === 'all') ids.forEach((id) => selected.delete(id))
  else ids.forEach((id) => selected.add(id))
}

function toggleType(t: QuestionType) {
  if (types.has(t)) {
    if (types.size === 1) {
      uni.showToast({ title: '至少保留一种题型', icon: 'none' })
      return
    }
    types.delete(t)
  } else types.add(t)
}

async function start(acceptFewer: boolean) {
  if (!canStart.value || starting.value) return
  starting.value = true
  try {
    const session = await practiceApi.start(
      {
        mode: 'RANDOM',
        categoryIds: [...selected],
        count: count.value,
        types: TYPES.filter((t) => types.has(t)),
        scope: scope.value,
        reciteMode: recite.value,
        shuffleOptions: shuffle.value,
        acceptFewer,
      },
      ['POOL_INSUFFICIENT'],
    )
    uni.redirectTo({ url: `${PAGES.practiceAnswer}?id=${session.id}` })
  } catch (e) {
    if (e instanceof ApiError && e.code === 'POOL_INSUFFICIENT') askFewer(e)
  } finally {
    starting.value = false
  }
}

/** 题不够：有题时问要不要按实际数量开始，0 道直接说明 */
function askFewer(e: ApiError) {
  const available = Number((e.data as { available?: number } | null)?.available ?? 0)
  if (available > 0) {
    uni.showModal({
      title: '题目不够',
      content: `符合条件的只有 ${available} 道，要开始吗？`,
      confirmText: '开始',
      success: (res) => {
        if (res.confirm) start(true)
      },
    })
  } else {
    uni.showModal({
      title: '没有可用的题',
      content: '当前章节、题型和范围下没有题目，换个条件试试。',
      showCancel: false,
    })
  }
}
</script>

<style lang="scss" scoped>
.setup {
  padding-bottom: 120px;
}

.gap {
  margin-top: 16px;
}

.subject {
  margin-bottom: 14px;
}

.subject-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.subject-name {
  font-weight: 600;
  flex: 1;
}

.check {
  width: 20px;
  height: 20px;
  line-height: 20px;
  text-align: center;
  border-radius: 4px;
  border: 1px solid var(--c-border);
  font-size: 13px;
  color: #fff;

  &.all,
  &.some {
    background: var(--c-primary);
    border-color: var(--c-primary);
  }
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
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

.seg {
  display: flex;
  border: 1px solid var(--c-border);
  border-radius: 10px;
  overflow: hidden;
}

.seg-item {
  flex: 1;
  text-align: center;
  padding: 8px 0;
  font-size: var(--fs-sm);
  color: var(--c-text-2);
  border-right: 1px solid var(--c-border);

  &:last-child {
    border-right: none;
  }

  &.on {
    background: var(--c-primary);
    color: #fff;
  }
}

.recite {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
}

.recite-text {
  display: flex;
  flex-direction: column;
}

.footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
  background: var(--c-card);
  border-top: 1px solid var(--c-border);
  text-align: center;
}

.start {
  margin-top: 6px;
}

.retry {
  margin-top: 12px;
}
</style>
