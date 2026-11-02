<template>
  <div class="page-card">
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="用户名或昵称" clearable style="width: 240px" @keyup.enter="search(1)" @clear="search(1)" />
      <el-button type="primary" @click="search(1)">查询</el-button>
    </div>

    <LoadError v-if="error && !rows.length" :error="error" title="用户没加载出来" @retry="search(query.page)" />
    <el-table v-else v-loading="loading" :data="rows" empty-text="没有用户">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="用户名" min-width="140" />
      <el-table-column label="昵称" min-width="120">
        <template #default="{ row }">{{ row.nickname || '—' }}</template>
      </el-table-column>
      <el-table-column label="角色" width="90">
        <template #default="{ row }">
          <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'" size="small">{{ row.role === 'ADMIN' ? '管理员' : '学员' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="answerCount" label="作答次数" width="100" />
      <el-table-column label="最近活跃" width="150">
        <template #default="{ row }">{{ formatTime(row.lastActiveAt) }}</template>
      </el-table-column>
      <el-table-column label="注册时间" width="150">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'warning'" size="small">{{ row.status === 'ACTIVE' ? '正常' : '已禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-tooltip v-if="row.id === auth.user?.id" content="不能禁用自己" placement="top">
            <span><el-button link disabled>禁用</el-button></span>
          </el-tooltip>
          <el-button v-else link :type="row.status === 'ACTIVE' ? 'danger' : 'success'" :loading="switching === row.id" @click="toggle(row)">
            {{ row.status === 'ACTIVE' ? '禁用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination v-model:current-page="query.page" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="search" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi } from '@/api'
import type { AdminUser } from '@/api/types'
import LoadError from '@/components/LoadError.vue'
import { useAuthStore } from '@/stores/auth'
import { formatTime } from '@/logic/http'

const auth = useAuthStore()
const query = reactive({ keyword: '', page: 1, size: 20 })
const rows = ref<AdminUser[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref<unknown>(null)
const switching = ref<number | null>(null)

onMounted(() => search(1))

async function search(page: number) {
  query.page = page
  loading.value = true
  error.value = null
  try {
    const res = await userApi.list({ ...query, keyword: query.keyword.trim() })
    rows.value = res.items
    total.value = res.total
  } catch (e) {
    error.value = e
  } finally {
    loading.value = false
  }
}

async function toggle(row: AdminUser) {
  const disabling = row.status === 'ACTIVE'
  if (disabling) {
    try {
      await ElMessageBox.confirm(`禁用后「${row.username}」不能登录，已登录的请求会被拒绝。`, '禁用用户', { type: 'warning', confirmButtonText: '禁用' })
    } catch {
      return
    }
  }
  switching.value = row.id
  try {
    const saved = await userApi.setStatus(row.id, disabling ? 'DISABLED' : 'ACTIVE')
    Object.assign(row, saved)
    ElMessage.success(disabling ? '已禁用' : '已启用')
  } catch {
    /* 请求层已提示 */
  } finally {
    switching.value = null
  }
}
</script>
