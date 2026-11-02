<template>
  <div class="page-card">
    <div class="toolbar">
      <el-radio-group v-model="query.status" @change="search(1)">
        <el-radio-button value="PENDING">待处理</el-radio-button>
        <el-radio-button value="RESOLVED">已采纳</el-radio-button>
        <el-radio-button value="REJECTED">已驳回</el-radio-button>
        <el-radio-button value="">全部</el-radio-button>
      </el-radio-group>
      <span class="spacer" />
      <el-button @click="search(query.page)">刷新</el-button>
    </div>

    <LoadError v-if="error && !rows.length" :error="error" title="纠错没加载出来" @retry="search(query.page)" />
    <el-table v-else v-loading="loading" :data="rows" empty-text="没有纠错反馈">
      <el-table-column label="题目" min-width="240">
        <template #default="{ row }">
          <div class="ellipsis">#{{ row.questionId }} {{ row.stemPreview }}</div>
          <div class="muted ellipsis">{{ row.categoryPath }}</div>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="110">
        <template #default="{ row }">{{ TYPE[row.type as FeedbackType] }}</template>
      </el-table-column>
      <el-table-column prop="content" label="内容" min-width="220" show-overflow-tooltip />
      <el-table-column prop="username" label="提交人" width="110" />
      <el-table-column label="提交时间" width="150">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="150">
        <template #default="{ row }">
          <el-tag :type="STATUS_TAG[row.status as FeedbackStatus]" size="small">{{ STATUS[row.status as FeedbackStatus] }}</el-tag>
          <div v-if="row.reply" class="muted ellipsis" :title="row.reply">回复：{{ row.reply }}</div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/questions/${row.questionId}`)">改题</el-button>
          <el-button v-if="row.status === 'PENDING'" link type="primary" @click="open(row)">处理</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination v-model:current-page="query.page" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="search" />
    </div>

    <el-dialog v-model="dialog.open" title="处理纠错" width="480px">
      <p class="content">{{ dialog.row?.content }}</p>
      <el-form label-width="64px">
        <el-form-item label="结论">
          <el-radio-group v-model="dialog.status">
            <el-radio value="RESOLVED">采纳（已改题）</el-radio>
            <el-radio value="REJECTED">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="回复">
          <el-input v-model="dialog.reply" type="textarea" :autosize="{ minRows: 2, maxRows: 5 }" maxlength="500" show-word-limit placeholder="可不填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.open = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { feedbackApi } from '@/api'
import type { FeedbackRow, FeedbackStatus, FeedbackType } from '@/api/types'
import LoadError from '@/components/LoadError.vue'
import { formatTime } from '@/logic/http'

const TYPE: Record<FeedbackType, string> = { ANSWER_WRONG: '答案有误', STEM_WRONG: '题干有误', ANALYSIS_UNCLEAR: '解析看不懂', OTHER: '其他' }
const STATUS: Record<FeedbackStatus, string> = { PENDING: '待处理', RESOLVED: '已采纳', REJECTED: '已驳回' }
const STATUS_TAG: Record<FeedbackStatus, 'warning' | 'success' | 'info'> = { PENDING: 'warning', RESOLVED: 'success', REJECTED: 'info' }

const router = useRouter()
const query = reactive({ status: 'PENDING' as FeedbackStatus | '', page: 1, size: 20 })
const rows = ref<FeedbackRow[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref<unknown>(null)
const dialog = reactive({ open: false, row: null as FeedbackRow | null, status: 'RESOLVED' as 'RESOLVED' | 'REJECTED', reply: '', saving: false })

onMounted(() => search(1))

async function search(page: number) {
  query.page = page
  loading.value = true
  error.value = null
  try {
    const res = await feedbackApi.list(query)
    rows.value = res.items
    total.value = res.total
  } catch (e) {
    error.value = e
  } finally {
    loading.value = false
  }
}

function open(row: FeedbackRow) {
  Object.assign(dialog, { open: true, row, status: 'RESOLVED', reply: '' })
}

async function save() {
  if (!dialog.row) return
  dialog.saving = true
  try {
    const reply = dialog.reply.trim()
    await feedbackApi.handle(dialog.row.id, { status: dialog.status, ...(reply ? { reply } : {}) })
    ElMessage.success('已处理')
    dialog.open = false
    await search(query.page)
  } catch {
    /* 请求层已提示 */
  } finally {
    dialog.saving = false
  }
}
</script>

<style scoped>
.content {
  background: #f4f6fb;
  padding: 10px 12px;
  border-radius: 6px;
  white-space: pre-wrap;
}
</style>
