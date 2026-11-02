/**
 * 页面里一个「按目标考试显示」的模块：首页科目、统计各块、考试列表、排行榜……
 * 用法：const weak = useTargetData((examId) => statsApi.weak(30, 5))；模板里用 weak.data / weak.status / weak.error，
 * onShow 或下拉刷新时调用 weak.refresh()。切换目标考试后旧数据立即不再显示，迟到的旧请求被丢弃。
 */
import { computed, shallowRef, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { finishLoad, startLoad, viewOf, type Slot } from '@/logic/scoped'

export function useTargetData<T>(loader: (targetId: number) => Promise<T>) {
  const user = useUserStore()
  const slot = shallowRef<Slot<T> | null>(null)

  const view = computed(() => viewOf(slot.value, user.targetCategoryId))
  const data = computed(() => view.value.data)
  const status = computed(() => view.value.status)
  const error = computed(() => view.value.error)

  // 换了目标考试：立刻清空，不等下一次加载
  watch(
    () => user.targetCategoryId,
    (id, old) => {
      if (id !== old) slot.value = null
    },
  )

  async function refresh(): Promise<void> {
    const target = user.targetCategoryId
    if (!target) return
    slot.value = startLoad(slot.value, target)
    try {
      const value = await loader(target)
      slot.value = finishLoad(slot.value, target, { ok: true, data: value })
    } catch (e) {
      slot.value = finishLoad(slot.value, target, { ok: false, error: e })
    }
  }

  return { data, status, error, refresh }
}
