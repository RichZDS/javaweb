# 交接 · 码上刷题（在线刷题学习 App）· 2026-11-03

## 现状

三批功能全部做完并上线，线上有 300 个演示学员、4 个月的刷题记录；Android 第一版安装包（网页套壳）已上下载页。main 的 CI 六个任务全绿。
还没做的只有：真机装安装包走一遍、iOS 安装包、uni-app 原生版安装包（提醒和检查更新要靠它）。

## 地址和账号（本文不写任何密码）

| 用途 | 地址 / 位置 |
| --- | --- |
| 网页版（手机浏览器） | https://43.142.166.108/quiz/ |
| 管理后台 | https://43.142.166.108/quiz/admin/ ，账号 `admin`，密码在服务器 `~/quiz-javaweb/.env` 的 `ADMIN_PASSWORD` |
| Android 安装包 | https://43.142.166.108/quiz/download/ （`quiz.apk`，1.0.0） |
| 演示学员 | 300 个，统一密码已交给用户（不在仓库、不在服务器文件里）；推荐 `citrus_`、`shuyan_yang746`、`liu_chengyu`、`ziqian_zhao2002`、`jason_shen`、`claraliao` |
| 代码 | github.com/RichZDS/javaweb（公开），本地 `/data/workspace/javaweb` |
| 签名证书 | 本机 `/data/workspace/quiz-release/`（`quiz-release.keystore` + `keystore.env`，600）。**丢了就没法覆盖升级 App**，用户需自己另存一份 |

## 仓库

| 目录 | 内容 |
| --- | --- |
| `backend/` | Java 17 + Spring Boot 3.5 + MyBatis-Plus + Flyway（V1–V3）+ MySQL 8；`seed/` 示例题库 396 题，`mock/` 演示数据 |
| `app/` | uni-app（Vue 3 + TS），H5 和 App 资源同一套代码 |
| `admin/` | Vue 3 + Element Plus 管理后台 |
| `android/` | Java 写的 WebView 壳，`android/build-apk.sh` 出签名包 |
| `deploy/` | docker compose（db / backend / web）、nginx、`deploy.sh`、`backup.sh`、性能脚本 `perf/` |
| `e2e/` | 测试的接口 / 界面 / 演示数据核对脚本 |
| `docs/` | 见下 |

文档：`spec.md`（功能）、`api.md`（接口唯一依据，0.1–0.4 节是各阶段定稿）、`seed-format.md`、`mock-data.md`（演示数据约定 + 怎么跑）、`release.md`（安装包）、`performance.md`（H2 和服务器实测）、`acceptance.md`（验收清单和终验）、`test-report.md`（各轮测试）、`deploy/README.md`（部署、回滚、备份恢复）。

## 服务器

- 腾讯云上海，4 核 3.7 GB。部署目录 `~/quiz-javaweb`：`.env`（600，数据库 / JWT / 管理员密码）、`app/`、`www/`、`nginx/`、`backups/`、`releases/`（最近 3 版）。
- 80/443 是另一个项目 course-labs 的 Caddy 容器（`/home/ubuntu/course-labs/caddy/Caddyfile`）。我们只加了一行 `reverse_proxy /quiz* 127.0.0.1:18090`，原文件备份 `Caddyfile.bak-quiz-20261102-202342`。没有新开端口。
- 容器：`quiz-db-1`（512 MB）、`quiz-backend-1`（640 MB，`-Xmx384m`）、`quiz-web-1`（nginx，127.0.0.1:18090）。
- 发版：本机 `cd /data/workspace/javaweb && bash deploy/deploy.sh`（读 `deploy/.env.deploy`，gitignore）。带安装包：`APK_PATH=<apk> bash deploy/deploy.sh`；不带时服务器上已有的 `quiz.apk` 会保留。
- 登录服务器：`deploy/.env.deploy` 用 sshpass 读一个临时密码文件，**交接时已删除**（密码不留在开发机）。下次部署先向用户要服务器密码，写进 `DEPLOY_SSH_PREFIX` 指的文件（chmod 600），用完删掉；或者请用户允许加一把 SSH 公钥。
- 备份：用户定了**不设定时任务、手动备份**：服务器上 `bash ~/quiz-javaweb/backup.sh`，恢复见 `deploy/README.md`。现有备份：`backups/quiz-20261103-120450.sql.gz`（灌演示数据前，只有题库和管理员）、更早的预发布库。
- `APP_RELEASE_ANDROID_*`（`.env`）控制 `/api/app/version` 和下载页；下载地址要指向 `…/quiz/download/quiz.apk`，不是下载页本身。

