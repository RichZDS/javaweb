<template>
  <view class="ar">
    <view v-if="unanswered" class="ar-verdict none">— 未作答</view>
    <view v-else-if="!recite" class="ar-verdict" :class="correct ? 'ok' : 'bad'">
      {{ correct ? '✓ 回答正确' : '✗ 回答错误' }}
    </view>
    <view class="ar-row">
      <text class="ar-label">正确答案</text>
      <text class="ar-value ok-text">{{ correctText || '—' }}</text>
    </view>
    <view v-if="!recite" class="ar-row">
      <text class="ar-label">{{ userLabel }}</text>
      <text class="ar-value" :class="unanswered ? 'none-text' : correct ? 'ok-text' : 'bad-text'">{{ userText }}</text>
    </view>
    <view class="ar-analysis">
      <view class="ar-label">解析</view>
      <rich-text v-if="analysisHtml" class="rich" :nodes="analysisHtml" />
      <view v-else class="muted">暂无解析</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { QuestionType } from '@/api/types'
import { formatAnswer, formatCorrect } from '@/logic/answer'

const props = withDefaults(
  defineProps<{
    type: QuestionType
    correct?: boolean | null
    userAnswer?: string[] | null
    correctAnswer?: string[] | null
    acceptedAnswers?: string[][] | null
    analysisHtml?: string | null
    /** 背题模式：只显示答案和解析 */
    recite?: boolean
    userLabel?: string
    /** 没有作答（考试里未答的题）：显示「未作答」，不算答错 */
    unanswered?: boolean
  }>(),
  {
    correct: null,
    userAnswer: null,
    correctAnswer: null,
    acceptedAnswers: null,
    analysisHtml: null,
    recite: false,
    userLabel: '你的答案',
    unanswered: false,
  },
)

const correctText = computed(() => formatCorrect(props.type, props.correctAnswer, props.acceptedAnswers))
const userText = computed(() => formatAnswer(props.type, props.userAnswer))
</script>

<style lang="scss" scoped>
.ar {
  margin-top: 16px;
  padding: 14px;
  border-radius: 10px;
  background: var(--c-card);
  border: 1px solid var(--c-border);
}

.ar-verdict {
  font-weight: 600;
  font-size: var(--fs-lg);
  margin-bottom: 8px;

  &.ok {
    color: var(--c-correct);
  }

  &.bad {
    color: var(--c-wrong);
  }

  &.none {
    color: var(--c-text-3);
  }
}

.ar-row {
  display: flex;
  gap: 10px;
  margin-bottom: 6px;
}

.ar-label {
  flex: none;
  color: var(--c-text-3);
  font-size: var(--fs-sm);
  width: 64px;
}

.ar-value {
  flex: 1;
  min-width: 0;
  word-break: break-all;
  white-space: pre-wrap;
}

.ok-text {
  color: var(--c-correct);
}

.bad-text {
  color: var(--c-wrong);
}

.none-text {
  color: var(--c-text-3);
}

.ar-analysis {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--c-border);

  .ar-label {
    margin-bottom: 4px;
  }
}
</style>
