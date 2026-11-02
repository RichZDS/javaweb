<template>
  <div class="img-field">
    <el-upload :show-file-list="false" :http-request="upload" :before-upload="check" accept="image/jpeg,image/png,image/webp,image/gif">
      <el-button :loading="uploading">{{ modelValue ? '换一张' : '上传图片' }}</el-button>
    </el-upload>
    <template v-if="modelValue">
      <el-image :src="src" fit="contain" class="thumb" :preview-src-list="[src]" />
      <el-button link type="danger" @click="emit('update:modelValue', null)">移除</el-button>
    </template>
    <span class="muted">jpg / png / webp / gif，不超过 2 MB</span>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, type UploadRawFile, type UploadRequestOptions } from 'element-plus'
import { authApi } from '@/api'
import { API_BASE } from '@/api/http'
import { resolveAssetUrl } from '@/logic/http'

const props = defineProps<{ modelValue: string | null }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: string | null): void }>()

const uploading = ref(false)
const src = computed(() => resolveAssetUrl(API_BASE, props.modelValue))
const TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']

function check(file: UploadRawFile): boolean {
  if (!TYPES.includes(file.type)) {
    ElMessage.error('只能上传 jpg、png、webp、gif 图片')
    return false
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error('图片不能超过 2 MB')
    return false
  }
  return true
}

async function upload(options: UploadRequestOptions) {
  uploading.value = true
  try {
    const { url } = await authApi.uploadImage(options.file)
    emit('update:modelValue', url)
  } catch {
    /* 请求层已提示 */
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.img-field {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.thumb {
  width: 120px;
  height: 80px;
  border: 1px solid #e6e9f0;
  border-radius: 4px;
}
</style>
