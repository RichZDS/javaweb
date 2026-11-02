<template>
  <el-result icon="warning" :title="title" :sub-title="message">
    <template #extra>
      <el-button type="primary" @click="emit('retry')">重试</el-button>
    </template>
  </el-result>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { describeError } from '@/logic/http'

const props = withDefaults(defineProps<{ error: unknown; title?: string }>(), { title: '没加载出来' })
const emit = defineEmits<{ (e: 'retry'): void }>()
const message = computed(() => describeError(props.error))
</script>
