# 06b · Android 安装包第一版：网页套壳（qzd-fe-5b1e）

用户改主意：第一版不用 DCloud，做一个 **Java 写的 Android WebView 壳**，打开线上网页版。取代 06 单子（离线 SDK 那条线停掉，已装的 Android SDK、图标、下载页 404 处理照用）。

## 定下来的
- 目录：仓库新建 `android/`（你负责），原生 Android 工程，**Java**，Gradle wrapper；SDK `/data/tools/android-sdk`，`GRADLE_USER_HOME=/data/tools/gradle-home`。
- 包名 `com.richzds.quiz`，versionName 1.0.0 / versionCode 100，应用名「码上刷题」。以后换成 uni-app 原生版时用同一个包名、同一个证书，能直接覆盖升级。
- 签名：`/data/workspace/quiz-release/keystore.env`（密码不能打印、不能进仓库，Gradle 从环境变量或本机文件读）。
- minSdk 26（Android 8，保证认 Let's Encrypt 证书），targetSdk 用当前 SDK 的最新稳定版。

## 壳要做到
1. 打开 `https://43.142.166.108/quiz/`（地址放在一个常量/资源里），开 JS、DOM Storage，登录状态（localStorage）关掉 App 再开还在。
2. 返回键：网页能后退就后退，到首页再按退出。
3. 头像上传：`<input type=file>` 能选相册（有拍照入口更好），用 `WebChromeClient.onShowFileChooser`。
4. 站外链接、下载链接交给系统浏览器；站内链接留在 App 里。
5. 没网/加载失败：显示本地的错误页和「重试」按钮，不显示 WebView 默认错误页。
6. User-Agent 末尾加 `MaShangShuaTiApp/1.0.0`；H5 里如果有“下载 App”之类的提示，在这个 UA 下隐藏（如有需要改 `app/`）。
7. 图标、启动图（用你已生成的）；状态栏颜色和网页主色一致。
8. 权限只要 INTERNET（拍照要 CAMERA 就加，不用就不加）。

## 交付
- `android/` 工程 + 一条命令出签名 APK（如 `android/build-apk.sh` → `android/dist/quiz-1.0.0.apk`），产物不进仓库。
- 验证（没有模拟器）：`apksigner verify --print-certs` SHA1 = `76:F7:C5:2A:D6:A9:D9:9E:04:B3:7B:90:2A:19:3A:D4:76:EA:D7:E8`；`aapt2 dump badging` 包名/版本/minSdk/targetSdk/权限；APK 大小；有单元测试就跑（URL 判断站内/站外的逻辑写成纯 Java 方法并测）。
- `docs/release.md`：最前面加「第一版：网页套壳安装包」一节（原理、一条命令、改版本号、证书在哪、以后怎么换成原生版）；下载页文案改成「Android 安装包」，说明 iPhone 用网页版。
- CI：加一个 android job（JDK 17 + Gradle 构建 debug APK，不签名、不需要密钥）。
- 走 heavy 闸门。commit 后报审，贴验证输出。
