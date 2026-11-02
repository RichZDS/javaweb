<template>
  <view class="page-root me" :class="themeClass">
    <view class="profile">
      <view class="avatar" @tap="changeAvatar">
        <image v-if="user.avatar" class="avatar-img" :src="user.avatar" mode="aspectFill" />
        <text v-else class="avatar-text">{{ initial }}</text>
        <view class="avatar-edit">换</view>
      </view>
      <view class="profile-main">
        <view class="nick-row" @tap="editNickname">
          <text class="nick">{{ user.displayName }}</text>
          <uni-icons type="compose" size="16" color="#fff" />
        </view>
        <text class="uname">账号 {{ user.user?.username }}</text>
      </view>
    </view>

    <view class="card list">
      <view class="row" @tap="go(PAGES.target)">
        <text class="row-name">目标考试</text>
        <text class="row-value">{{ user.targetCategoryName || '未选择' }}</text>
        <uni-icons type="right" size="16" color="#98a0ad" />
      </view>
    </view>

    <view class="card list">
      <view class="row" @tap="go(PAGES.wrong)">
        <text class="row-name">错题本</text>
        <text class="row-value" />
        <uni-icons type="right" size="16" color="#98a0ad" />
      </view>
    </view>

    <view class="logout-wrap">
      <button class="btn-plain logout" @tap="logout">退出登录</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { authApi } from '@/api'
import { useTheme } from '@/composables/useTheme'
import { useUserStore } from '@/stores/user'
import { validateNickname } from '@/logic/auth'
import { PAGES, ensureReady } from '@/utils/nav'

const MAX_AVATAR_BYTES = 2 * 1024 * 1024

const { themeClass } = useTheme()
const user = useUserStore()

const initial = computed(() => (user.displayName || '?').slice(0, 1).toUpperCase())

onShow(() => {
  ensureReady()
})

function go(url: string) {
  uni.navigateTo({ url })
}

function editNickname() {
  uni.showModal({
    title: '修改昵称',
    editable: true,
    placeholderText: '最多 30 个字',
    content: user.user?.nickname ?? '',
    success: async (res) => {
      if (!res.confirm) return
      const nickname = (res.content ?? '').trim()
      if (!nickname) {
        uni.showToast({ title: '昵称不能为空', icon: 'none' })
        return
      }
      const err = validateNickname(nickname)
      if (err) {
        uni.showToast({ title: err, icon: 'none' })
        return
      }
      try {
        await user.update({ nickname })
        uni.showToast({ title: '已保存', icon: 'none' })
      } catch {
        /* 请求层已提示 */
      }
    },
  })
}

/** 头像：选图 → 本地先查大小（≤ 2 MB）→ 上传 → 保存到资料 */
function changeAvatar() {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    success: async (res) => {
      const files = res.tempFiles as { path: string; size: number }[] | { path: string; size: number }
      const file = Array.isArray(files) ? files[0] : files
      if (!file) return
      if (file.size > MAX_AVATAR_BYTES) {
        uni.showToast({ title: '图片不能超过 2 MB', icon: 'none' })
        return
      }
      uni.showLoading({ title: '上传中' })
      try {
        const { url } = await authApi.uploadImage(file.path)
        await user.update({ avatarUrl: url })
        uni.hideLoading()
        uni.showToast({ title: '头像已更新', icon: 'none' })
      } catch {
        uni.hideLoading()
      }
    },
  })
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
.profile {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 12px;
  padding: 20px 16px;
  border-radius: 14px;
  background: linear-gradient(135deg, #3a6cf4, #5b8cff);
  color: #fff;
}

.avatar {
  position: relative;
  width: 64px;
  height: 64px;
  border-radius: 32px;
  background: rgba(255, 255, 255, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  flex: none;
}

.avatar-img {
  width: 64px;
  height: 64px;
}

.avatar-text {
  font-size: 28px;
  font-weight: 700;
}

.avatar-edit {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 18px;
  line-height: 18px;
  text-align: center;
  font-size: 11px;
  background: rgba(0, 0, 0, 0.35);
}

.profile-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.nick-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.nick {
  font-size: var(--fs-xl);
  font-weight: 700;
}

.uname {
  font-size: var(--fs-sm);
  opacity: 0.85;
}

.list {
  padding: 0 16px;
}

.row {
  display: flex;
  align-items: center;
  height: 52px;
  border-bottom: 1px solid var(--c-border);

  &:last-child {
    border-bottom: none;
  }
}

.row-name {
  flex: none;
}

.row-value {
  flex: 1;
  text-align: right;
  margin-right: 6px;
  color: var(--c-text-3);
  font-size: var(--fs-sm);
}

.logout-wrap {
  padding: 12px 16px;
}

.logout {
  color: var(--c-wrong) !important;
  border-color: var(--c-wrong) !important;
}
</style>
