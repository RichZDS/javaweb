<template>
  <view class="module-error">
    <text class="me-title">{{ title }}</text>
    <text class="me-reason">{{ reason }}</text>
    <button class="btn-plain me-retry" size="mini" @tap="emit('retry')">重试</button>
  </view>
</template>

<script setup lang="ts">
/** 某个模块加载失败：只说当前考试的这一块没加载出来，给原因和重试 */
import { computed } from 'vue'
import { describeError, toApiError } from '@/logic/http'

const props = defineProps<{ title: string; error: unknown }>()
const emit = defineEmits<{ (e: 'retry'): void }>()
const reason = computed(() => describeError(toApiError(props.error)))
</script>

<style lang="scss" scoped>
.module-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 0;
  gap: 4px;
}

.me-title {
  color: var(--c-text-2);
  font-size: var(--fs-sm);
}

.me-reason {
  color: var(--c-wrong);
  font-size: var(--fs-xs);
}

.me-retry {
  margin-top: 6px;
}
</style>
