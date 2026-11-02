/** 练习结果在答题页和结果页之间传递（结果页刷新时会自己再取一次） */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { FinishResult } from '@/api/types'

export const usePracticeStore = defineStore('practice', () => {
  const lastResult = ref<{ sessionId: number; result: FinishResult } | null>(null)

  function setResult(sessionId: number, result: FinishResult) {
    lastResult.value = { sessionId, result }
  }

  function takeResult(sessionId: number): FinishResult | null {
    return lastResult.value?.sessionId === sessionId ? lastResult.value.result : null
  }

  return { setResult, takeResult }
})
