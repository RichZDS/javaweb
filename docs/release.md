# 发布手机 App（Android / iOS）

现在对外发的是**第一版：网页套壳安装包**（Android，下一节）。iPhone 用户直接用网页版。

第 1–6 节是以后换成 uni-app 原生版（HBuilderX 云打包、TestFlight）的步骤，先留作备选。

## 第一版：网页套壳安装包（Android）

### 原理

`android/` 是一个 Java 写的原生 Android 工程，整个 App 只有一个全屏 WebView，打开线上网页版 `https://43.142.166.108/quiz/`。

- 网页版发版（H5 部署）后，套壳 App 里马上就是新内容，**不用发新安装包**。只有改了套壳本身（地址、图标、壳的功能）才要发新包。
- 壳做的事：
  - 返回键：网页能后退就后退，退到首页再按就退出。
  - 头像上传：网页的文件框能选相册，也能直接拍照（用系统相机应用，所以不需要相机权限）。
  - 链接：站外链接、下载页、安装包、管理后台交给系统浏览器，站内的留在 App 里。规则在 `UrlPolicy.java`，有单元测试。
  - 没网或打不开：显示本地的错误页和「重试」。证书出错一律不继续加载。
  - User-Agent 末尾加 `MaShangShuaTiApp/<版本号>`。网页版认出这个标记后会隐藏「学习提醒」（壳里没有本机通知），设置页「检查更新」比的是安装包的版本，「去更新」交给系统浏览器下载。
  - 登录状态存在 WebView 的 localStorage 里，关掉 App 再开还在。
- 包名 `com.richzds.quiz`，minSdk 26（Android 8，认 Let's Encrypt 证书），targetSdk 37。申请的权限只有 `INTERNET`；另外 androidx.core 自带一个 signature 级的内部权限 `com.richzds.quiz.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`，用户看不到，保留它是为了 Android 8–12 上依赖库注册不导出的广播时不抛异常。
- 图标是自适应矢量图标（主色底，白色「< ✓ >」），启动图是主色底加同一个图案，状态栏是网页主色 `#3A6CF4`。

### 前提（本机已装好）

| 东西 | 位置 |
| --- | --- |
| JDK 17 | `source /data/crew/qzd-5b1e/bin/env.sh` 后就有 |
| Android SDK（`platforms;android-37.0`、`build-tools;36.0.0`） | `/data/tools/android-sdk`（`ANDROID_HOME`） |
| Gradle 9.6.0 | 工程自带 wrapper（`android/gradlew`），第一次运行自动下载，缓存在 `/data/tools/gradle-home`（`GRADLE_USER_HOME`） |
| 签名证书 | `/data/workspace/quiz-release/quiz-release.keystore`，别名和密码在同目录 `keystore.env`（权限 600） |

换一台机器时：装 JDK 17 和 Android 命令行工具，用 `sdkmanager "platforms;android-37.0" "build-tools;36.0.0"` 装平台。签名配置文件用 `QUIZ_KEY_ENV=<路径>` 指过去，格式是四行 `KEYSTORE=`、`ALIAS=`、`STORE_PASSWORD=`、`KEY_PASSWORD=`。

### 一条命令出安装包

```bash
source /data/crew/qzd-5b1e/bin/env.sh
heavy sozai-lock heavy -- android/build-apk.sh   # 本机按资源规则走闸门；别的机器直接 android/build-apk.sh
# 产物：android/dist/quiz-<versionName>.apk（不进仓库）
```

脚本按顺序做这几件事：
1. 跑单元测试；
2. 打 release 包（R8 压缩，只有 0.1 MB 左右）；
3. 用 `keystore.env` 里的证书签名；
4. 用 `apksigner` 校验，打印证书指纹和包大小。

证书 SHA-1 必须是 `76:F7:C5:2A:D6:A9:D9:9E:04:B3:7B:90:2A:19:3A:D4:76:EA:D7:E8`。密码只经过环境变量交给 Gradle，脚本不打印，也不写进任何文件。

自己核对：

```bash
BT=/data/tools/android-sdk/build-tools/36.0.0
$BT/apksigner verify --print-certs android/dist/quiz-1.0.0.apk   # 证书指纹
$BT/aapt2 dump badging android/dist/quiz-1.0.0.apk | head -4      # 包名、版本、minSdk、targetSdk、权限
```

### 改版本号

改 `android/gradle.properties` 里的两行，`quizVersionCode` 每次都要比上一版大，否则手机上装不上（不能覆盖安装）：

```properties
quizVersionName=1.0.1
quizVersionCode=101
```

