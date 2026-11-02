<template>
  <el-dialog :model-value="modelValue" :title="title" width="640px" @update:model-value="(v: boolean) => emit('update:modelValue', v)">
    <p class="muted">下面这些规则在题库里找不到足够的已发布题目。补题、改规则的题数，或换分类后再上架。</p>
    <el-table :data="gaps">
      <el-table-column prop="categoryPath" label="分类" min-width="220" />
      <el-table-column label="题型" width="80">
        <template #default="{ row }">{{ TYPE_LABEL[row.type as QuestionType] }}</template>
      </el-table-column>
      <el-table-column prop="need" label="需要" width="80" />
      <el-table-column prop="available" label="现有" width="80" />
      <el-table-column label="还差" width="80">
        <template #default="{ row }">{{ Math.max(0, row.need - row.available) }}</template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button type="primary" @click="emit('update:modelValue', false)">知道了</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import type { PoolGap, QuestionType } from '@/api/types'
import { TYPE_LABEL } from '@/logic/question'

defineProps<{ modelValue: boolean; title: string; gaps: PoolGap[] }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()
</script>
