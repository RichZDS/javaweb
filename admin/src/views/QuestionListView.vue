<template>
  <div class="page-card">
    <div class="toolbar">
      <el-cascader
        v-model="query.categoryId"
        :options="cascader"
        :props="{ checkStrictly: true, emitPath: false }"
        placeholder="分类"
        clearable
        style="width: 260px"
        @change="search(1)"
      />
      <el-select v-model="query.type" placeholder="题型" clearable style="width: 110px" @change="search(1)">
        <el-option v-for="(label, key) in TYPE_LABEL" :key="key" :label="label" :value="key" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 110px" @change="search(1)">
        <el-option v-for="(label, key) in STATUS_LABEL" :key="key" :label="label" :value="key" />
      </el-select>
      <el-input v-model="query.keyword" placeholder="题干关键词" clearable style="width: 200px" @keyup.enter="search(1)" @clear="search(1)" />
      <el-button type="primary" @click="search(1)">查询</el-button>
      <span class="spacer" />
      <el-button type="primary" plain @click="router.push('/questions/new')">新建题目</el-button>
    </div>

    <LoadError v-if="error && !rows.length" :error="error" title="题目没加载出来" @retry="search(query.page)" />
    <el-table v-else v-loading="loading" :data="rows" empty-text="没有符合条件的题目">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="题干" min-width="280">
        <template #default="{ row }">
          <div class="ellipsis">{{ row.stemPreview }}</div>
          <div class="muted ellipsis">{{ row.categoryPath }}</div>
        </template>
      </el-table-column>
      <el-table-column label="题型" width="70">
        <template #default="{ row }">{{ TYPE_LABEL[row.type as QuestionType] }}</template>
      </el-table-column>
      <el-table-column prop="difficulty" label="难度" width="64" />
      <el-table-column label="状态" width="86">
        <template #default="{ row }">
          <el-tag :type="STATUS_TAG[row.status as QuestionStatus]" size="small">{{ STATUS_LABEL[row.status as QuestionStatus] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="64" />
      <el-table-column prop="answerCount" label="作答" width="70" />
      <el-table-column label="正确率" width="80">
        <template #default="{ row }">{{ formatRate(row.correctRate) }}</template>
      </el-table-column>
      <el-table-column label="更新时间" width="150">
        <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/questions/${row.id}`)">编辑</el-button>
          <el-button v-if="row.status !== 'ARCHIVED'" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :page-sizes="[20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @current-change="search"
        @size-change="search(1)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { categoryApi, questionApi } from '@/api'
import type { AdminCategory, AdminQuestionRow, QuestionStatus, QuestionType } from '@/api/types'
import LoadError from '@/components/LoadError.vue'
import { formatRate, formatTime } from '@/logic/http'
import { TYPE_LABEL } from '@/logic/question'
import { toCascader } from '@/logic/tree'

const STATUS_LABEL: Record<QuestionStatus, string> = { DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' }
const STATUS_TAG: Record<QuestionStatus, 'info' | 'success' | 'warning'> = { DRAFT: 'info', PUBLISHED: 'success', ARCHIVED: 'warning' }

const router = useRouter()
const tree = ref<AdminCategory[]>([])
const cascader = computed(() => toCascader(tree.value))
const query = reactive({
  categoryId: null as number | null,
  type: '' as QuestionType | '',
  status: '' as QuestionStatus | '',
  keyword: '',
  page: 1,
  size: 20,
})
const rows = ref<AdminQuestionRow[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref<unknown>(null)

onMounted(() => {
  categoryApi
    .tree()
    .then((t) => (tree.value = t))
    .catch(() => {})
  search(1)
})

async function search(page: number) {
  query.page = page
  loading.value = true
  error.value = null
  try {
    const res = await questionApi.list({ ...query, keyword: query.keyword.trim() })
    rows.value = res.items
    total.value = res.total
  } catch (e) {
    error.value = e
  } finally {
    loading.value = false
  }
}

async function remove(row: AdminQuestionRow) {
  try {
    await ElMessageBox.confirm('没人做过的题会被删除；已经有人做过的题会改为「已归档」，不再出现在学员端。', `删除第 ${row.id} 题`, {
      type: 'warning',
      confirmButtonText: '删除',
    })
  } catch {
    return
  }
  try {
    const res = await questionApi.remove(row.id)
    ElMessage.success(res.deleted ? '已删除' : '已有作答记录，已改为归档')
    await search(query.page)
  } catch {
    /* 请求层已提示 */
  }
}
</script>