## 演示数据

- 产品经理写数据（`backend/src/main/resources/mock/`），后端的模拟器按天推进时钟、调用真实业务代码生成全部记录，所以统计、错题、打卡、成绩都一致。约定和运行方法见 `docs/mock-data.md`。
- 线上 2026-11-03 12:05–12:27 跑的（jar `de7e580` 那版逻辑，部署 `f2d7308`）：作答 197,657、练习 16,822、考试 1,051、错题 24,586、收藏 4,868、纠错 249、题 486、卷 12。
- **只能在只有示例题库和管理员的空库上跑**。要重灌：备份 → 按 `deploy/README.md` 清库重建 → 停后端 → `docker compose run` 跑模拟器（`docs/mock-data.md` 7.3）→ 起后端。跑一次约 25 分钟。

## 安装包

- 1.0.0 是 WebView 壳，打开线上网页版；没有学习提醒和 App 内检查更新（网页版在壳里自动隐藏这两项）。包名 `com.richzds.quiz`，minSdk 26，110 KB。
- 改版本：`android/gradle.properties` 的版本号 → `android/build-apk.sh` → `APK_PATH=… bash deploy/deploy.sh` → 改服务器 `.env` 的 `APP_RELEASE_ANDROID_LATEST_VERSION` / `NOTES` 并重启后端。
- 以后换 uni-app 原生版：同包名、同证书就能覆盖升级。需要用户注册 DCloud、建应用、按包名 + SHA1 申请离线打包 Key，并从网盘下 Android 离线 SDK（5.34.2026102909，只有百度云 / 和彩云），或者在自己电脑上用 HBuilderX 云打包。步骤见 `docs/release.md`。
- iOS：需要苹果开发者账号（每年 99 美元）+ TestFlight / 审核；现在 iPhone 用网页版（下载页有说明）。

## 待办

1. 用户用 Android 真机装 `quiz.apk` 走一遍（登录、刷题、头像上传、返回键、断网重试）。本机没有模拟器，没真机验过。
2. iOS 安装包、uni-app 原生版：等用户的账号。
3. 产品经理终验里 142 条用例有 119 条标「没测」（多数由测试的自动化覆盖，但没逐条对表）；答辩前如需完整验收表，可按 `docs/acceptance.md` 补走。
4. 本机临时库可清：MySQL 13306（`/data/tools/mysql-8.0`，数据 `/data/crew/qzd-5b1e/mysql`，1.2 GB）里的 `quiz_test`、`quiz_mock`、`quiz_mock_v2`、`quiz_mock_check`。班子解散时已停库，数据目录还在；要再用就 `bash /data/crew/qzd-5b1e/bin/mysql-start`。
5. 本机工具留着以后打包用：`/data/tools/android-sdk`、`/data/tools/gradle-home`、`/data/tools/jdk-17`、`/data/tools/maven`。

## 坑

- 本机在新加坡、服务器在上海，跨境下载约 16 KB/s：本机测线上页面会很慢，不是应用问题；性能要在服务器本机测（`deploy/perf/`）。本机有 HTTP 代理，访问服务器 IP 要 `--noproxy '*'`，Playwright 要去掉 `*_proxy` 环境变量。
- `ssh … 'bash -s' < 脚本` 时，脚本里的 `docker compose exec/run` 会吃掉标准输入，后面的步骤不执行：每条都加 `</dev/null`。长任务在服务器上用 `nohup` 跑、本机轮询。
- `docker compose run` 继承后端的 640 MB 限制；模拟器用 `-Xmx320m -XX:+UseSerialGC` 跑得下。
- 测试里改数据源要四项一起钉（url、driver、用户名、密码），否则 CI 的 MySQL 任务会被环境变量带成 MySQL 驱动。
- 微信内置浏览器下载不了 APK，要用手机自带浏览器。

## 班子

班子 qzd-5b1e（工头 + 后端 / 前端 Claude，产品经理 / 测试 GPT）已解散。任务单、交接、工作日志归档在 `docs/crew/`（摘要）和本机 `/data/crew/archive/qzd-5b1e-20261103.tgz`（全量）。
