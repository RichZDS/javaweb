<template>
  <div class="page-card">
    <div class="toolbar">
      <el-select v-model="query.categoryId" placeholder="考试" clearable style="width: 200px" @change="search(1)">
        <el-option v-for="c in exams" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 110px" @change="search(1)">
        <el-option label="已上架" value="ONLINE" />
        <el-option label="已下架" value="OFFLINE" />
      </el-select>
      <el-button type="primary" @click="search(1)">查询</el-button>
      <span class="spacer" />
      <el-button type="primary" plain @click="router.push('/papers/new')">新建试卷</el-button>
    </div>

    <LoadError v-if="error && !rows.length" :error="error" title="试卷没加载出来" @retry="search(query.page)" />
    <el-table v-else v-loading="loading" :data="rows" empty-text="还没有试卷">
      <el-table-column prop="title" label="试卷" min-width="220" />
      <el-table-column prop="categoryName" label="考试" width="160" />
      <el-table-column label="组卷" width="110">
        <template #default="{ row }">
          {{ row.mode === 'RULE' ? '规则卷' : '固定卷' }}
          <el-tag v-if="row.shuffleOptions" size="small" type="info">选项随机</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="questionCount" label="题量" width="70" />
      <el-table-column label="总分 / 及格" width="110">
        <template #default="{ row }">{{ row.totalScore }} / {{ row.passScore }}</template>
      </el-table-column>
      <el-table-column label="时长" width="80">
        <template #default="{ row }">{{ row.durationMin }} 分钟</template>
      </el-table-column>
      <el-table-column prop="attempts" label="考过" width="70" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ONLINE' ? 'success' : 'info'" size="small">{{ row.status === 'ONLINE' ? '已上架' : '已下架' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/papers/${row.id}`)">编辑</el-button>
          <el-button link :type="row.status === 'ONLINE' ? 'warning' : 'success'" :loading="switching === row.id" @click="toggle(row)">
            {{ row.status === 'ONLINE' ? '下架' : '上架' }}
          </el-button>
          <el-button link type="danger" :disabled="row.attempts > 0" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination v-model:current-page="query.page" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="search" />
    </div>

    <GapsDialog v-model="gaps.open" :title="gaps.title" :gaps="gaps.list" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { categoryApi, paperApi } from '@/api'
import type { AdminCategory, AdminPaper, PaperStatus, PoolGap } from '@/api/types'
import LoadError from '@/components/LoadError.vue'
import GapsDialog from '@/components/GapsDialog.vue'
import { ApiError } from '@/logic/http'

const router = useRouter()
const tree = ref<AdminCategory[]>([])
const exams = computed(() => tree.value.filter((c) => c.level === 1))
const query = reactive({ categoryId: null as number | null, status: '' as PaperStatus | '', page: 1, size: 20 })
const rows = ref<AdminPaper[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref<unknown>(null)
const switching = ref<number | null>(null)
const gaps = reactive({ open: false, title: '', list: [] as PoolGap[] })

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
    const res = await paperApi.list(query)
    rows.value = res.items
    total.value = res.total
  } catch (e) {
    error.value = e
  } finally {
    loading.value = false
  }
}

/** 上下架；规则卷题不够时展示每条规则的缺口 */
async function toggle(row: AdminPaper) {
  const next: PaperStatus = row.status === 'ONLINE' ? 'OFFLINE' : 'ONLINE'
  switching.value = row.id
  try {
    const saved = await paperApi.setStatus(row.id, next)
    Object.assign(row, saved)
    ElMessage.success(next === 'ONLINE' ? '已上架' : '已下架')
  } catch (e) {
    if (e instanceof ApiError && e.code === 'POOL_INSUFFICIENT') {
      Object.assign(gaps, { open: true, title: `「${row.title}」题不够，暂时不能上架`, list: (e.data as { gaps?: PoolGap[] } | null)?.gaps ?? [] })
    }
  } finally {
    switching.value = null
  }
}

async function remove(row: AdminPaper) {
  try {
    await ElMessageBox.confirm(`删除「${row.title}」？只有没人考过的试卷能删除。`, '删除试卷', { type: 'warning', confirmButtonText: '删除' })
  } catch {
    return
  }
  try {
    await paperApi.remove(row.id)
    ElMessage.success('已删除')
    await search(query.page)
  } catch {
    /* 请求层已提示 */
  }
}
</script>