User-Agent 里的版本号跟着 `quizVersionName` 变。网页版地址也在这个文件里（`quizHomeUrl`）。

### 证书

- 证书和密码**不进仓库**（根目录 `.gitignore` 忽略了 `*.keystore`、`*.jks`）。
- 证书丢了，以后的新版本就不能覆盖安装旧版，用户只能卸载重装，本地数据全丢。所以要把 `quiz-release.keystore` 和 `keystore.env` 另外备份一份（密码管理器或离线介质）。

### 放到下载页

```bash
APK_PATH=android/dist/quiz-1.0.0.apk deploy/deploy.sh   # 安装包会放到服务器的 www/download/quiz.apk
```

- 下载页 `/quiz/download/` 会先用 HEAD 请求确认安装包在：在的话显示「下载 Android 安装包」，不在（404）就显示灰色的「安装包准备中」，不会给一个 404 链接。
- 这次部署没给 `APK_PATH` 时，服务器上已有的安装包保留不动。
- 想让旧版本的套壳提示更新：后端设 `APP_RELEASE_ANDROID_LATEST_VERSION`、`APP_RELEASE_ANDROID_DOWNLOAD_URL`（如 `https://43.142.166.108/quiz/download/quiz.apk`）后重启，见第 5 节。顺序同样是先放包、再改后端。

### 真机检查（本机没有模拟器，下面这些要在手机上过一遍）

- [ ] 安装、打开有启动图，进入登录页。登录后从最近任务里划掉 App 再打开，仍是登录状态。
- [ ] 返回键：进几层页面后能一层层退回；在首页再按返回就退出 App。
- [ ] 「我的」换头像：能从相册选，也能拍照，上传成功。
- [ ] 设置页没有「学习提醒」；版本号显示安装包版本；「检查更新」正常提示。
- [ ] 开飞行模式再打开 App：显示「网络连接失败」和「重试」。关掉飞行模式点「重试」，能进入。
- [ ] 下载页、外部链接在系统浏览器里打开。
- [ ] 状态栏是蓝色、图标是白色；系统深色模式下底部导航栏条变成深色。

### 以后换成 uni-app 原生版

用同一个包名 `com.richzds.quiz`、同一个证书，`versionCode` 比套壳的最后一版大，用户就能直接覆盖安装，不用卸载。建议套壳用 100–199，原生版从 200 起。

覆盖后登录状态不会带过去：套壳的登录令牌在 WebView 的存储里，原生版另存一份，所以用户要重新登录一次，题目记录都在服务器上，不受影响。

原生版的打包步骤见下面第 1–6 节。

### CI

`.github/workflows/ci.yml` 的 `android` 任务用 JDK 17 跑单元测试，再打一个**不签名的 debug 包**（不需要任何密钥），作为构建产物上传。正式包只在本机用 `build-apk.sh` 出。

---

> 下面第 1–6 节是 uni-app 原生版的步骤。本机（CI 和开发机）只能跑到 `npm run build:app` 产出 App 资源包这一步。HBuilderX 云打包、证书申请、TestFlight 上传都需要 HBuilderX、DCloud 账号和苹果开发者账号，**没有在本环境实际执行过**，这些步骤按 DCloud 和苹果的官方流程整理，第一次操作时请对照各自后台的当前界面。
>
> 原生版和套壳是同一个应用：包名必须是 `com.richzds.quiz`，Android 证书用上面那份 `quiz-release.keystore`（HBuilderX「使用自有证书」），不要另外生成一份。

## 1. 先改好的配置

| 位置 | 字段 | 说明 |
| --- | --- | --- |
| `app/src/manifest.json` | `appid` | 仓库里是占位 `__UNI__REPLACE_ME`。在 HBuilderX 登录 DCloud 账号后，打开 manifest 的「基础配置」点「重新获取」得到自己的 appid（形如 `__UNI__1A2B3C4`）。不要把别人的 appid 提交进仓库。 |
| `app/src/manifest.json` | `versionName` / `versionCode` | 对外版本号 / 内部递增整数。每次发版都要一起加：`1.0.1` 对应 `101`，`1.1.0` 对应 `110`。 |
| `app/.env.app` | `VITE_API_BASE` | 安装包里的接口地址，目前是 `https://43.142.166.108/quiz/api`。**必须是 HTTPS 且证书受信任**（见第 6 节）。 |
| 后端环境变量 | `APP_RELEASE_<ANDROID/IOS>_*` | 版本检查用，见第 5 节。 |

