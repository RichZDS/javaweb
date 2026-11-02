import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AdminLayout from '@/layouts/AdminLayout.vue'

const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true, title: '登录' } },
  {
    path: '/',
    component: AdminLayout,
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '数据看板' } },
      { path: 'categories', name: 'categories', component: () => import('@/views/CategoryView.vue'), meta: { title: '分类管理' } },
      { path: 'questions', name: 'questions', component: () => import('@/views/QuestionListView.vue'), meta: { title: '题目管理' } },
      { path: 'questions/new', name: 'question-new', component: () => import('@/views/QuestionEditView.vue'), meta: { title: '新建题目', menu: 'questions' } },
      { path: 'questions/:id', name: 'question-edit', component: () => import('@/views/QuestionEditView.vue'), meta: { title: '编辑题目', menu: 'questions' } },
      { path: 'import', name: 'import', component: () => import('@/views/ImportView.vue'), meta: { title: 'Excel 导入' } },
      { path: 'papers', name: 'papers', component: () => import('@/views/PaperListView.vue'), meta: { title: '试卷管理' } },
      { path: 'papers/new', name: 'paper-new', component: () => import('@/views/PaperEditView.vue'), meta: { title: '新建试卷', menu: 'papers' } },
      { path: 'papers/:id', name: 'paper-edit', component: () => import('@/views/PaperEditView.vue'), meta: { title: '编辑试卷', menu: 'papers' } },
      { path: 'feedback', name: 'feedback', component: () => import('@/views/FeedbackView.vue'), meta: { title: '纠错处理' } },
      { path: 'users', name: 'users', component: () => import('@/views/UserView.vue'), meta: { title: '用户管理' } },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
]

// hash 路由：部署在 /quiz/admin/ 下不需要服务器额外配置回退规则
export const router = createRouter({ history: createWebHashHistory(import.meta.env.VITE_BASE), routes })

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.loggedIn) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.name === 'login' && auth.loggedIn) return { name: 'dashboard' }
  return true
})

router.afterEach((to) => {
  document.title = `${(to.meta.title as string) || '管理后台'} · 码上刷题`
})
