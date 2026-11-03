# 资源规则

这台机器是共享的：4 核、15 GB 内存、没有交换分区，同时有别的任务在跑。

- 开工前先 `source /data/crew/qzd-5b1e/bin/env.sh`，拿到 JDK 17、Maven 3.9、npm 官方源。
- `mvn`（构建、测试）、`npm install` / `npm ci`、`npm run build`、`vue-tsc` 全量检查、`uni build` 一律包一层闸门：`heavy mvn -B -ntp test`、`heavy npm ci`。闸门保证全班同一时间只跑一个重任务，负载高时自动等。
- 开发中优先跑相关的单个测试类（`heavy mvn -B -ntp -Dtest=GradingServiceTest test`），报审前再跑一次全量。
- 不要开常驻的 watch 进程或 dev server 一直挂着；要起本地服务验证，用完就停。
- 本机没有 MySQL、Docker。后端测试用 H2（MySQL 兼容模式）；CI 上会再用真 MySQL 8 跑一遍，所以 SQL 要两边都能跑。
- 本机 Node 是 22；npm 缓存放 `/data/npm-cache`。

## 锁顺序（2026-11-02 定，防死锁）
本机还有一把全机共用的锁 `sozai-lock`（分 light / pg / heavy 三档）。两把锁的用法固定如下，任何人不得反过来：
- **重任务**（mvn、npm ci / install / build、vue-tsc 全量、uni build、Playwright 整套）：`heavy sozai-lock heavy -- <命令>`，即外层班子闸门、内层全机锁。
- **小任务**（单个 java / python / gcc / node 脚本、单个测试文件）：只用 `sozai-lock light -- <命令>`，**不要**再套 `heavy`。
- 已经持有 `sozai-lock` 的进程里，绝不再去拿 `heavy`。
- 补充（PM 问，工头定）：**单个浏览器跑单个走查脚本**算小任务，用 `sozai-lock light --`；同一个人同一时间只开一个浏览器。成套的 Playwright（`npm run test:ui` 跑多个用例或多个浏览器）仍按重任务。

## 停进程（2026-11-02 定，后端误停了产品经理的服务后）
- 只停自己启动的进程：启动时把 PID 记到自己的文件里，停的时候按这个 PID 停，并先核对 `/proc/<pid>/cwd`（必须是自己的工作树）和启动时间。
- **禁止**用 `pgrep -f`、`pkill`、`killall`、`fuser -k` 这类按名字或端口模糊匹配的方式停进程。全班有好几个同名的 `quiz-backend.jar`、`vite`、`node` 进程。
- 每个人用自己固定的端口段，避免互相抢端口：后端 18xx0、前端 15xx0、产品经理 188x1、测试 由脚本随机分配。

## 本地 MySQL（2026-11-02，工头装）
- MySQL 8.0.44，只监听 `127.0.0.1:13306`，root 无密码，仅测试用。启动 `mysql-start`、停止 `mysql-stop`、清空测试库 `mysql-reset-test`（都在 `/data/crew/qzd-5b1e/bin`，`source env.sh` 后可直接用）。工头已启动，**不要停它**，别人可能正在用。
- 后端测试跑 MySQL：先 `mysql-reset-test`，再 `SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:13306/quiz_test?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC' SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD= SPRING_DATASOURCE_DRIVER_CLASS_NAME=com.mysql.cj.jdbc.Driver heavy sozai-lock heavy -- mvn -B -ntp verify`（和 CI 的 backend-mysql 同一套）。同一时间只一个人用 quiz_test。
