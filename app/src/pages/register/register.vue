<template>
  <view class="page-root auth" :class="themeClass">
    <view class="brand">
      <text class="brand-name">注册账号</text>
      <text class="brand-sub">注册后直接登录，再选一个目标考试</text>
    </view>

    <view class="card form">
      <view class="field">
        <text class="label">用户名</text>
        <input v-model.trim="form.username" class="input" placeholder="3–20 位字母、数字或下划线" maxlength="40" />
        <text v-if="errors.username" class="err">{{ errors.username }}</text>
      </view>
      <view class="field">
        <text class="label">昵称（可不填）</text>
        <input v-model.trim="form.nickname" class="input" placeholder="不填就用用户名" maxlength="30" />
        <text v-if="errors.nickname" class="err">{{ errors.nickname }}</text>
      </view>
      <view class="field">
        <text class="label">密码</text>
        <input v-model="form.password" class="input" password placeholder="6–32 位" maxlength="64" />
        <text v-if="errors.password" class="err">{{ errors.password }}</text>
      </view>
      <view class="field">
        <text class="label">确认密码</text>
        <input v-model="form.confirm" class="input" password placeholder="再输一次密码" maxlength="64" @confirm="submit" />
        <text v-if="errors.confirm" class="err">{{ errors.confirm }}</text>
      </view>
      <button class="btn-primary submit" :loading="loading" :disabled="loading" @tap="submit">注册并登录</button>
    </view>

    <view class="switch">
      已有账号？<text class="link" @tap="goLogin">去登录</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useTheme } from '@/composables/useTheme'
import { useUserStore } from '@/stores/user'
import { validateNickname, validatePassword, validateUsername } from '@/logic/auth'
import { ApiError } from '@/logic/http'
import { PAGES, afterLogin } from '@/utils/nav'

const { themeClass } = useTheme()
const user = useUserStore()

const form = reactive({ username: '', nickname: '', password: '', confirm: '' })
const errors = reactive({ username: '', nickname: '', password: '', confirm: '' })
const loading = ref(false)

function validate(): boolean {
  errors.username = validateUsername(form.username)
  errors.nickname = validateNickname(form.nickname)
  errors.password = validatePassword(form.password)
  errors.confirm = form.confirm === form.password ? '' : '两次输入的密码不一致'
  return !errors.username && !errors.nickname && !errors.password && !errors.confirm
}

async function submit() {
  if (!validate()) return
  loading.value = true
  try {
    await user.register(form.username, form.password, form.nickname)
    afterLogin()
  } catch (e) {
    // 用户名已存在：除了弹提示，也标在输入框下面
    if (e instanceof ApiError && e.code === 'USERNAME_TAKEN') errors.username = e.message || '用户名已存在'
  } finally {
    loading.value = false
  }
}

function goLogin() {
  // 从登录页进来的就返回，否则直接打开登录页
  const pages = getCurrentPages()
  if (pages.length > 1) uni.navigateBack()
  else uni.redirectTo({ url: PAGES.login })
}
</script>

<style lang="scss" scoped>
@import '@/styles/auth.scss';

.auth {
  padding-top: 24px;
}
</style>
