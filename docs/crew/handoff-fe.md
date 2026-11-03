# 交接 · 前端 qzd-fe-5b1e（2026-11-03 解散）

负责范围：`app/`（uni-app 手机 App / H5）、`admin/`（Vue 3 + Element Plus 管理后台）、`android/`（Android 网页套壳）、`docs/release.md`、`deploy/download/index.html`、CI 的 `app` / `admin` / `android` 任务。工作树 `/data/worktrees/javaweb-fe`，分支 `feat/frontend`。

## 1 在做哪张单

没有在做的单。最后一张是 **APK-WEBVIEW**（`board/tasks/06b-apk-webview-fe.md`，Android 网页套壳）：审过，合入 main `648b35e`，工头正在连同安装包部署到线上下载页。之前的单都已签收合入：
- 里程碑 M1–M4；
- D2 选项打乱；
- 自查两条 bug（FE-D2-OK）；
- iOS 12 兼容（FE-SR-OK）；
- 记录范围和搜题范围（FIX-FINAL-FE）。

06 号单（DCloud 离线 SDK 打包）已按工头指示停掉，原因见第 4 节。

## 2 做到哪一步（可验证的事实）

- **代码**：`feat/frontend` HEAD 是 `cc6cf04`，工作树干净，已全部合进 main（`648b35e`）。没有没提交的改动。
- **最后几次提交**：
  - `cc6cf04`：保留 androidx 的内部广播权限；
  - `fed5474`：Android 套壳；
  - `8c46716`：记录范围和搜题范围；
  - `5056e11`：iOS 12 兼容；
  - `6f22f42`、`7f3269e`：自查两条 bug。
- **最后一次检查**（都走 heavy 闸门，日志在 `/data/crew/qzd-5b1e/logs/`）：
  - `app`：type-check 0、`npm test` 0（117 例）、build:h5 0、build:app 0（`logs/fe-apk/exit-codes.txt`）。
  - `android`：`android/build-apk.sh` 0（JUnit 13 例）；不带签名变量跑 `testDebugUnitTest assembleDebug lintDebug` 0，lint 没有错误，只有 2 个预期的警告（`logs/fe-apk/`）。
  - 安装包 `android/dist/quiz-1.0.0.apk`：110,486 字节，v2 签名，证书 SHA-1 `76:F7:C5:2A:D6:A9:D9:9E:04:B3:7B:90:2A:19:3A:D4:76:EA:D7:E8`；`com.richzds.quiz` 1.0.0 / 100，minSdk 26，targetSdk 37（`logs/fe-apk/verify-perm.txt`）。工头已用 apksigner / aapt2 复核。
  - `admin`：最近一次全量检查在 D2（type-check、单测、build 都是 0）。之后 admin 没再改过，也没再跑。
- **进程**：本地后端（15080）已按 PID 停掉，`backend.pid` 已删；15xx0 端口没有监听；Gradle 用的是 `--no-daemon`，没有常驻进程。
- **记录**：每一张单的说明、命令、退出码、截图路径都在 `board/worklog-fe.md`（最新的在最上面）。截图在 `/data/crew/qzd-5b1e/shots/fe-*`，走查脚本放在各目录的 `_scripts/` 下。

## 3 动了哪些文件（概括）

- `app/`：整个 uni-app 工程。
  - `src/api` 接口封装；
  - `src/logic` 纯逻辑：考试同步 `exam.ts`、按考试隔离 `scoped.ts`、记录范围 `records.ts`、搜题范围 `searchScope.ts`、套壳识别 `shell.ts`、选项打乱等，都有单测；
  - `src/pages` 全部页面；`src/components`；`src/composables`（`useRecordList`、`useTargetData`、`useTheme`）；
  - `src/utils`：`compat.ts` 放兼容老 iOS 的小工具，`native.ts` 放 App 端能力和套壳的检查更新；
  - `tests/`，`vite.config.ts`（构建目标 iOS 12）。
- `admin/`：整个管理后台（题库、试卷、分类树、用户、看板、Excel 导入），带单测。
- `android/`：Java WebView 套壳。
  - `MainActivity`；`UrlPolicy` 决定站内还是站外；`WebShellRules` 管 UA 标记和拍照入口；
  - 矢量图标和启动图；`build-apk.sh`；Gradle wrapper 9.6.0；JUnit 测试。
- `docs/release.md`：最前面是「第一版：网页套壳安装包」，后面是 uni-app 原生版（HBuilderX 云打包、TestFlight）的备选步骤。
- `deploy/download/index.html`：下载页。先用 HEAD 查安装包，不在就显示「安装包准备中」。
- `.github/workflows/ci.yml`：`app`、`admin`、`android` 三个任务。

