<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { useUserStore } from '@/stores/user'
import { useSettingsStore } from '@/stores/settings'
import { checkUpdate, scheduleReminders } from '@/utils/native'

onLaunch(() => {
  // 7 天内免登录：本地令牌还有效就直接用，后台拉一次最新资料（令牌被后端拒绝时会跳登录页）
  const user = useUserStore()
  if (user.loggedIn) user.refresh().catch(() => {})

  // 以下只在 App 端生效：重排接下来 7 天的学习提醒；检查新版本
  const settings = useSettingsStore()
  scheduleReminders(settings.reminderOn, settings.reminderTime)
  checkUpdate(false)
})
</script>

<style lang="scss">
/* 主题变量：页面根节点挂 .page-root + 字号 class（fs-S/M/L）+ 可选 theme-dark */
page {
  background: #f4f6fb;
}

.page-root {
  --c-primary: #3a6cf4;
  --c-primary-weak: #e8eefe;
  --c-bg: #f4f6fb;
  --c-card: #ffffff;
  --c-text: #1f2633;
  --c-text-2: #5c6575;
  --c-text-3: #98a0ad;
  --c-border: #e6e9f0;
  --c-correct: #18a058;
  --c-correct-weak: #e7f6ee;
  --c-wrong: #d03050;
  --c-wrong-weak: #fdecef;
  --c-warn: #f0a020;
  --c-warn-weak: #fff5e1;

  --fs-xs: 12px;
  --fs-sm: 14px;
  --fs-base: 16px;
  --fs-lg: 18px;
  --fs-xl: 22px;

  min-height: 100vh;
  box-sizing: border-box;
  /* 包住第一个卡片的外边距，避免它穿出去露出浅色的 page 背景（夜间模式下会看到一条白边） */
  display: flow-root;
  background: var(--c-bg);
  color: var(--c-text);
  font-size: var(--fs-base);
  line-height: 1.6;
}

.page-root.fs-S {
  --fs-xs: 11px;
  --fs-sm: 13px;
  --fs-base: 14px;
  --fs-lg: 16px;
  --fs-xl: 20px;
}

.page-root.fs-L {
  --fs-xs: 14px;
  --fs-sm: 16px;
  --fs-base: 19px;
  --fs-lg: 21px;
  --fs-xl: 25px;
}

.page-root.theme-dark {
  --c-primary: #6b93ff;
  --c-primary-weak: #1f2a47;
  --c-bg: #12151c;
  --c-card: #1c2029;
  --c-text: #e3e6ec;
  --c-text-2: #a9b0bd;
  --c-text-3: #6f7785;
  --c-border: #2b303b;
  --c-correct: #36c47a;
  --c-correct-weak: #173326;
  --c-wrong: #f0607a;
  --c-wrong-weak: #3a1c24;
  --c-warn: #f2b84b;
  --c-warn-weak: #3a2f17;
}

/* H5 夜间模式：覆盖 uni 渲染的导航栏和底部页签栏（App 端由 setNavigationBarColor / setTabBarStyle 处理） */
.quiz-dark uni-page-head .uni-page-head {
  background-color: #1c2029 !important;
  color: #ffffff !important;
}

.quiz-dark uni-page-head .uni-page-head .uni-btn-icon {
  color: #ffffff !important;
}

.quiz-dark .uni-tabbar {
  background-color: #1c2029 !important;
}

.quiz-dark .uni-tabbar-border {
  background-color: #2b303b !important;
}

.quiz-dark uni-page-body,
.quiz-dark body {
  background-color: #12151c;
}

/* 通用小部件 */
.card {
  background: var(--c-card);
  border-radius: 12px;
  margin: 12px;
  padding: 14px 16px;
}

.card-title {
  font-size: var(--fs-lg);
  font-weight: 600;
  margin-bottom: 8px;
}

.muted {
  color: var(--c-text-3);
  font-size: var(--fs-sm);
}

.btn-primary,
.btn-plain {
  border-radius: 22px;
  font-size: var(--fs-base);
  height: 44px;
  line-height: 44px;
}

.btn-primary {
  background: var(--c-primary) !important;
  color: #fff !important;
}

.btn-primary[disabled] {
  opacity: 0.5;
}

.btn-plain {
  background: var(--c-card) !important;
  color: var(--c-primary) !important;
  border: 1px solid var(--c-primary) !important;
}

.btn-primary::after,
.btn-plain::after {
  border: none;
}

/* size="mini" 的小按钮 */
.btn-primary[size='mini'],
.btn-plain[size='mini'] {
  height: 32px;
  line-height: 32px;
  padding: 0 16px;
  font-size: var(--fs-sm);
  border-radius: 16px;
}

.empty {
  text-align: center;
  color: var(--c-text-3);
  padding: 40px 16px;
  font-size: var(--fs-sm);
}

.tag {
  display: inline-block;
  font-size: var(--fs-xs);
  padding: 0 8px;
  border-radius: 10px;
  line-height: 20px;
  background: var(--c-primary-weak);
  color: var(--c-primary);
}

/* 后端渲染的富文本（题干、选项、解析）：代码块等宽、可横向滚动，不遮住按钮 */
.rich {
  word-break: break-word;
  overflow-wrap: anywhere;
}

.rich pre {
  overflow-x: auto;
  max-width: 100%;
  font-family: Menlo, Consolas, 'Courier New', monospace;
  font-size: var(--fs-sm);
  white-space: pre;
}

.rich code {
  font-family: Menlo, Consolas, 'Courier New', monospace;
}

.rich img {
  max-width: 100%;
  height: auto;
}

.rich p {
  margin: 0 0 6px;
}
</style>