证书、密钥库、描述文件（`*.keystore`、`*.jks`、`*.p12`、`*.mobileprovision`）一律不进仓库，根目录 `.gitignore` 已经忽略。它们和密码单独保存在发版人自己的电脑或密码管理器里。

## 2. 出 App 资源包

```bash
cd app
npm ci
npm run build:app          # uni build -p app --mode app，读取 .env.app
# 产物：app/dist/build/app（manifest.json、app-service.js、pages、static…）
```

- 一定要用 `npm run build:app`（`--mode app`）。如果直接在 HBuilderX 里打开 `app/` 源码目录发行，它按 `production` 模式编译，会用 `.env.production` 里的相对地址 `/quiz/api`，安装包里请求不到后端。
- 产物目录可以直接导入 HBuilderX：「文件 → 导入 → 从本地目录导入」，选 `app/dist/build/app`。

## 3. Android 安装包（HBuilderX 云打包）

1. 安装最新正式版 HBuilderX，登录 DCloud 账号，导入上一步的 `app/dist/build/app`。
2. 签名证书：用套壳已经在用的 `/data/workspace/quiz-release/quiz-release.keystore`（别名、密码在同目录 `keystore.env`），**不要重新生成**。换证书会导致已经装了套壳的用户无法覆盖安装。
3. 菜单「发行 → 原生 App-云打包」：
   - 勾选 Android，包名填 `com.richzds.quiz`（和套壳一致，以后不能改）；
   - 证书选「使用自有证书」，填别名、密码，选择 `.keystore` 文件；
   - 渠道包不勾，「打正式包」；
   - 提交后在控制台看排队和打包进度，完成后给出 `.apk` 下载地址。
4. 真机安装验证：登录、刷题、考试（含断网后恢复）、夜间模式、学习提醒、检查更新。
5. 把 `.apk` 放到能下载的地址（如部署服务器的 `/quiz/download/quiz-1.0.0.apk`），这个地址在第 5 节填给后端。

Android 权限只申请了网络状态、Wi-Fi 状态、相机（拍照换头像）、振动，见 `manifest.json` 的 `app-plus.distribute.android.permissions`；没有接入任何推送、统计 SDK。

## 4. iOS：证书、描述文件和 TestFlight

### 4.1 需要的账号

- 苹果开发者计划账号（个人或公司，年费）。没有它不能真机安装正式包，也不能上 TestFlight。
- App Store Connect 里能管理 App 的角色（账号持有人、管理或 App 管理）。

### 4.2 证书（.p12）

