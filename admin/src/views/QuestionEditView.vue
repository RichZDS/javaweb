<template>
  <div>
    <LoadError v-if="loadError" :error="loadError" title="题目没加载出来" @retry="load" />
    <div v-else v-loading="loading" class="page-card">
      <el-form label-width="96px" class="form">
        <el-form-item label="章节" required>
          <el-cascader
            v-model="form.categoryId"
            :options="cascader"
            :props="{ emitPath: false }"
            placeholder="选择考试 / 科目 / 章节"
            filterable
            style="width: 420px"
          />
        </el-form-item>
        <el-form-item label="题型" required>
          <el-radio-group :model-value="form.type" @update:model-value="(t: unknown) => (form = changeType(form, t as QuestionType))">
            <el-radio-button v-for="(label, key) in TYPE_LABEL" :key="key" :value="key">{{ label }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="题干" required>
          <MarkdownField v-model="form.stem" :rows="5" placeholder="题干，支持 Markdown 和代码块" />
        </el-form-item>
        <el-form-item label="题图">
          <ImageField v-model="form.imageUrl" />
        </el-form-item>

        <!-- 单选、多选 -->
        <template v-if="form.type === 'SINGLE' || form.type === 'MULTIPLE'">
          <el-form-item label="选项" required>
            <div class="options">
              <div v-for="(opt, i) in form.options" :key="opt.key" class="option">
                <el-checkbox
                  v-if="form.type === 'MULTIPLE'"
                  :model-value="form.correct.includes(opt.key)"
                  @update:model-value="(on: unknown) => toggleCorrect(opt.key, !!on)"
                >
                  {{ opt.key }}
                </el-checkbox>
                <el-radio v-else :model-value="form.correct[0] ?? ''" :value="opt.key" @update:model-value="form.correct = [opt.key]">
                  {{ opt.key }}
                </el-radio>
                <el-input v-model="opt.text" :placeholder="`选项 ${opt.key}（支持 Markdown）`" />
                <el-button link type="danger" :disabled="form.options.length <= MIN_OPTIONS" @click="form = removeOption(form, i)">删除</el-button>
              </div>
              <el-button size="small" :disabled="form.options.length >= MAX_OPTIONS" @click="form = addOption(form)">加选项</el-button>
              <span class="muted">勾选的就是正确答案；{{ form.type === 'SINGLE' ? '单选题选 1 个' : '多选题至少 2 个' }}</span>
            </div>
          </el-form-item>
        </template>

        <!-- 判断 -->
        <el-form-item v-else-if="form.type === 'JUDGE'" label="正确答案" required>
          <el-radio-group :model-value="form.correct[0] ?? ''" @update:model-value="(v: unknown) => (form.correct = [String(v)])">
            <el-radio value="T">正确</el-radio>
            <el-radio value="F">错误</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 填空 -->
        <template v-else>
          <el-form-item label="每空答案" required>
            <div class="blanks">
              <div v-for="(alts, i) in form.blanks" :key="i" class="blank">
                <span class="blank-no">第 {{ i + 1 }} 空</span>
                <el-select
                  v-model="form.blanks[i]"
                  multiple
                  filterable
                  allow-create
                  default-first-option
                  :reserve-keyword="false"
                  placeholder="输入可接受的答案后回车，可以填多个"
                  style="flex: 1"
                />
                <el-button link type="danger" :disabled="form.blanks.length <= 1" @click="form.blanks.splice(i, 1)">删除</el-button>
                <span v-if="!alts.length" class="muted">还没有答案</span>
              </div>
              <el-button size="small" @click="form.blanks.push([])">加一个空</el-button>
            </div>
          </el-form-item>
          <el-form-item label="判分方式">
            <el-checkbox v-model="form.ignoreCase">忽略大小写</el-checkbox>
            <el-checkbox v-model="form.ignoreSpaces">忽略所有空白</el-checkbox>
            <span class="muted">首尾空白和换行符差异总是忽略</span>
          </el-form-item>
        </template>

        <el-form-item label="解析" :required="form.status === 'PUBLISHED'">
          <MarkdownField v-model="form.analysis" :rows="4" placeholder="解析，发布前必填" />
        </el-form-item>
        <el-form-item label="难度">
          <el-rate v-model="form.difficulty" :max="5" show-score score-template="{value} 级" />
        </el-form-item>
        <el-form-item label="标签">
          <el-select v-model="form.tags" multiple filterable allow-create default-first-option :reserve-keyword="false" placeholder="知识点标签，回车添加" style="width: 420px" />
        </el-form-item>
        <el-form-item label="来源">
          <el-input v-model="form.source" maxlength="255" style="width: 420px" placeholder="原创，或写明授权来源" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="99999" step-strictly :value-on-clear="null" placeholder="留空排到章节末尾" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="DRAFT">草稿</el-radio>
            <el-radio value="PUBLISHED">发布</el-radio>
          </el-radio-group>
          <span v-if="original" class="muted meta">当前版本 {{ original.version }} · 作答 {{ original.answerCount }} 次 · 状态 {{ original.status }}</span>
        </el-form-item>

        <el-form-item v-if="errors.length">
          <el-alert type="error" :closable="false" show-icon :title="errors.join('；')" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save">保存</el-button>
          <el-button @click="router.push('/questions')">返回列表</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { categoryApi, questionApi } from '@/api'
import type { AdminCategory, AdminQuestion, QuestionType } from '@/api/types'
import LoadError from '@/components/LoadError.vue'
import MarkdownField from '@/components/MarkdownField.vue'
import ImageField from '@/components/ImageField.vue'
import {
  MAX_OPTIONS,
  MIN_OPTIONS,
  TYPE_LABEL,
  addOption,
  buildQuestionBody,
  changeType,
  emptyQuestionForm,
  formFromQuestion,
  removeOption,
  validateQuestionForm,
  type QuestionForm,
} from '@/logic/question'
import { toCascader } from '@/logic/tree'

const route = useRoute()
const router = useRouter()

const id = computed(() => (route.params.id ? Number(route.params.id) : null))
const tree = ref<AdminCategory[]>([])
const cascader = computed(() => toCascader(tree.value, { onlyLevel3: true }))
const form = ref<QuestionForm>(emptyQuestionForm())
const original = ref<AdminQuestion | null>(null)
const loading = ref(false)
const loadError = ref<unknown>(null)
const saving = ref(false)
const errors = ref<string[]>([])

onMounted(load)

async function load() {
  loading.value = true
  loadError.value = null
  try {
    const [t, q] = await Promise.all([categoryApi.tree(), id.value ? questionApi.detail(id.value) : Promise.resolve(null)])
    tree.value = t
    if (q) {
      original.value = q
      form.value = formFromQuestion(q)
    }
  } catch (e) {
    loadError.value = e
  } finally {
    loading.value = false
  }
}

function toggleCorrect(key: string, on: boolean) {
  const set = new Set(form.value.correct)
  if (on) set.add(key)
  else set.delete(key)
  form.value.correct = [...set]
}

async function save() {
  errors.value = validateQuestionForm(form.value)
  if (errors.value.length) return
  saving.value = true
  try {
    const body = buildQuestionBody(form.value)
    const saved = id.value ? await questionApi.update(id.value, body) : await questionApi.create(body)
    ElMessage.success(id.value ? `已保存，版本 ${saved.version}` : `已新建第 ${saved.id} 题`)
    original.value = saved
    if (!id.value) router.replace(`/questions/${saved.id}`)
    else form.value = formFromQuestion(saved)
  } catch {
    /* 请求层已提示（含字段级校验错误） */
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.form {
  max-width: 900px;
}

.options,
.blanks {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.option,
.blank {
  display: flex;
  align-items: center;
  gap: 10px;
}

.blank-no {
  width: 48px;
  flex: none;
  color: #5c6575;
}

.meta {
  margin-left: 16px;
}
</style>
