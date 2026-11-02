<template>
  <el-dialog :model-value="modelValue" title="挑选题目" width="880px" @update:model-value="(v: boolean) => emit('update:modelValue', v)">
    <div class="toolbar">
      <el-cascader
        v-model="query.categoryId"
        :options="options"
        :props="{ checkStrictly: true, emitPath: false }"
        placeholder="分类（可选到任意一级）"
        clearable
        style="width: 260px"
      />
      <el-select v-model="query.type" placeholder="题型" clearable style="width: 110px">
        <el-option v-for="(label, key) in TYPE_LABEL" :key="key" :label="label" :value="key" />
      </el-select>
      <el-input v-model="query.keyword" placeholder="题干关键词" clearable style="width: 200px" @keyup.enter="search(1)" />
      <el-button type="primary" @click="search(1)">查询</el-button>
      <span class="muted">只列出已发布的题</span>
    </div>
    <el-table v-loading="loading" :data="rows" height="380" @selection-change="(s: AdminQuestionRow[]) => (selected = s)">
      <el-table-column type="selection" width="44" :selectable="(r: AdminQuestionRow) => !exclude.includes(r.id)" />
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="题型" width="70">
        <template #default="{ row }">{{ TYPE_LABEL[row.type as QuestionType] }}</template>
      </el-table-column>
      <el-table-column label="题干">
        <template #default="{ row }">
          <span class="ellipsis">{{ row.stemPreview }}</span>
          <el-tag v-if="exclude.includes(row.id)" size="small" type="info">已在卷中</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="categoryPath" label="分类" width="240" show-overflow-tooltip />
    </el-table>
    <div class="pager">
      <el-pagination v-model:current-page="query.page" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="search" />
    </div>
    <div v-if="error" class="muted">题目没加载出来：{{ error }}</div>
    <template #footer>
      <span class="muted">已选 {{ selected.length }} 题</span>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :disabled="!selected.length" @click="confirm">加入试卷</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
/** 固定卷挑题：在本试卷的考试范围内按分类、题型、关键词找已发布的题，多选后加入 */
import { computed, reactive, ref, watch } from 'vue'
import { questionApi } from '@/api'
import type { AdminCategory, AdminQuestionRow, QuestionType } from '@/api/types'
import { TYPE_LABEL } from '@/logic/question'
import { toCascader } from '@/logic/tree'
import { describeError } from '@/logic/http'

const props = defineProps<{ modelValue: boolean; tree: AdminCategory[]; examId: number | null; exclude: number[] }>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'pick', rows: AdminQuestionRow[]): void
}>()

const query = reactive({ categoryId: null as number | null, type: '' as QuestionType | '', keyword: '', page: 1, size: 20 })
const rows = ref<AdminQuestionRow[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const selected = ref<AdminQuestionRow[]>([])
const options = computed(() => toCascader(props.tree, { rootId: props.examId }))

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      query.categoryId = props.examId
      selected.value = []
      search(1)
    }
  },
)

async function search(page: number) {
  query.page = page
  loading.value = true
  error.value = ''
  try {
    const res = await questionApi.list({
      categoryId: query.categoryId ?? props.examId,
      type: query.type,
      status: 'PUBLISHED',
      keyword: query.keyword.trim(),
      page: query.page,
      size: query.size,
    })
    rows.value = res.items
    total.value = res.total
  } catch (e) {
    error.value = describeError(e)
  } finally {
    loading.value = false
  }
}

function confirm() {
  emit('pick', selected.value)
  emit('update:modelValue', false)
}
</script>
