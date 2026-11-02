<template>
  <view class="ac">
    <view class="ac-head">
      <text class="ac-title">答题卡</text>
      <view class="ac-legend">
        <text v-for="s in legend" :key="s" class="ac-legend-item" :class="`st-${s}`">{{ label(s) }}</text>
        <text v-if="marked?.some(Boolean)" class="ac-legend-item st-flag">再看</text>
      </view>
    </view>
    <scroll-view scroll-y class="ac-scroll">
      <view class="ac-grid">
        <view
          v-for="(status, i) in statuses"
          :key="i"
          class="ac-cell"
          :class="[`st-${status}`, { current: i === current }]"
          @tap="emit('jump', i)"
        >
          <text class="ac-no">{{ i + 1 }}</text>
          <text class="ac-word">{{ label(status) }}</text>
          <text v-if="marked?.[i]" class="ac-flag">再看</text>
        </view>
      </view>
    </scroll-view>
    <slot />
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CARD_LABEL, type CardStatus } from '@/logic/answer'

const props = defineProps<{
  statuses: CardStatus[]
  current: number
  /** 覆盖默认文字，如背题模式的「已看 / 未看」 */
  labels?: Partial<Record<CardStatus, string>>
  /** 考试里标了「稍后再看」的题 */
  marked?: boolean[]
}>()
const emit = defineEmits<{ (e: 'jump', index: number): void }>()

function label(s: CardStatus): string {
  return props.labels?.[s] ?? CARD_LABEL[s]
}

/** 只列出本卡里出现过的状态 */
const legend = computed(() => {
  const order: CardStatus[] = ['correct', 'wrong', 'answered', 'unanswered']
  return order.filter((s) => props.statuses.includes(s))
})
</script>

<style lang="scss" scoped>
.ac {
  background: var(--c-card);
  border-radius: 16px 16px 0 0;
  padding: 16px;
  color: var(--c-text);
}

.ac-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.ac-title {
  font-weight: 600;
  font-size: var(--fs-lg);
}

.ac-legend {
  display: flex;
  gap: 6px;
}

.ac-legend-item {
  font-size: var(--fs-xs);
  padding: 0 8px;
  border-radius: 10px;
  line-height: 20px;
}

.ac-scroll {
  max-height: 50vh;
}

.ac-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 10px;
}

.ac-cell {
  position: relative;
  height: 48px;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 2px solid transparent;
  line-height: 1.2;

  &.current {
    border-color: var(--c-primary);
  }
}

.ac-no {
  font-weight: 600;
  font-size: var(--fs-sm);
}

.ac-word {
  font-size: 10px;
}

.ac-flag {
  position: absolute;
  top: -6px;
  right: -6px;
  padding: 0 4px;
  border-radius: 6px;
  background: var(--c-warn);
  color: #fff;
  font-size: 9px;
  line-height: 14px;
}

.st-flag {
  background: var(--c-warn-weak);
  color: var(--c-warn);
}

.st-correct {
  background: var(--c-correct-weak);
  color: var(--c-correct);
}

.st-wrong {
  background: var(--c-wrong-weak);
  color: var(--c-wrong);
}

.st-answered {
  background: var(--c-primary-weak);
  color: var(--c-primary);
}

.st-unanswered {
  background: var(--c-bg);
  color: var(--c-text-3);
}
</style>