在 [Apple Developer → Certificates](https://developer.apple.com/account/resources/certificates/list) 申请 **Apple Distribution** 证书。需要一个 CSR 文件：

- 有 Mac：钥匙串访问 →「证书助理 → 从证书颁发机构请求证书」，存到磁盘。
- 没有 Mac：用 openssl 生成，

  ```bash
  openssl genrsa -out ios_dist.key 2048
  openssl req -new -key ios_dist.key -out ios_dist.csr -subj "/emailAddress=你的邮箱/CN=码上刷题/C=CN"
  ```

上传 CSR，下载得到 `distribution.cer`，再转成 HBuilderX 要的 `.p12`：

```bash
openssl x509 -inform DER -in distribution.cer -out ios_dist.pem
openssl pkcs12 -export -inkey ios_dist.key -in ios_dist.pem -out ios_dist.p12   # 设置导出密码，打包时要填
```

（用钥匙串申请的，直接在钥匙串里右键证书「导出」为 `.p12`。）

### 4.3 App ID 和描述文件（.mobileprovision）

1. Identifiers → 新建 App ID，Bundle ID 用明确值（如 `com.richzds.quiz`，和打包时一致，以后不能改）。不需要勾选推送等额外能力。
2. Profiles → 新建描述文件：
   - 上 TestFlight / App Store：类型选 **App Store Connect**；
   - 只给登记过 UDID 的几台测试机直接装：类型选 **Ad Hoc**，并勾选这些设备。
3. 选上面的 App ID 和 Distribution 证书，下载 `.mobileprovision`。

### 4.4 云打包

HBuilderX「发行 → 原生 App-云打包」，勾选 iOS：

- Bundle ID：与 App ID 一致；
- 证书私钥文件：`ios_dist.p12`，填导出密码；
- 证书 profile 文件：上一步的 `.mobileprovision`；
- 选「打正式包」，完成后下载 `.ipa`。

`manifest.json` 里已经写了相册、相机的用途说明（`NSPhotoLibraryUsageDescription`、`NSCameraUsageDescription`），审核要求必须有。

### 4.5 TestFlight 分发

1. [App Store Connect](https://appstoreconnect.apple.com) →「我的 App → 新建 App」：平台 iOS，名称「码上刷题」，套装 ID 选上面的 Bundle ID，SKU 任填（如 `quiz-app`）。
2. 上传 `.ipa`：Mac 上用苹果的 **Transporter** 应用登录开发者账号后拖入 `.ipa` 交付。上传后在 App Store Connect 的 TestFlight 页等待「处理中」结束（一般几分钟到一小时）。
3. 出口合规：App 只用 HTTPS 等系统标准加密，问卷选「使用了加密，但属于豁免范围」。
4. 内部测试：「内部测试」组里加 App Store Connect 团队成员（最多 100 人），他们在 iPhone 上装 TestFlight App 后即可安装，不需要审核。
5. 外部测试：新建外部测试组，填写测试信息，第一个构建版本要过 Beta 版 App 审核；通过后可以用邮箱邀请，或开启公开链接。
6. 每个构建版本 90 天后过期，需要上传新构建。

上架 App Store 是在同一个 App 记录里填写商店信息、截图、隐私问卷后「提交以供审核」，不在本文范围；没有真实的审核通过记录前，不要把上架记为完成。

## 5. 发新版本和 App 内更新提示

App 启动时（App 端和 Android 套壳）调用 `GET /api/app/version?platform=android|ios`，用 `plus.runtime.version`（即 `versionName`）比较：

| 情况 | App 的表现 |
| --- | --- |
| 当前版本 < `minVersion` | 弹窗「需要更新后才能继续使用」，没有取消按钮 |
| 当前版本 < `latestVersion` | 弹窗「发现新版本 x.y.z」+ 更新说明，可以「以后再说」 |
| 其他 | 不提示；设置页「检查更新」会提示「已是最新版本」 |

点「去更新」用系统浏览器打开 `downloadUrl`：Android 填 `.apk` 下载地址，iOS 填 TestFlight 公开链接或 App Store 地址。比较逻辑在 `app/src/logic/version.ts`，有单元测试。

发一个新版本的顺序：

1. 改 `app/src/manifest.json` 的 `versionName`、`versionCode`（两个都要加），提交。
2. `npm run build:app`，按第 3、4 节打包、真机验证。
3. Android：把新 `.apk` 放到下载地址；iOS：上传新构建到 TestFlight（或提交审核）。
4. **确认新包能下载、能安装之后**，再改后端环境变量并重启后端：

   ```bash
   APP_RELEASE_ANDROID_LATEST_VERSION=1.0.1
   APP_RELEASE_ANDROID_MIN_VERSION=1.0.0        # 只有旧版本必须淘汰时才调高
   APP_RELEASE_ANDROID_DOWNLOAD_URL=https://<域名或IP>/quiz/download/quiz-1.0.1.apk
   APP_RELEASE_ANDROID_NOTES=修复了考试断网后的同步提示
   # iOS 同理：APP_RELEASE_IOS_LATEST_VERSION、..._MIN_VERSION、..._DOWNLOAD_URL、..._NOTES
   ```

5. 用旧版本 App 冷启动一次，确认弹出更新提示、链接可用；用新版本启动确认不再提示。

先改后端再放包会让用户点到打不开的链接，所以顺序不能反。

## 6. 发布前检查清单

- [ ] `manifest.json` 的 `appid` 已换成自己的，`versionName` / `versionCode` 已递增。
- [ ] 用 `npm run build:app` 出包，`.env.app` 的接口地址正确。
- [ ] 接口是 HTTPS，且证书被手机信任：Android 9+ 默认禁止明文 HTTP，iOS 的 ATS 要求受信任的 TLS 证书。现在的地址是 IP，需要给这个 IP 签发的受信任证书（不少 CA 不签 IP 证书），更稳妥的是换成域名 + 正式证书，再改 `.env.app`。自签名证书在手机上会直接请求失败。
- [ ] 真机走一遍：注册登录、选目标考试、刷题看解析、错题本、考试（断网作答后恢复、到时自动交卷）、统计图表、夜间模式和字号、学习提醒（设一个两分钟后的时间，退到后台等通知）、检查更新。
- [ ] **真机确认学习提醒能弹出**：设置页打开学习提醒，时间设成两分钟后，把 App 退到后台等通知。不弹就检查打包模块：在 HBuilderX 的 App 模块配置里只勾系统自带的推送（Push）模块，不填任何厂商参数。学习提醒只用本机通知（`plus.push.createMessage`），不经过任何推送服务器，不算接入第三方推送。
- [ ] 证书、密钥库、描述文件和密码没有进仓库。
