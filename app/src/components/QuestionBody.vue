<template>
  <view class="qb">
    <view class="qb-head">
      <text class="tag">{{ typeLabel }}</text>
      <text v-if="indexText" class="qb-index">{{ indexText }}</text>
      <text v-if="question.categoryPath" class="qb-path">{{ question.categoryPath }}</text>
    </view>

    <rich-text class="rich qb-stem" :nodes="question.stemHtml || ''" />
    <image v-if="imageSrc" class="qb-image" :src="imageSrc" mode="widthFix" @tap="previewImage" />

    <!-- 选择题、判断题 -->
    <view v-if="question.type !== 'FILL'" class="qb-options">
      <view
        v-for="opt in question.options"
        :key="opt.key"
        class="qb-option"
        :class="optionClass(opt.key)"
        @tap="choose(opt.key)"
      >
        <view class="qb-key">{{ question.type === 'JUDGE' ? (opt.key === 'T' ? '对' : '错') : opt.key }}</view>
        <rich-text class="rich qb-opt-text" :nodes="opt.html || escapeText(opt.text)" />
        <text v-if="optionMark(opt.key)" class="qb-mark">{{ optionMark(opt.key) }}</text>
      </view>
      <view v-if="question.type === 'MULTIPLE' && !locked" class="muted qb-hint">多选题：选出全部正确选项后提交</view>
    </view>

    <!-- 填空题：每空一个输入框，看代码写输出可能有多行 -->
    <view v-else class="qb-blanks">
      <view v-for="(_, i) in blankCount" :key="i" class="qb-blank">
        <text class="qb-blank-label">第 {{ i + 1 }} 空</text>
        <textarea
          class="qb-input"
          :value="modelValue[i] ?? ''"
          :disabled="locked"
          auto-height
          maxlength="500"
          placeholder="在这里输入答案"
          @input="(e: any) => inputBlank(i, e.detail.value)"
        />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { QuestionOption, QuestionType } from '@/api/types'
import { TYPE_LABEL, setBlank, toggleChoice } from '@/logic/answer'
import { resolveAssetUrl } from '@/logic/http'
import { API_BASE } from '@/api/http'

interface ShownQuestion {
  type: QuestionType
  stemHtml: string
  options: QuestionOption[]
  blankCount: number
  imageUrl?: string | null
  categoryPath?: string
}

const props = withDefaults(
  defineProps<{
    question: ShownQuestion
    modelValue: string[]
    /** 已提交、背题、只看解析时为 true：不能再改 */
    locked?: boolean
    /** 有值时在选项上标出正确答案（提交后 / 背题） */
    correctAnswer?: string[] | null
    indexText?: string
  }>(),
  { locked: false, correctAnswer: null, indexText: '' },
)

const emit = defineEmits<{ (e: 'update:modelValue', value: string[]): void }>()

const typeLabel = computed(() => TYPE_LABEL[props.question.type])
const blankCount = computed(() => Math.max(props.question.blankCount || 0, 1))
const imageSrc = computed(() => resolveAssetUrl(API_BASE, props.question.imageUrl))

function choose(key: string) {
  if (props.locked) return
  emit('update:modelValue', toggleChoice(props.question.type, props.modelValue, key, props.question.options))
}

function inputBlank(index: number, value: string) {
  emit('update:modelValue', setBlank(props.modelValue, index, value, blankCount.value))
}

function optionClass(key: string): string {
  const chosen = props.modelValue.includes(key)
  if (props.correctAnswer) {
    if (props.correctAnswer.includes(key)) return 'is-correct'
    if (chosen) return 'is-wrong'
    return ''
  }
  return chosen ? 'is-selected' : ''
}

/** 颜色之外再给文字标记 */
function optionMark(key: string): string {
  if (!props.correctAnswer) return ''
  const chosen = props.modelValue.includes(key)
  if (props.correctAnswer.includes(key)) return chosen ? '✓ 你选的' : '✓ 正确答案'
  return chosen ? '✗ 你选的' : ''
}

/** 选项没给 html 时（如判断题的「正确 / 错误」）按纯文本显示 */
function escapeText(text: string): string {
  return (text || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function previewImage() {
  if (imageSrc.value) uni.previewImage({ urls: [imageSrc.value] })
}
</script>

<style lang="scss" scoped>
.qb {
  padding: 4px 0;
}

.qb-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.qb-index {
  font-weight: 600;
  color: var(--c-text-2);
  font-size: var(--fs-sm);
}

.qb-path {
  color: var(--c-text-3);
  font-size: var(--fs-xs);
}

.qb-stem {
  font-size: var(--fs-base);
  line-height: 1.7;
}

.qb-image {
  width: 100%;
  margin-top: 8px;
  border-radius: 8px;
}

.qb-options {
  margin-top: 14px;
}

.qb-option {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  margin-bottom: 10px;
  border: 1px solid var(--c-border);
  border-radius: 10px;
  background: var(--c-card);
  position: relative;
}

.qb-key {
  flex: none;
  width: 26px;
  height: 26px;
  line-height: 26px;
  text-align: center;
  border-radius: 13px;
  border: 1px solid var(--c-border);
  font-size: var(--fs-sm);
  color: var(--c-text-2);
}

.qb-opt-text {
  flex: 1;
  min-width: 0;
  font-size: var(--fs-base);
}

.qb-mark {
  flex: none;
  font-size: var(--fs-xs);
  align-self: center;
}

.is-selected {
  border-color: var(--c-primary);
  background: var(--c-primary-weak);

  .qb-key {
    background: var(--c-primary);
    border-color: var(--c-primary);
    color: #fff;
  }
}

.is-correct {
  border-color: var(--c-correct);
  background: var(--c-correct-weak);

  .qb-key {
    background: var(--c-correct);
    border-color: var(--c-correct);
    color: #fff;
  }

  .qb-mark {
    color: var(--c-correct);
  }
}

.is-wrong {
  border-color: var(--c-wrong);
  background: var(--c-wrong-weak);

  .qb-key {
    background: var(--c-wrong);
    border-color: var(--c-wrong);
    color: #fff;
  }

  .qb-mark {
    color: var(--c-wrong);
  }
}

.qb-hint {
  margin-top: -2px;
}

.qb-blanks {
  margin-top: 14px;
}

.qb-blank {
  margin-bottom: 12px;
}

.qb-blank-label {
  font-size: var(--fs-sm);
  color: var(--c-text-2);
}

.qb-input {
  width: 100%;
  box-sizing: border-box;
  min-height: 44px;
  margin-top: 4px;
  padding: 10px 12px;
  border: 1px solid var(--c-border);
  border-radius: 10px;
  background: var(--c-card);
  color: var(--c-text);
  font-size: var(--fs-base);
  font-family: Menlo, Consolas, 'Courier New', monospace;
}
</style>