## 4 没来得及做的

- **APK 没在真机上装过**：本机没有 KVM，跑不了模拟器。`docs/release.md` 里的「真机检查」清单要有人在手机上过一遍：登录保持、返回键、相册和拍照换头像、断网错误页、外链、状态栏颜色。
- **iOS**：没有安装包，iPhone 用户用网页版。
- **uni-app 原生安装包没出过**：
  - 离线 SDK 打包（06 号单）停了，因为对应版本 HBuilderX 5.34.2026102909 的 SDK 只在百度网盘 / 和彩云上，要登录才能下。
  - HBuilderX 云打包需要 DCloud 账号，本机也做不了。
  - 以后要做，按 release.md 第 1–6 节，必须用同一个包名和同一份证书。
- **自查时记下的低风险问题，没改**：
  - `useTargetData` 对同一个考试连续刷新时，后发的请求可能先回来，被旧结果覆盖，建议加请求序号；
  - 已交卷考试的本机草稿 `quiz.exam.<记录id>` 没有清理。
- **可以清掉的工具**（都是我装的，大约 460 MB）：`/data/tools/gradle-9.6.0`（只用来生成 wrapper）、`/data/tools/downloads`（校验过的 zip）。`/data/tools/android-sdk` 和 `/data/tools/gradle-home` 出包还要用，别删。

## 5 坑（下家会踩的）

- **老 iOS 解析失败**：uni-app 默认按 Safari 14 出代码，会留下 `??`，iOS 13.4 以下读到就整个包解析失败。`app/vite.config.ts` 里的 `build.target: ['es2018', 'ios12']` 别删。新的内置函数构建不会补，`tests/compat.test.ts` 会扫源码里的 `allSettled`、`fromEntries`、`globalThis`、`.at(`、`replaceAll` 等，报错了就用 `src/utils/compat.ts` 里的写法。
- **套壳 UA 标记两边要一致**：`MaShangShuaTiApp` 写在 `android/.../WebShellRules.UA_MARK` 和 `app/src/logic/shell.ts` 两处，改名要两边一起改。网页版靠它隐藏学习提醒、检查更新比安装包版本。
- **签名证书**：在 `/data/workspace/quiz-release/`，`keystore.env` 有四行：KEYSTORE、ALIAS、STORE_PASSWORD、KEY_PASSWORD。
  - 不进仓库、不打印。
  - 证书丢了，以后的版本就不能覆盖安装。
  - 原生版必须用同一份证书和包名 `com.richzds.quiz`，versionCode 从 200 起（套壳用 100–199）。
- **AGP 9 / Gradle 的坑**：
  - 只有 `testDebugUnitTest`，没有 release 的单测任务。
  - 改资源目录名（比如把 `-v26` 去掉）后要先 `./gradlew clean`，否则报「找不到资源」。
  - XML 注释里不能出现 `--`。
  - targetSdk 35 起强制全面屏，状态栏和导航栏的位置是用 `MainActivity.applyInsets()` 自己垫出来的。
  - 环境变量：`ANDROID_HOME=/data/tools/android-sdk`，`GRADLE_USER_HOME=/data/tools/gradle-home`。
- **下载页的 HEAD 检查**依赖 nginx 对 `/quiz/download/` 缺文件返回 404（`try_files … =404`）。如果改成 SPA 那种回退到 index.html，缺包时也会返回 200，按钮就会给出坏链接。
- **H5 走查（Playwright）**：
  - uni 的 H5 会把栈里的旧页面留在 DOM 里，选元素要限定在当前可见的页面。
  - 提示框是 `<uni-toast>` 元素（选择器 `uni-toast`，不是 `.uni-toast`）。
  - 多个元素匹配时，`innerText()` 在严格模式下会抛错，用 `allInnerTexts()`。
  - 本机有代理时，浏览器加 `--no-proxy-server`，并 unset 掉 `http(s)_proxy`。
- **契约**：`docs/api.md` 是唯一依据，前端不改它，发现问题先报工头。记录列表、错题本、统计、继续练习都默认按目标考试过滤（0.4 节）；首页「继续练习」前端另外加了一层兜底判断。
- **预发布站的证书**：用的是 Let's Encrypt 给 IP 签的短期证书，由 Caddy 自动续期。App 和套壳的接口地址都写死这个 IP，续期一旦失败，App 里所有请求都会失败。
- **共享机器的规矩**：
  - 重任务（npm build、vue-tsc、gradle）用 `heavy sozai-lock heavy -- …`；
  - 只停自己记了 PID 的进程，不用 `pkill` / `pgrep -f`；
  - 前端端口段是 15xx0。
