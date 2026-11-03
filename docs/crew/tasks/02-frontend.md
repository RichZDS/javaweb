# 02 前端（手机 App + 管理后台） · assignee: qzd-fe-5b1e · 状态: doing

## 现场 / 锚点
- 工作树 `/data/worktrees/javaweb-fe`，分支 `feat/frontend`。`app/`、`admin/` 都还是空的。
- 接口：`docs/api.md`（前端不判分、不算统计，全部以后端返回为准）。功能和页面：`docs/spec.md`。CI 期望的脚本见 `.github/workflows/ci.yml` 的 `admin`、`app` 两个任务。
- 本机 Node 22，`source /data/crew/qzd-5b1e/bin/env.sh` 后 npm 走官方源。
- 后端由另一位同事在 `feat/backend` 并行开发。后端 M1 合并到 main 前，你先按 api.md 写页面和类型；合并后工头会通知你 `git merge main`，你再起本地后端调通（`cd backend && heavy mvn -B -ntp -DskipTests package && java -jar target/quiz-backend.jar`，dev 配置自带示例题和管理员 `admin` / `admin123`）。

## 做法

### 手机 App（`app/`）
- 用官方模板 `npx degit dcloudio/uni-preset-vue#vite-ts app` 起工程（拉不到就照模板手工建）。Vue 3 + TypeScript + Pinia；组件用 `@dcloudio/uni-ui`；图表用 uCharts（npm `@qiun/ucharts`）。
- App 显示名「码上刷题」，`manifest.json` 里 appid 先留占位 `__UNI__REPLACE_ME`，版本号 1.0.0。
- 接口地址只在一处配置：`import.meta.env.VITE_API_BASE`。`.env.development` 用 `http://localhost:8080/api`；`.env.production`（H5）用 `/quiz/api`；`.env.app`（打安装包）用 `https://43.142.166.108/quiz/api`。H5 路由 base 设 `/quiz/`（`manifest.json` 的 `h5.router.base`），后续可能调整，所以也只放一处。
- `package.json` 脚本：`dev:h5`、`build:h5`（产物 `dist/build/h5`）、`build:app`（`uni build -p app --mode app`，产物 `dist/build/app`）、`type-check`（`vue-tsc --noEmit`）、`test`（`vitest run`）。提交 `package-lock.json`。
- 请求封装：统一带令牌；401 清令牌跳登录；其他错误按后端 `message` 提示；网络失败给“网络不好，稍后重试”。
- 页面（底部 4 个页签：首页、考试、统计、我的）：
  - 登录、注册、选择目标考试（首次登录强制选）。
  - 首页：目标考试切换、科目和章节列表（题量、进度、正确率）、随机刷题、继续练习、错题入口、今日作答次数、每日一练、搜题入口。
  - 随机刷题设置：章节多选、题量 10/20/50、题型、范围；题不够时弹窗“只有 N 道，要开始吗”，确认后带 `acceptFewer` 重发。
  - 练习答题页（所有练习共用）：一页一题、左右滑动、上一题下一题、答题卡（颜色加文字标对错未答）、提交后显示本人答案、标准答案、解析、收藏和笔记、纠错入口；背题模式直接显示答案。进度变化时保存 `currentIndex`。
  - 练习结果页：用时、正确率、本次错题、一键重做错题。
  - 错题本（分组、筛选、两种排序、重练、移出）、收藏夹、练习记录、搜题。
  - 考试：试卷列表、考前须知、考试答题页、成绩单、考试记录。
  - 统计：总览、近 30 天趋势折线、章节柱状图、薄弱点（点一下开专项练）、考试分数折线（画及格线）、周排行榜。
  - 我的：头像昵称、目标考试、错题本、收藏、练习记录、考试记录、设置（字号、夜间模式、学习提醒、关于、退出）。
