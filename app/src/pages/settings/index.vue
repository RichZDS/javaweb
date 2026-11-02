<template>
  <view class="page-root settings" :class="themeClass">
    <view class="card">
      <view class="card-title">显示</view>
      <view class="row">
        <text class="row-name">字号</text>
        <view class="seg">
          <text v-for="f in FONTS" :key="f.key" class="seg-item" :class="{ on: settings.fontScale === f.key }" @tap="settings.fontScale = f.key">
            {{ f.label }}
          </text>
        </view>
      </view>
      <view class="preview">
        <text class="preview-text">示例：下面哪种数据结构适合按下标随机访问？</text>
      </view>
      <view class="row">
        <text class="row-name">夜间模式</text>
        <switch :checked="settings.dark" color="#3a6cf4" @change="(e: any) => (settings.dark = e.detail.value === true)" />
      </view>
    </view>

    <view class="card">
      <view class="card-title">学习</view>
      <view class="row">
        <view class="row-main">
          <text class="row-name">错题自动移出</text>
          <text class="muted">错题练习里连续答对几次就移出错题本</text>
        </view>
        <view class="stepper">
          <text class="step" :class="{ disabled: threshold <= 1 || savingThreshold }" @tap="changeThreshold(-1)">−</text>
          <text class="step-val">{{ threshold }} 次</text>
          <text class="step" :class="{ disabled: threshold >= 10 || savingThreshold }" @tap="changeThreshold(1)">＋</text>
        </view>
      </view>
      <view class="row">
        <view class="row-main">
          <text class="row-name">学习提醒</text>
          <text class="muted">{{ appOnly ? '每天定时用本机通知提醒你刷题' : '网页版不支持提醒，请在手机 App 里打开' }}</text>
        </view>
        <switch :checked="settings.reminderOn" :disabled="!appOnly" color="#3a6cf4" @change="(e: any) => toggleReminder(e.detail.value === true)" />
      </view>
      <picker v-if="settings.reminderOn" mode="time" :value="settings.reminderTime" @change="changeReminderTime">
        <view class="row">
          <text class="row-name">提醒时间</text>
          <text class="row-value">{{ settings.reminderTime }} ›</text>
        </view>
      </picker>
    </view>

    <view class="card">
      <view class="card-title">账号</view>
      <view class="row" @tap="passwordOpen = true">
        <text class="row-name">修改密码</text>
        <text class="row-value">›</text>
      </view>
    </view>

    <view class="card">
      <view class="card-title">关于</view>
      <view class="row">
        <text class="row-name">码上刷题</text>
        <text class="row-value">版本 {{ version }}</text>
      </view>
      <view class="row" @tap="checkUpdate(true)">
        <text class="row-name">检查更新</text>
        <text class="row-value">›</text>
      </view>
      <view class="about muted">面向计算机专业学生的刷题 App：计算机考研 408、Java 程序设计、数据库原理与 MySQL。示例题目均为原创。</view>
    </view>

    <view class="logout-wrap">
      <button class="btn-plain logout" @tap="logout">退出登录</button>
    </view>

    <uni-popup ref="passwordPopup" type="center" @change="(e: any) => (passwordOpen = e.show)">
      <view class="dialog">
        <view class="dialog-title">修改密码</view>
        <input v-model="pwd.old" class="dialog-input" password placeholder="原密码" maxlength="64" />
        <input v-model="pwd.next" class="dialog-input" password placeholder="新密码（6–32 位）" maxlength="64" />
        <input v-model="pwd.confirm" class="dialog-input" password placeholder="再输一次新密码" maxlength="64" />
        <text v-if="pwdError" class="dialog-err">{{ pwdError }}</text>
        <view class="dialog-btns">
          <button class="btn-plain" size="mini" @tap="passwordOpen = false">取消</button>
          <button class="btn-primary" size="mini" :loading="savingPwd" @tap="savePassword">保存</button>
        </view>
      </view>
    </uni-popup>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { authApi } from '@/api'
import { useTheme } from '@/composables/useTheme'
import { useSettingsStore, type FontScale } from '@/stores/settings'
import { useUserStore } from '@/stores/user'
import { validatePassword } from '@/logic/auth'
import { describeError, toApiError } from '@/logic/http'
import { checkUpdate, currentVersion, isApp, scheduleReminders } from '@/utils/native'
import { PAGES } from '@/utils/nav'

interface PopupRef {
  open: () => void
  close: () => void
}

const FONTS: { key: FontScale; label: string }[] = [
  { key: 'S', label: '小' },
  { key: 'M', label: '标准' },
  { key: 'L', label: '大' },
]

const { themeClass } = useTheme()
const settings = useSettingsStore()
const user = useUserStore()

const appOnly = isApp()
const version = currentVersion()
const threshold = computed(() => user.user?.wrongRemoveThreshold ?? 3)
const savingThreshold = ref(false)

