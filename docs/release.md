# 发布手机 App（Android / iOS）

本文写的是「码上刷题」App 从源码到安装包、TestFlight 测试和发新版本的完整步骤。

> 本机（CI 和开发机）只能跑到 `npm run build:app` 产出 App 资源包这一步。HBuilderX 云打包、证书申请、TestFlight 上传都需要 HBuilderX、DCloud 账号和苹果开发者账号，**没有在本环境实际执行过**，下面这些步骤按 DCloud 和苹果的官方流程整理，第一次操作时请对照各自后台的当前界面。

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
2. 准备签名证书（只做一次，之后每个版本都用同一个，换证书会导致用户无法覆盖安装）：

   ```bash
   keytool -genkeypair -v -keystore quiz-release.keystore -alias quiz \
     -keyalg RSA -keysize 2048 -validity 36500
   ```

   记下别名 `quiz`、证书密码。测试阶段也可以选 HBuilderX 的「使用公共测试证书」，但正式分发必须用自己的证书。
3. 菜单「发行 → 原生 App-云打包」：
   - 勾选 Android，包名填固定值（如 `com.richzds.quiz`，以后不能改）；
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

App 启动时（只在 App 端）调用 `GET /api/app/version?platform=android|ios`，用 `plus.runtime.version`（即 `versionName`）比较：

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
