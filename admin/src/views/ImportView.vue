<template>
  <div>
    <div class="page-card">
      <h3>第一步：下载模板</h3>
      <p class="muted">
        表头：分类路径 | 题型 | 题干 | 选项A–F | 答案 | 解析 | 难度 | 标签 | 来源。分类路径写成「考试/科目/章节」，三级都要已存在；
        答案：单选 A、多选 AC、判断「对 / 错」、填空每空用 || 分隔、同一空的多个答案用 | 分隔（如 int|Integer||5）。
      </p>
      <el-button :loading="downloading" @click="download">下载 Excel 模板</el-button>
    </div>

    <div class="page-card">
      <h3>第二步：上传填好的文件</h3>
      <el-upload drag :show-file-list="false" :http-request="upload" :before-upload="check" accept=".xlsx" :disabled="uploading">
        <div v-if="uploading">正在导入…</div>
        <div v-else>把 .xlsx 文件拖到这里，或<em>点击选择</em><div class="muted">不超过 5 MB；整表先校验，有错的行跳过，正确的行照常导入</div></div>
      </el-upload>
    </div>

    <div v-if="result" class="page-card">
      <h3>导入结果<span v-if="fileName" class="muted">（{{ fileName }}）</span></h3>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="总行数">{{ result.total }}</el-descriptions-item>
        <el-descriptions-item label="成功">{{ result.success }}</el-descriptions-item>
        <el-descriptions-item label="失败">{{ result.failed.length }}</el-descriptions-item>
      </el-descriptions>
      <el-table v-if="result.failed.length" :data="result.failed" class="failed" max-height="400">
        <el-table-column prop="row" label="行号" width="90" />
        <el-table-column prop="reason" label="原因" />
      </el-table>
      <el-alert v-else type="success" :closable="false" title="全部导入成功" show-icon class="failed" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, type UploadRawFile, type UploadRequestOptions } from 'element-plus'
import { questionApi } from '@/api'
import type { ImportResult } from '@/api/types'

const MAX_BYTES = 5 * 1024 * 1024
const downloading = ref(false)
const uploading = ref(false)
const result = ref<ImportResult | null>(null)
const fileName = ref('')

async function download() {
  downloading.value = true
  try {
    // 模板接口要带令牌，所以不能直接用链接下载
    const blob = await questionApi.template()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '题目导入模板.xlsx'
    a.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch {
    /* 请求层已提示 */
  } finally {
    downloading.value = false
  }
}

function check(file: UploadRawFile): boolean {
  if (!file.name.toLowerCase().endsWith('.xlsx')) {
    ElMessage.error('只支持 .xlsx 文件')
    return false
  }
  if (file.size > MAX_BYTES) {
    ElMessage.error('文件不能超过 5 MB')
    return false
  }
  return true
}

async function upload(options: UploadRequestOptions) {
  uploading.value = true
  fileName.value = options.file.name
  try {
    result.value = await questionApi.import(options.file)
    ElMessage.success(`导入完成：成功 ${result.value.success} 行，失败 ${result.value.failed.length} 行`)
  } catch {
    /* 请求层已提示 */
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
h3 {
  margin: 0 0 8px;
  font-size: 15px;
}

.failed {
  margin-top: 12px;
}
</style>
