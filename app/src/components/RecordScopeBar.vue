<template>
  <!-- 记录页顶部：当前看的是哪个范围，点另一边切换 -->
  <view class="scope-bar">
    <view v-if="hasTarget" class="seg">
      <view class="seg-item cur" :class="{ on: scope === 'target' }" @tap="emit('change', 'target')">
        <text class="seg-text">当前考试：{{ targetName }}</text>
      </view>
      <view class="seg-item all" :class="{ on: scope === 'all' }" @tap="emit('change', 'all')">全部考试</view>
    </view>
    <view v-else class="muted none">还没有选目标考试，下面是全部考试的记录</view>
  </view>
</template>

<script setup lang="ts">
import type { RecordScope } from '@/logic/records'

defineProps<{
  scope: RecordScope
  hasTarget: boolean
  targetName: string
}>()

const emit = defineEmits<{ (e: 'change', scope: RecordScope): void }>()
</script>

<style lang="scss" scoped>
.scope-bar {
  padding: 12px 12px 0;
}

.seg {
  display: flex;
  border: 1px solid var(--c-border);
  border-radius: 10px;
  overflow: hidden;
  background: var(--c-card);
}

.seg-item {
  padding: 9px 12px;
  font-size: var(--fs-sm);
  color: var(--c-text-2);
  text-align: center;

  &.cur {
    flex: 1;
    min-width: 0;
    border-right: 1px solid var(--c-border);
  }

  &.all {
    flex: none;
    width: 88px;
  }

  &.on {
    background: var(--c-primary-weak);
    color: var(--c-primary);
    font-weight: 600;
  }
}

/* 考试名太长时省略，不把「全部考试」挤出去 */
.seg-text {
  display: block;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.none {
  font-size: var(--fs-sm);
}
</style>
