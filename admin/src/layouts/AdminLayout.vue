<template>
  <el-container class="layout">
    <el-aside width="208px" class="aside">
      <div class="brand">码上刷题 · 后台</div>
      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item v-for="m in MENUS" :key="m.path" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="title">{{ route.meta.title }}</span>
        <span class="spacer" />
        <span class="who">{{ auth.user?.nickname || auth.user?.username }}</span>
        <el-button link type="primary" @click="logout">退出</el-button>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChatLineSquare, DataLine, Document, Files, FolderOpened, Upload, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const MENUS = [
  { path: '/dashboard', title: '数据看板', icon: DataLine },
  { path: '/categories', title: '分类管理', icon: FolderOpened },
  { path: '/questions', title: '题目管理', icon: Document },
  { path: '/import', title: 'Excel 导入', icon: Upload },
  { path: '/papers', title: '试卷管理', icon: Files },
  { path: '/feedback', title: '纠错处理', icon: ChatLineSquare },
  { path: '/users', title: '用户管理', icon: User },
]

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

/** 编辑页高亮所属菜单 */
const activeMenu = computed(() => {
  const menu = route.meta.menu as string | undefined
  return menu ? `/${menu}` : route.path
})

function logout() {
  auth.clear()
  router.replace({ name: 'login' })
}
</script>

<style scoped>
.layout {
  height: 100%;
}

.aside {
  background: #1f2a44;
  color: #fff;
}

.brand {
  height: 56px;
  line-height: 56px;
  padding-left: 20px;
  font-weight: 700;
  font-size: 16px;
}

.menu {
  border-right: none;
  --el-menu-bg-color: #1f2a44;
  --el-menu-text-color: #c7cfdd;
  --el-menu-hover-bg-color: #2b3757;
  --el-menu-active-color: #fff;
}

.menu :deep(.el-menu-item.is-active) {
  background: #3a6cf4;
}

.header {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border-bottom: 1px solid #e6e9f0;
}

.title {
  font-weight: 600;
  font-size: 16px;
}

.spacer {
  flex: 1;
}

.who {
  color: #5c6575;
}

.main {
  padding: 16px;
}
</style>
