<template>
  <div class="login">
    <el-card class="box" shadow="never">
      <h2>码上刷题 · 管理后台</h2>
      <el-alert v-if="route.query.expired" title="登录已过期，请重新登录" type="warning" :closable="false" show-icon class="tip" />
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model.trim="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password autocomplete="current-password" @keyup.enter="submit" />
        </el-form-item>
        <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="tip" />
        <el-button type="primary" class="submit" :loading="loading" native-type="submit">登录</el-button>
      </el-form>
      <p class="muted">只有管理员账号可以登录后台。</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { authApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/logic/http'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const form = reactive({ username: '', password: '' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}
const loading = ref(false)
const error = ref('')

async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  loading.value = true
  error.value = ''
  try {
    // 登录失败（用户名或密码错误、账号禁用）的提示由请求层弹出，这里同时显示在表单里
    const result = await authApi.login(form.username, form.password).catch((e) => {
      error.value = describeError(e)
      throw e
    })
    if (result.user.role !== 'ADMIN') {
      error.value = '这个账号不是管理员，不能登录后台'
      return
    }
    auth.apply(result)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    router.replace(redirect)
  } catch {
    /* 已显示 */
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f2a44, #3a6cf4);
}

.box {
  width: 360px;
}

h2 {
  margin: 0 0 16px;
  text-align: center;
}

.tip {
  margin-bottom: 12px;
}

.submit {
  width: 100%;
}
</style>
