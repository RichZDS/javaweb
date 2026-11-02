import { computed, ref, type Ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import type { Page } from '@/api/types'
import { effectiveScope, scopeQuery, type RecordScope } from '@/logic/records'
import { useUserStore } from '@/stores/user'

const PAGE_SIZE = 20

/**
 * 练习记录、考试记录页共用：「当前考试 / 全部考试」切换 + 分页 + 下拉刷新。
 * - 每次回到页面都从第一页重新拉，切了目标考试回来就是新考试的记录
 * - 目标考试变了先清空，不显示上一个考试的记录
 * - 只采用最后一次请求的结果：来回切范围时，先发的请求晚回来也不会覆盖
 */
export function useRecordList<T>(fetchPage: (q: { all?: boolean; page: number; size: number }) => Promise<Page<T>>) {
  const user = useUserStore()
  const chosen = ref<RecordScope>('target')
  const hasTarget = computed(() => user.targetCategoryId !== null)
  const scope = computed(() => effectiveScope(chosen.value, hasTarget.value))

  const list = ref([]) as Ref<T[]>
  const page = ref(1)
  const total = ref(0)
  const state = ref<'loading' | 'ready' | 'error'>('loading')
  const hasMore = computed(() => list.value.length < total.value)

  let seq = 0
  /** 上次显示时的目标考试；undefined 表示还没显示过 */
  let shownFor: number | null | undefined

  async function load(p: number) {
    const mine = ++seq
    state.value = 'loading'
    try {
      const res = await fetchPage({ ...scopeQuery(scope.value), page: p, size: PAGE_SIZE })
      if (mine !== seq) return
      list.value = p === 1 ? res.items : list.value.concat(res.items)
      total.value = res.total
      page.value = p
      state.value = 'ready'
    } catch {
      if (mine !== seq) return
      state.value = 'error'
    }
  }

  function clear() {
    list.value = []
    total.value = 0
  }

  function setScope(next: RecordScope) {
    if (next === scope.value) return
    chosen.value = next
    clear()
    load(1)
  }

  onShow(() => {
    if (shownFor !== undefined && shownFor !== user.targetCategoryId) clear()
    shownFor = user.targetCategoryId
    load(1)
  })
  onPullDownRefresh(async () => {
    await load(1)
    uni.stopPullDownRefresh()
  })
  onReachBottom(() => {
    if (hasMore.value && state.value !== 'loading') load(page.value + 1)
  })

  return { scope, hasTarget, list, state, hasMore, setScope, reload: () => load(1) }
}
