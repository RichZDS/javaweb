<template>
  <view class="page-root auth" :class="themeClass">
    <view class="brand">
      <text class="brand-name">码上刷题</text>
      <text class="brand-sub">计算机考研 · Java · 数据库，随时刷一刷</text>
    </view>

    <view class="card form">
      <view class="field">
        <text class="label">用户名</text>
        <input v-model.trim="username" class="input" placeholder="3–20 位字母、数字或下划线" maxlength="20" />
        <text v-if="errors.username" class="err">{{ errors.username }}</text>
      </view>
      <view class="field">
        <text class="label">密码</text>
        <input v-model="password" class="input" password placeholder="6–32 位" maxlength="32" @confirm="submit" />
        <text v-if="errors.password" class="err">{{ errors.password }}</text>
      </view>
      <button class="btn-primary submit" :loading="loading" :disabled="loading" @tap="submit">登录</button>
      <view class="muted hint">登录后 7 天内免登录</view>
    </view>

    <view class="switch">
      还没有账号？<text class="link" @tap="goRegister">去注册</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useTheme } from '@/composables/useTheme'
import { useUserStore } from '@/stores/user'
import { validateUsername } from '@/logic/auth'
import { PAGES, afterLogin } from '@/utils/nav'

const { themeClass } = useTheme()
const user = useUserStore()

const username = ref('')
const password = ref('')
const loading = ref(false)
const errors = reactive({ username: '', password: '' })

async function submit() {
  errors.username = validateUsername(username.value)
  errors.password = password.value ? '' : '请输入密码'
  if (errors.username || errors.password) return
  loading.value = true
  try {
    await user.login(username.value, password.value)
    afterLogin()
  } catch {
    // 错误提示（用户名或密码错误、账号被禁用、网络）已由请求层按后端 message 弹出
  } finally {
    loading.value = false
  }
}

function goRegister() {
  uni.navigateTo({ url: PAGES.register })
}

</script>

<style lang="scss" scoped>
@import '@/styles/auth.scss';
</style>