const passwordPopup = ref<PopupRef | null>(null)
const passwordOpen = ref(false)
const pwd = reactive({ old: '', next: '', confirm: '' })
const pwdError = ref('')
const savingPwd = ref(false)

watch(passwordOpen, (open) => {
  if (open) {
    Object.assign(pwd, { old: '', next: '', confirm: '' })
    pwdError.value = ''
    passwordPopup.value?.open()
  } else passwordPopup.value?.close()
})

async function changeThreshold(delta: number) {
  const next = threshold.value + delta
  if (next < 1 || next > 10 || savingThreshold.value) return
  savingThreshold.value = true
  try {
    await user.setWrongThreshold(next)
    uni.showToast({ title: `连续答对 ${next} 次移出`, icon: 'none' })
  } catch {
    /* 请求层已提示 */
  } finally {
    savingThreshold.value = false
  }
}

function toggleReminder(on: boolean) {
  settings.reminderOn = on
  const n = scheduleReminders(settings.reminderOn, settings.reminderTime)
  uni.showToast({ title: settings.reminderOn ? `已开启，每天 ${settings.reminderTime} 提醒（已排 ${n} 天）` : '已关闭提醒', icon: 'none' })
}

function changeReminderTime(e: { detail: { value: string } }) {
  settings.reminderTime = e.detail.value
  scheduleReminders(settings.reminderOn, settings.reminderTime)
  uni.showToast({ title: `改为每天 ${settings.reminderTime} 提醒`, icon: 'none' })
}

async function savePassword() {
  pwdError.value = ''
  if (!pwd.old) {
    pwdError.value = '请输入原密码'
    return
  }
  const err = validatePassword(pwd.next)
  if (err) {
    pwdError.value = err
    return
  }
  if (pwd.next !== pwd.confirm) {
    pwdError.value = '两次输入的新密码不一致'
    return
  }
  savingPwd.value = true
  try {
    await authApi.changePassword({ oldPassword: pwd.old, newPassword: pwd.next })
    passwordOpen.value = false
    uni.showToast({ title: '密码已修改，下次用新密码登录', icon: 'none' })
  } catch (e) {
    // 原密码错时后端返回 400，字段指向 oldPassword
    pwdError.value = describeError(toApiError(e))
  } finally {
    savingPwd.value = false
  }
}

function logout() {
  uni.showModal({
    title: '退出登录',
    content: '确定退出当前账号吗？',
    success: (res) => {
      if (!res.confirm) return
      user.clear()
      uni.reLaunch({ url: PAGES.login })
    },
  })
}
</script>

<style lang="scss" scoped>
.settings {
  padding-bottom: 24px;
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 48px;
  border-bottom: 1px solid var(--c-border);

  &:last-child {
    border-bottom: none;
  }
}

.row-main {
  display: flex;
  flex-direction: column;
  padding: 6px 0;
}

.row-value {
  color: var(--c-text-3);
  font-size: var(--fs-sm);
}

.seg {
  display: flex;
  border: 1px solid var(--c-border);
  border-radius: 8px;
  overflow: hidden;
}

.seg-item {
  padding: 4px 14px;
  font-size: var(--fs-sm);
  color: var(--c-text-2);

  &.on {
    background: var(--c-primary);
    color: #fff;
  }
}

.preview {
  padding: 10px 12px;
  margin: 4px 0 8px;
  border-radius: 8px;
  background: var(--c-bg);
}

.preview-text {
  font-size: var(--fs-base);
}

.stepper {
  display: flex;
  align-items: center;
  gap: 10px;
}

.step {
  width: 28px;
  height: 28px;
  line-height: 28px;
  text-align: center;
  border-radius: 14px;
  border: 1px solid var(--c-border);
  color: var(--c-primary);

  &.disabled {
    opacity: 0.35;
  }
}

.step-val {
  min-width: 40px;
  text-align: center;
  font-weight: 600;
}

.about {
  padding: 8px 0 4px;
}

.logout-wrap {
  padding: 0 16px;
}

.logout {
  color: var(--c-wrong) !important;
  border-color: var(--c-wrong) !important;
}

.dialog {
  width: 300px;
  padding: 18px;
  border-radius: 14px;
  background: var(--c-card);
  color: var(--c-text);
}

.dialog-title {
  font-weight: 600;
  font-size: var(--fs-lg);
  margin-bottom: 10px;
}

.dialog-input {
  height: 40px;
  margin-bottom: 10px;
  padding: 0 12px;
  border-radius: 8px;
  border: 1px solid var(--c-border);
  background: var(--c-bg);
  color: var(--c-text);
  font-size: var(--fs-sm);
}

.dialog-err {
  display: block;
  color: var(--c-wrong);
  font-size: var(--fs-xs);
  margin-bottom: 8px;
}

.dialog-btns {
  display: flex;
  justify-content: flex-end;
  gap: 10px;

  button {
    margin: 0;
  }
}
</style>
