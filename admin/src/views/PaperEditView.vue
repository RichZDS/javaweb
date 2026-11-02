<template>
  <div>
    <LoadError v-if="loadError" :error="loadError" title="试卷没加载出来" @retry="load" />
    <div v-else v-loading="loading" class="page-card">
      <el-alert
        v-if="locked"
        type="warning"
        :closable="false"
        show-icon
        class="lock-tip"
        :title="`这份试卷已经有人考过（${original?.attempts} 次），只能改标题、描述和选项打乱；上下架在试卷列表里操作。`"
      />
      <el-form label-width="112px" class="form">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="100" show-word-limit style="width: 480px" />
        </el-form-item>
        <el-form-item label="所属考试" required>
          <el-select v-model="form.categoryId" :disabled="locked" placeholder="选择考试" style="width: 260px" @change="onExamChange">
            <el-option v-for="c in exams" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :autosize="{ minRows: 2, maxRows: 6 }" maxlength="500" show-word-limit style="width: 480px" />
        </el-form-item>
        <el-form-item label="选项打乱">
          <el-switch v-model="form.shuffleOptions" />
          <span class="muted hint">开启后每场考试的单选、多选题选项顺序随机；判断、填空不打乱。只影响之后开考的场次</span>
        </el-form-item>
        <el-form-item label="组卷方式" required>
          <el-radio-group v-model="form.mode" :disabled="locked || !!id">
            <el-radio-button value="FIXED">固定卷</el-radio-button>
            <el-radio-button value="RULE">规则卷</el-radio-button>
          </el-radio-group>
          <span class="muted hint">{{ form.mode === 'FIXED' ? '固定卷：挑好具体的题，每人题目相同' : '规则卷：按分类、题型和题数开考时随机抽题' }}</span>
        </el-form-item>
        <el-form-item label="时长（分钟）" required>
          <el-input-number v-model="form.durationMin" :disabled="locked" :min="1" :max="600" step-strictly />
        </el-form-item>
        <el-form-item label="及格分" required>
          <el-input-number v-model="form.passScore" :disabled="locked" :min="0" :max="10000" step-strictly />
          <span class="muted hint">预计总分 {{ previewTotal(form) }}（保存后以后端计算为准）</span>
        </el-form-item>

        <!-- 固定卷：挑题 -->
        <el-form-item v-if="form.mode === 'FIXED'" label="题目">
          <div class="block">
            <div class="toolbar">
              <el-button :disabled="locked || !form.categoryId" @click="pickerOpen = true">挑题</el-button>
              <span class="muted">默认每题</span>
              <el-input-number v-model="defaultScore" :disabled="locked" :min="1" :max="100" step-strictly size="small" />
              <span class="muted">分；共 {{ form.questions.length }} 题</span>
            </div>
            <el-table :data="form.questions" empty-text="还没挑题" max-height="460">
              <el-table-column label="序号" width="64">
                <template #default="{ $index }">{{ $index + 1 }}</template>
              </el-table-column>
              <el-table-column prop="questionId" label="题目 ID" width="84" />
              <el-table-column label="题型" width="70">
                <template #default="{ row }">{{ row.type ? TYPE_LABEL[row.type as QuestionType] : '—' }}</template>
              </el-table-column>
              <el-table-column label="题干" min-width="240">
                <template #default="{ row }"><span class="ellipsis">{{ row.stemPreview || '—' }}</span></template>
              </el-table-column>
              <el-table-column label="分值" width="140">
                <template #default="{ row }">
                  <el-input-number v-model="row.score" :disabled="locked" :min="1" :max="100" step-strictly size="small" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="150">
                <template #default="{ $index }">
                  <el-button link :disabled="locked || $index === 0" @click="form.questions = moveQuestion(form.questions, $index, -1)">上移</el-button>
                  <el-button link :disabled="locked || $index === form.questions.length - 1" @click="form.questions = moveQuestion(form.questions, $index, 1)">下移</el-button>
                  <el-button link type="danger" :disabled="locked" @click="form.questions.splice($index, 1)">移除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-form-item>

        <!-- 规则卷：规则表 -->
        <el-form-item v-else label="抽题规则">
          <div class="block">
            <el-table :data="form.rules" empty-text="还没有规则">
              <el-table-column label="分类（本考试内任意一级）" min-width="260">
                <template #default="{ row }">
                  <el-cascader
                    v-model="row.categoryId"
                    :options="examCascader"
                    :props="{ checkStrictly: true, emitPath: false }"
                    :disabled="locked"
                    placeholder="选择分类"
                    style="width: 100%"
                  />
                </template>
              </el-table-column>
              <el-table-column label="题型" width="120">
                <template #default="{ row }">
                  <el-select v-model="row.type" :disabled="locked">
                    <el-option v-for="(label, key) in TYPE_LABEL" :key="key" :label="label" :value="key" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="题数" width="130">
                <template #default="{ row }">
                  <el-input-number v-model="row.count" :disabled="locked" :min="1" :max="200" step-strictly size="small" />
                </template>
              </el-table-column>
              <el-table-column label="每题分值" width="140">
                <template #default="{ row }">
                  <el-input-number v-model="row.score" :disabled="locked" :min="1" :max="100" step-strictly size="small" />
                </template>
              </el-table-column>
              <el-table-column label="" width="70">
                <template #default="{ $index }">
                  <el-button link type="danger" :disabled="locked" @click="form.rules.splice($index, 1)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-button class="add-rule" :disabled="locked || !form.categoryId" @click="addRule">加一条规则</el-button>
            <span class="muted hint">同一道题不会被两条规则重复抽到；上架时会检查题量是否够</span>
          </div>
        </el-form-item>

        <el-form-item v-if="errors.length">
          <el-alert type="error" :closable="false" show-icon :title="errors.join('；')" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save">保存</el-button>
          <el-button @click="router.push('/papers')">返回列表</el-button>
          <span v-if="original" class="muted hint">
            当前：{{ original.status === 'ONLINE' ? '已上架' : '已下架' }} · 总分 {{ original.totalScore }} · {{ original.questionCount }} 题
          </span>
        </el-form-item>
      </el-form>
    </div>

    <QuestionPicker
      v-model="pickerOpen"
      :tree="tree"
      :exam-id="form.categoryId"
      :exclude="form.questions.map((q) => q.questionId)"
      @pick="(rows) => (form.questions = addQuestions(form.questions, rows, defaultScore))"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { categoryApi, paperApi } from '@/api'
