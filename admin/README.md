# 码上刷题 · 管理后台

Vite + Vue 3 + TypeScript + Element Plus + Pinia + Vue Router + axios，图表用 ECharts（vue-echarts）。
接口约定以仓库根目录的 [`docs/api.md`](../docs/api.md) 第 9 节为准；只有 `ADMIN` 角色能登录。

## 怎么跑

需要 Node 22。

```bash
cd admin
npm ci
npm run dev        # http://localhost:5173，/api 和 /uploads 代理到 http://localhost:8080
```

后端 `dev` 配置自带管理员 `admin` / `admin123`（仅本机开发用）。后端不在 8080 时，用 `VITE_DEV_BACKEND=http://127.0.0.1:18081 npm run dev` 改代理目标。

| 命令 | 作用 |
| --- | --- |
| `npm run dev` | 开发服务器 |
| `npm run build` | 生产构建，产物 `dist/` |
| `npm run type-check` | `vue-tsc --noEmit` |
| `npm run test` | vitest 单元测试（`tests/`，覆盖 `src/logic` 下的纯函数） |

## 环境变量

| 变量 | `.env.development` | `.env.production` | 说明 |
| --- | --- | --- | --- |
| `VITE_BASE` | `/` | `/quiz/admin/` | 站点路径前缀（Vite `base`） |
| `VITE_API_BASE` | `/api` | `/quiz/api` | 接口前缀 |
| `VITE_DEV_BACKEND` | 不设（默认 `http://localhost:8080`） | — | 只影响开发代理 |

- 路由用 hash 模式，部署在 `/quiz/admin/` 下不需要 Nginx 回退规则。
- 上传的图片后端返回 `/uploads/xx.png`，页面用「接口前缀去掉 `/api`」拼出地址，所以 `/quiz/uploads/` 要和 `/quiz/api/` 指向同一个后端。
- 根目录 `.gitignore` 忽略 `.env.*`，`admin/.gitignore` 把这两份放了回来：只有路径前缀，没有密钥。

## 页面

| 菜单 | 做什么 |
| --- | --- |
| 数据看板 | 用户数、已发布题数、试卷数、今日作答、待处理纠错；近 14 天作答与活跃人数；各试卷考试次数、平均分、及格率 |
| 分类管理 | 考试 → 科目 → 章节三级树；新建、改名、排序、删除（有子分类或有题时后端拒绝并说明原因） |
| 题目管理 | 按分类、题型、状态、关键词筛选；新建 / 编辑表单按题型切换答案输入；题干、解析 Markdown 编辑，预览走后端 `/admin/questions/preview`；上传题图；删除（有人做过的改为归档） |
| Excel 导入 | 下载模板（带令牌下载）；上传 .xlsx（≤ 5 MB），显示总数、成功数和逐行失败原因 |
| 试卷管理 | 固定卷挑题对话框（只列已发布题，可调分值、顺序）；规则卷规则表（分类可选本考试内任意一级）；上下架，规则卷题不够时弹出每条规则的缺口；有人考过后只能改标题和描述 |
| 纠错处理 | 按状态筛选；采纳 / 驳回并回复；一键跳到改题 |
| 用户管理 | 搜索；禁用 / 启用（不能禁用自己） |

## 约定

- 请求统一走 `src/api/http.ts`：带令牌；401 清登录回登录页；新建、保存、删除、上下架、导入失败时按后端 `message` 弹提示（字段校验错误带字段名）；页面加载失败不弹提示，页面里显示原因和「重试」。
- 请求体里的数字、布尔一律转成原生类型再发（`src/logic/question.ts`、`src/logic/paper.ts` 的 `build*Body`），分值、及格分、时长都是整数。
