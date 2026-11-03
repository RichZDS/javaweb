# 06 · Android 安装包：本机离线打包（qzd-fe-5b1e）

用户选了「注册 DCloud 账号、正规打包」。为了让用户只在网页上点几下，我们在本机用 uni-app **Android 离线 SDK** 打包，不用 HBuilderX。

## 已经有的
- 签名证书：`/data/workspace/quiz-release/quiz-release.keystore`，别名、密码在同目录 `keystore.env`（600）。**不能进仓库、不能打印密码**。SHA1 `76:F7:C5:2A:D6:A9:D9:9E:04:B3:7B:90:2A:19:3A:D4:76:EA:D7:E8`。
- 包名定为 `com.richzds.quiz`，版本 1.0.0 / versionCode 100。
- AppID、AppKey：用户在 DCloud 开发者中心建应用、按包名 + SHA1 申请离线打包 Key 后给我，我写进 `/data/workspace/quiz-release/dcloud.env`（`DCLOUD_APPID=`、`DCLOUD_APPKEY=`）。在那之前用占位值把整条流水线跑通（装上会提示 AppKey 错误，没关系）。

## 要做
1. 工具都放 `/data/tools`（根分区只剩 18G，不要往 `~` 下装）：Android SDK（cmdline-tools、platform-tools、离线 SDK 要求的 build-tools/platform）→ `/data/tools/android-sdk`；`GRADLE_USER_HOME=/data/tools/gradle-home`；离线 SDK 版本要和 `@dcloudio/uni-app 3.0.0-5030420261029002` 对应（HBuilderX 5.03 系列），放 `/data/tools/uniapp-android-sdk-<版本>`。本机有 HTTP 代理环境变量，Gradle 需要的话自己配。
2. 写 `app/scripts/build-android.sh`：`npm run build:app` → 把 `dist/build/app` 放进离线工程 `assets/apps/<APPID>/www` → 写 appid（dcloud_control.xml）、`dcloud_appkey`、applicationId、版本、签名（读 keystore.env）、图标 → gradle 出 release APK 到 `app/dist/release/quiz-<versionName>.apk`。AppID/AppKey 从 `/data/workspace/quiz-release/dcloud.env` 读，缺了就用占位并打印醒目提示。脚本不写任何密码。
3. 应用里用到的原生能力：本地提醒（`plus.push` 本地消息）、`plus.runtime`（版本、打开下载页）、相册/拍照选头像。按离线 SDK 文档只带需要的模块和权限；Android 13+ 通知权限 `POST_NOTIFICATIONS` 要有；把 manifest 里没用到的权限去掉。
4. 图标：用现有 logo 生成 Android 各尺寸图标和启动图，配进 manifest / 离线工程。
5. 下载页 `deploy/download/index.html`：`quiz.apk` 不存在（HEAD 404）时按钮换成「安装包准备中」，别给 404 链接。
6. 验证（模拟器跑不了，本机没有 KVM）：`apksigner verify --print-certs` 证书 SHA1 和上面一致；`aapt2 dump badging` 包名、版本、minSdk/targetSdk、权限对；APK 大小；解包看 `assets/apps/<APPID>/www` 里接口地址是 `https://43.142.166.108/quiz/api`。
7. `docs/release.md`：新增「Android 离线打包（本机）」一节放最前面：前提、一条命令、怎么换版本号、AppKey 怎么来；HBuilderX 云打包保留为备选。
8. 走 heavy 闸门（gradle、npm build）。commit 后报审，贴验证输出和 APK 大小。工具下载失败或版本对不上先报我，不要硬改 SDK。
