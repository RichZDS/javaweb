<template>
  <div class="md-field">
    <el-input
      :model-value="modelValue"
      type="textarea"
      :autosize="{ minRows: rows, maxRows: 20 }"
      :placeholder="placeholder"
      class="mono"
      @update:model-value="(v: string) => emit('update:modelValue', v)"
    />
    <div class="md-bar">
      <span class="muted">支持 Markdown，代码用 ``` 包起来并写语言（java / c / python / sql）</span>
      <el-button size="small" :loading="loading" @click="preview">预览</el-button>
    </div>
    <div v-if="html !== null" class="md-preview" v-html="html" />
    <div v-if="error" class="md-error">预览失败：{{ error }}</div>
  </div>
</template>

<script setup lang="ts">
/**
 * Markdown 输入 + 预览。预览走后端 /api/admin/questions/preview，
 * 返回的 HTML 由后端按白名单过滤过（和学员端看到的一致），这里直接显示。
 */
import { ref, watch } from 'vue'
import { questionApi } from '@/api'
import { describeError } from '@/logic/http'

const props = withDefaults(defineProps<{ modelValue: string; rows?: number; placeholder?: string }>(), { rows: 4, placeholder: '' })
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()

const html = ref<string | null>(null)
const loading = ref(false)
const error = ref('')

// 内容改了以后旧预览就不准了，收起来
watch(
  () => props.modelValue,
  () => {
    html.value = null
  },
)

async function preview() {
  loading.value = true
  error.value = ''
  try {
    html.value = (await questionApi.preview(props.modelValue || '')).html
  } catch (e) {
    error.value = describeError(e)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.md-field {
  width: 100%;
}

.mono :deep(textarea) {
  font-family: Menlo, Consolas, 'Courier New', monospace;
}

.md-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 6px 0;
}

.md-error {
  color: #d03050;
  font-size: 13px;
}
</style>
