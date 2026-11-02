<template>
  <div class="page-card">
    <div class="toolbar">
      <el-button type="primary" @click="openCreate(null)">新建考试（一级）</el-button>
      <span class="muted">三级结构：考试 → 科目 → 章节，题目只挂在章节上；同级按「排序」从小到大</span>
      <span class="spacer" />
      <el-button @click="load">刷新</el-button>
    </div>

    <LoadError v-if="error && !tree.length" :error="error" title="分类没加载出来" @retry="load" />
    <el-table
      v-else
      v-loading="loading"
      :data="tree"
      row-key="id"
      default-expand-all
      :tree-props="{ children: 'children' }"
      empty-text="还没有分类"
    >
      <el-table-column prop="name" label="名称" min-width="260" />
      <el-table-column label="层级" width="90">
        <template #default="{ row }">{{ LEVEL_LABEL[row.level as 1 | 2 | 3] }}</template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column prop="questionCount" label="题量" width="90" />
      <el-table-column label="操作" width="260">
        <template #default="{ row }">
          <el-button v-if="row.level < 3" link type="primary" @click="openCreate(row)">加{{ LEVEL_LABEL[(row.level + 1) as 2 | 3] }}</el-button>
          <el-button link type="primary" @click="openEdit(row)">修改</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog.open" :title="dialog.title" width="420px">
      <el-form label-width="64px" @submit.prevent="save">
        <el-form-item v-if="dialog.parentName" label="上级">
          <span>{{ dialog.parentName }}</span>
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model.trim="dialog.name" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dialog.sort" :min="0" :max="9999" :step="1" step-strictly />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.open = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { categoryApi } from '@/api'
import type { AdminCategory } from '@/api/types'
import LoadError from '@/components/LoadError.vue'

const LEVEL_LABEL = { 1: '考试', 2: '科目', 3: '章节' } as const

const tree = ref<AdminCategory[]>([])
const loading = ref(false)
const error = ref<unknown>(null)
const dialog = reactive({
  open: false,
  title: '',
  mode: 'create' as 'create' | 'edit',
  id: 0,
  parentId: null as number | null,
  parentName: '',
  name: '',
  sort: 0,
  saving: false,
})

onMounted(load)

async function load() {
  loading.value = true
  error.value = null
  try {
    tree.value = await categoryApi.tree()
  } catch (e) {
    error.value = e
  } finally {
    loading.value = false
  }
}

function openCreate(parent: AdminCategory | null) {
  Object.assign(dialog, {
    open: true,
    mode: 'create',
    title: parent ? `在「${parent.name}」下新建${LEVEL_LABEL[(parent.level + 1) as 2 | 3]}` : '新建考试',
    parentId: parent?.id ?? null,
    parentName: parent?.name ?? '',
    name: '',
    sort: (parent?.children?.length ?? tree.value.length) * 10,
  })
}

function openEdit(node: AdminCategory) {
  Object.assign(dialog, { open: true, mode: 'edit', title: `修改「${node.name}」`, id: node.id, parentName: '', name: node.name, sort: node.sort })
}

async function save() {
  if (!dialog.name) {
    ElMessage.warning('请填写名称')
    return
  }
  dialog.saving = true
  try {
    const sort = Number(dialog.sort) || 0
    if (dialog.mode === 'create') await categoryApi.create({ parentId: dialog.parentId, name: dialog.name, sort })
    else await categoryApi.update(dialog.id, { name: dialog.name, sort })
    ElMessage.success('已保存')
    dialog.open = false
    await load()
  } catch {
    /* 请求层已提示 */
  } finally {
    dialog.saving = false
  }
}

async function remove(node: AdminCategory) {
  try {
    await ElMessageBox.confirm(`删除「${node.name}」？有子分类或有题目时不能删除。`, '删除分类', { type: 'warning', confirmButtonText: '删除' })
  } catch {
    return
  }
  try {
    await categoryApi.remove(node.id)
    ElMessage.success('已删除')
    await load()
  } catch {
    /* 409 CONFLICT 等由请求层按后端说明提示 */
  }
}
</script>