import type { AdminCategory, AdminPaper, QuestionType } from '@/api/types'
import LoadError from '@/components/LoadError.vue'
import QuestionPicker from '@/components/QuestionPicker.vue'
import {
  addQuestions,
  buildPaperBody,
  emptyPaperForm,
  formFromPaper,
  isStructureLocked,
  moveQuestion,
  previewTotal,
  validatePaperForm,
  type PaperForm,
} from '@/logic/paper'
import { TYPE_LABEL } from '@/logic/question'
import { toCascader } from '@/logic/tree'

const route = useRoute()
const router = useRouter()

const id = computed(() => (route.params.id ? Number(route.params.id) : null))
const tree = ref<AdminCategory[]>([])
const exams = computed(() => tree.value.filter((c) => c.level === 1))
const examCascader = computed(() => toCascader(tree.value, { rootId: form.value.categoryId }))
const form = ref<PaperForm>(emptyPaperForm())
const original = ref<AdminPaper | null>(null)
const locked = computed(() => isStructureLocked(original.value))
const defaultScore = ref(5)
const pickerOpen = ref(false)
const loading = ref(false)
const loadError = ref<unknown>(null)
const saving = ref(false)
const errors = ref<string[]>([])

onMounted(load)

async function load() {
  loading.value = true
  loadError.value = null
  try {
    const [t, p] = await Promise.all([categoryApi.tree(), id.value ? paperApi.detail(id.value) : Promise.resolve(null)])
    tree.value = t
    if (p) {
      original.value = p
      form.value = formFromPaper(p)
    }
  } catch (e) {
    loadError.value = e
  } finally {
    loading.value = false
  }
}

/** 换了考试：原来挑的题和规则都不属于新考试了，清掉 */
function onExamChange() {
  if (form.value.questions.length || form.value.rules.length) {
    ElMessage.info('换了考试，已清空原来的题目和规则')
  }
  form.value.questions = []
  form.value.rules = []
}

function addRule() {
  form.value.rules.push({ categoryId: form.value.categoryId, type: 'SINGLE', count: 5, score: 2 })
}

async function save() {
  errors.value = validatePaperForm(form.value, locked.value)
  if (errors.value.length) return
  saving.value = true
  try {
    const body = buildPaperBody(form.value)
    const saved = id.value ? await paperApi.update(id.value, body) : await paperApi.create(body)
    original.value = saved
    form.value = formFromPaper(saved)
    ElMessage.success(`已保存：总分 ${saved.totalScore}，${saved.questionCount} 题`)
    if (!id.value) router.replace(`/papers/${saved.id}`)
  } catch {
    /* 请求层已提示 */
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.form {
  max-width: 1000px;
}

.lock-tip {
  margin-bottom: 16px;
}

.hint {
  margin-left: 12px;
}

.block {
  width: 100%;
}

.add-rule {
  margin-top: 8px;
}
</style>