- 考试答题页的规则（最重要，单独写成可测试的模块）：
  - 用 `serverTime` 算出与本机的时间差，倒计时按服务器时间走；到截止时间锁定作答并自动交卷。
  - 每题每次改答案，版本号 +1，先存本地（`uni.setStorageSync`，按考试记录分开存），再调保存接口；失败进重试队列，网络恢复后补传；每题显示“已保存 / 未同步”。
  - 收到 `accepted:false` 时以服务器版本为准；收到 `EXAM_EXPIRED` 时停止同步、提示已到时间并跳成绩单。
  - 交卷前先把队列里的答案同步完；有未答题先提醒；断网时不显示交卷成功。
  - App 被关掉后重新打开，从“进行中”的记录接着答，本地草稿和服务器已存答案取版本大的。
- 富文本用 `rich-text` 显示后端给的 HTML。
- 只在 App 端（`#ifdef APP-PLUS`）：启动时调 `/api/app/version` 比较 `plus.runtime.version`，有新版弹窗；学习提醒用本地通知。
- 夜间模式用 CSS 变量切换；字号三档。

### 管理后台（`admin/`）
- Vite + Vue 3 + TypeScript + Element Plus + Pinia + Vue Router + axios；图表用 ECharts（`vue-echarts`）。
- `base` 取 `VITE_BASE`（生产 `/quiz/admin/`），接口前缀取 `VITE_API_BASE`（开发 `/api` 并代理到 `http://localhost:8080`，生产 `/quiz/api`）。
- 脚本：`dev`、`build`（产物 `dist`）、`type-check`、`test`（vitest）。提交 `package-lock.json`。
- 页面：登录（只允许 ADMIN）、数据看板、分类管理（树、增改删、排序）、题目管理（筛选列表；编辑表单按题型切换答案输入；Markdown 编辑加预览，预览走 `/api/admin/questions/preview`；上传图片）、Excel 导入（下载模板、上传、逐行结果）、试卷管理（固定卷挑题对话框；规则卷规则表；上下架，题不够时展示缺口）、纠错处理、用户管理（禁用、启用）。

### 阶段
- **M1**：App 工程和请求封装、登录注册、选目标考试、首页、随机刷题设置、练习答题页、结果页、错题本、我的。
- **M2**：App 考试全流程、统计全部图表、顺序练习、搜题、收藏、每日一练、背题、练习记录、排行榜。
- **M3**：管理后台全部页面。
- **M4**：夜间模式、字号、学习提醒、版本检查、`docs/release.md`（Android 和 iOS 用 HBuilderX 云打包的步骤、证书和描述文件怎么准备、TestFlight 分发、版本更新怎么发）。

每个阶段完成就 commit 并报审。

## 明令不做
- 不改 `docs/api.md`；不改 `backend/`。接口不够用就写进报审「疑问」。
- 前端不实现判分和统计口径，只展示后端结果。
- 不引入 uni-app 云开发（uniCloud）、不接任何第三方统计或推送服务。
- 不在仓库里放 appid 以外的任何账号信息、证书。

## 测试
- 把考试同步、倒计时、答案格式转换写成纯函数模块，用 vitest 覆盖：版本递增、旧版本回包、断网重试、截止锁定、草稿与服务器合并。
- 每个阶段报审前：`heavy npm run type-check`、`heavy npm run test`、`heavy npm run build:h5`（M3 起加 admin 的 `type-check`、`test`、`build`）全部通过，贴退出码。
- 后端可用之后，每个阶段在浏览器里真的走一遍：起本地后端，`npm run dev:h5` 或用 build 产物，用 Playwright（`PLAYWRIGHT_BROWSERS_PATH=/data/ms-playwright`，chromium 1217）手机尺寸截图关键页面，存到 `/data/crew/qzd-5b1e/shots/fe-<阶段>/`，报审时附路径。
- `build:app` 在本机跑一次，确认能出 `dist/build/app`（真正的安装包要在 HBuilderX 里云打包，不在本任务范围）。

## 交付
- `feat/frontend` 上每个阶段的 commit。
- 报审：阶段名、commit id、各命令退出码、截图路径、已知问题、疑问。
- `app/README.md`、`admin/README.md`：怎么跑、环境变量。
