# 码上刷题 · 手机 App

uni-app（Vue 3 + TypeScript + Pinia），一套代码出 H5、Android、iOS。组件用 `@dcloudio/uni-ui`，图表用 uCharts。
接口约定以仓库根目录的 [`docs/api.md`](../docs/api.md) 为准；前端不判分、不算统计，只展示后端结果。

## 怎么跑

需要 Node 22。

```bash
cd app
npm ci
npm run dev:h5      # 浏览器调试，接口直连 http://localhost:8080/api（后端 dev 配置已放行 localhost 跨域）
```

后端在仓库 `backend/` 下，`dev` 配置自带示例题库和管理员账号，见根目录 README。

| 命令 | 作用 |
| --- | --- |
| `npm run dev:h5` | H5 开发服务器 |
| `npm run build:h5` | H5 生产包，产物 `dist/build/h5` |
| `npm run build:app` | App 资源包，产物 `dist/build/app`（用 HBuilderX 导入后云打包，见 `docs/release.md`） |
| `npm run type-check` | `vue-tsc --noEmit` |
| `npm run test` | vitest 单元测试（`tests/`，只测 `src/logic` 下的纯函数） |

## 环境变量

接口地址只在一处配置：`VITE_API_BASE`，按构建模式取不同文件。

| 文件 | 用于 | `VITE_API_BASE` |
| --- | --- | --- |
| `.env.development` | `npm run dev:h5` | `http://localhost:8080/api` |
| `.env.production` | `npm run build:h5`（部署在 `/quiz/` 下） | `/quiz/api` |
| `.env.app` | `npm run build:app`（安装包） | `https://43.142.166.108/quiz/api` |

- 根目录 `.gitignore` 忽略 `.env.*`，`app/.gitignore` 把这三份放了回来：里面只有接口地址，没有密钥。
- 头像、题图这类上传文件，后端返回 `/uploads/xx.png`，前端用接口地址去掉末尾 `/api` 拼出完整地址（`src/logic/http.ts` 的 `resolveAssetUrl`），所以部署时 `/quiz/uploads/` 要和 `/quiz/api/` 指向同一个后端。
- H5 路由用 hash 模式，`base` 只在 `src/manifest.json` 的 `h5.router.base`（当前 `/quiz/`）一处配置；hash 模式下 Nginx 不需要额外的 history 回退规则。
- App 的 `appid` 在 `src/manifest.json` 里是占位 `__UNI__REPLACE_ME`，云打包前换成自己 DCloud 账号下的 appid。

## 目录

```
src/
  api/          接口类型（types.ts）、请求封装（http.ts）、按模块分组的接口函数（index.ts）
  logic/        纯函数：返回解释、作答格式转换、展示格式化、账号校验（有单元测试）
  stores/       Pinia：登录状态（7 天免登录）、本机设置、练习结果传递
  components/   题目展示、答案解析、答题卡
  pages/        页面，路由见 pages.json
  static/tab/   底部页签图标，由 scripts/tab-icons.mjs 生成
tests/          vitest 单元测试
```

## 考试答题（`src/logic/exam.ts`，有单元测试）

- 每题每次改答案（或「稍后再看」）版本号 +1：先写本地草稿（`uni.setStorageSync('quiz.exam.<记录id>')`），再调保存接口。
- 每题同一时间只有一个保存请求；请求在路上时又改了，等回包后只发最新版本。
- 回包 `accepted:false`：以服务器为准，重新取一次考试记录拿服务器上的答案。
- 断网 / 服务器出错：留在队列里，1s、2s、4s…（最长 30s）退避重试；网络恢复事件立即补传。每题显示「已保存 / 未同步」。
- `EXAM_EXPIRED`：停止同步，提示已到时间，去成绩单。
- 倒计时：用接口的 `serverTime` 和本机单调时钟（`performance.now()`）做锚点推算服务器时间，改手机时间不影响；页面回到前台时重新取一次服务器时间校正。到截止时间锁定作答并自动交卷。
- 交卷前先把队列同步完，有没传上去的就不交、不显示成功；有未答 / 稍后再看的题先提醒。
- 重新打开 App：本地草稿和服务器已存答案按题取版本大的，本地更新的自动补传。

## 约定

- 请求统一走 `src/api/http.ts`：自动带 `Authorization: Bearer <token>`；401 清令牌跳登录页；其他错误按后端 `message` 弹提示；连不上服务器提示「网络不好，稍后重试」。需要自己处理某个错误码（如 `POOL_INSUFFICIENT`）时传 `silent: ['错误码']` 再 catch。
- 主题用 CSS 变量（`App.vue`），每个页面根节点写 `<view class="page-root" :class="themeClass">`，字号三档、夜间模式都靠切换这个 class。
- 时间一律按北京时间显示（`formatDateTime`），没有数据的正确率显示「—」，不显示 0% 或 NaN。
