<template>
  <view class="page-root detail" :class="themeClass">
    <view v-if="state === 'loading'" class="empty">加载中…</view>
    <view v-else-if="state === 'locked'" class="empty">
      <view>这道题在你正在进行的考试里</view>
      <view>考试结束前不能查看答案</view>
    </view>
    <view v-else-if="state === 'error'" class="empty">
      <view>{{ errorText }}</view>
      <button class="btn-plain retry" size="mini" @tap="load">重试</button>
    </view>

    <view v-else-if="q" class="card">
      <QuestionBody :question="q" :model-value="lastWrong" locked :correct-answer="q.type !== 'FILL' ? q.correctAnswer : null" />
      <AnswerReveal
        :type="q.type"
        :correct="lastWrong.length ? false : null"
        :user-answer="lastWrong"
        :correct-answer="q.correctAnswer"
        :accepted-answers="q.acceptedAnswers"
        :analysis-html="q.analysisHtml"
        :recite="!lastWrong.length"
        user-label="当时答案"
      />
      <view v-if="q.note" class="note">
        <text class="note-label">我的笔记</text>
        <text class="note-text">{{ q.note }}</text>
      </view>
      <view class="ops">
        <button class="btn-plain" size="mini" @tap="toggleFavorite">{{ q.favorited ? '取消收藏' : '收藏' }}</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { favoriteApi, questionApi } from '@/api'
import type { QuestionDetail } from '@/api/types'
import QuestionBody from '@/components/QuestionBody.vue'
import AnswerReveal from '@/components/AnswerReveal.vue'
import { useTheme } from '@/composables/useTheme'
import { ApiError } from '@/logic/http'

const { themeClass } = useTheme()

const id = ref(0)
const q = ref<QuestionDetail | null>(null)
/** 从错题本进来时带上「当时的错误答案」 */
const lastWrong = ref<string[]>([])
const state = ref<'loading' | 'ready' | 'locked' | 'error'>('loading')
const errorText = ref('题目没加载出来')

onLoad((query) => {
  id.value = Number(query?.id)
  if (query?.lastWrong) {
    try {
      const parsed = JSON.parse(decodeURIComponent(query.lastWrong))
      if (Array.isArray(parsed)) lastWrong.value = parsed.map(String)
    } catch {
      /* 忽略格式不对的参数 */
    }
  }
  load()
})

async function load() {
  state.value = 'loading'
  try {
    q.value = await questionApi.detail(id.value, ['EXAM_IN_PROGRESS', 'NOT_FOUND'])
    state.value = 'ready'
  } catch (e) {
    if (e instanceof ApiError && e.code === 'EXAM_IN_PROGRESS') state.value = 'locked'
    else {
      errorText.value = e instanceof ApiError && e.code === 'NOT_FOUND' ? '题目不存在或已下架' : '题目没加载出来'
      state.value = 'error'
    }
  }
}

async function toggleFavorite() {
  if (!q.value) return
  try {
    if (q.value.favorited) {
      await favoriteApi.remove(q.value.id)
      q.value.favorited = false
      q.value.note = null
    } else {
      await favoriteApi.save(q.value.id)
      q.value.favorited = true
    }
  } catch {
    /* 请求层已提示 */
  }
}
</script>

<style lang="scss" scoped>
.detail {
  padding-bottom: 24px;
}

.note {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--c-warn-weak);
  display: flex;
  flex-direction: column;
}

.note-label {
  font-size: var(--fs-xs);
  color: var(--c-warn);
}

.note-text {
  white-space: pre-wrap;
  font-size: var(--fs-sm);
}

.ops {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;

  button {
    margin: 0;
  }
}

.retry {
  margin-top: 12px;
}
</style>
