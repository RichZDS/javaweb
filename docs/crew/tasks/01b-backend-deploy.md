# 01b 后端：部署包、选项打乱、性能实测 · assignee: qzd-be-5b1e · 状态: doing

## 现场 / 锚点
- 后端 M1–M3 已全部合入 main（596eaa1）。部署目标是一台 Ubuntu 24.04 服务器：4 核、3.6 GB 内存（可用约 1.9 GB）、已有 Docker 和 docker compose v5。80/443 被另一个项目的 Caddy 占用，我们的服务只监听本机 `127.0.0.1:18090`，由那边的 Caddy 把 `https://<服务器IP>/quiz/` 转发过来（Caddy 的修改由工头做，不在本单范围）。
- 你**不连服务器**，只在仓库里写部署文件，并在本机验证能用的部分（本机没有 Docker，见「测试」）。

## 做法

### D1 部署包（`deploy/` 目录，先交）
- `deploy/docker-compose.yml`，项目名 `quiz`，三个服务，全部 `restart: unless-stopped`，只用内部网络，不对外开端口（除了 web 的 `127.0.0.1:18090:80`）：
  - `db`：`mysql:8.0`，数据卷 `quiz-mysql`；为小内存调参（`innodb_buffer_pool_size=128M`、`performance_schema=OFF`、`max_connections=50` 等），`mem_limit: 512m`；utf8mb4；健康检查。
  - `backend`：`eclipse-temurin:17-jre`，挂载 `deploy/app/quiz-backend.jar` 只读运行（不在服务器上构建）；`SPRING_PROFILES_ACTIVE=prod`；JVM `-Xms128m -Xmx384m`，`mem_limit: 640m`；上传目录用数据卷 `quiz-uploads`；等 db 健康后再起；健康检查走 `/api/app/version`。
  - `web`：`nginx:alpine`，`mem_limit: 64m`。配置 `deploy/nginx.conf`：`/quiz/` 下 —— `/quiz/api/` 和 `/quiz/uploads/` 反代到 backend（去掉 `/quiz` 前缀）；`/quiz/admin/` 是管理后台静态文件（SPA 回退到 index.html）；`/quiz/download/` 放 Android 安装包和一个下载页；其余 `/quiz/` 是 H5 版 App 静态文件（SPA 回退）。开 gzip，静态资源加缓存头，index.html 不缓存；`client_max_body_size 6m`；转发 `X-Forwarded-*`。
- `deploy/.env.example`：列出所有环境变量和说明，密码类全部是占位符。后端读 `X-Forwarded-Proto` 等头（`server.forward-headers-strategy=framework`）。
- `deploy/deploy.sh`（在**本机**跑）：读 `deploy/.env.deploy`（不进仓库，含服务器地址），本地打 jar、构建 admin 和 H5、打包 `deploy/` 需要的文件，用 rsync / scp 传到服务器的 `~/quiz-javaweb/`，然后远程 `docker compose up -d`，最后用 curl 检查健康接口。脚本支持 `--dry-run` 只打印将要做的事。不在脚本里写任何密码。
- `deploy/backup.sh`：在服务器上用 `mysqldump` 导出到 `~/quiz-javaweb/backups/`，保留最近 7 份；`deploy/README.md` 写清首次部署、更新、回滚、备份恢复的步骤，以及首次部署怎么设置 `APP_SEED_ENABLED=true` 导入题库、之后改回 false。
- `.gitignore` 加上 `deploy/.env.deploy`、`deploy/app/`、`deploy/backups/`（`.gitignore` 你可以改这几行）。

### D2 选项打乱（第三批，后端部分）
- 开练习时新参数 `shuffleOptions: boolean`（默认 false）；开考由试卷字段 `shuffleOptions`（后台可设，默认 false）决定。为真时，每道单选、多选题在这一场里随机排一次选项顺序，并固定下来（存进场次）；返回给客户端的选项按新顺序重新标 A、B、C…，客户端提交的也是新标签，后端映射回原选项再判分；解析里引用的选项字母保持原文不变，所以要在返回的题目里加 `optionMapping: {"A":"C",…}`（新标签 → 原标签），前端在解析旁注明“原选项 C”。判断、填空不打乱。
- 先在 `docs/api-proposal-shuffle.md` 写清字段和行为，发工头确认后再写代码（不要直接改 api.md）。

### D3 性能实测（给论文用）
- 写一个只在测试里用的数据生成器：1 万道题、200 个用户、10 万条作答记录（H2，测试配置）。
- 测这几个接口的耗时（各跑 50 次，取中位数和 95 分位）：分类树、开随机练习（20 题）、提交答案、统计总览、统计按日、薄弱点、开考、交卷结算。
- 结果写进 `docs/performance.md`：机器配置、数据量、每个接口的中位数和 95 分位、是否达到 0.5 秒目标、没达到的原因和改进（比如加索引）。如果改了索引，用新的迁移脚本。
- 说明这是 H2 上的测试数，不代表服务器上 MySQL 的数字；服务器上的实测部署后由工头补。

## 明令不做
- 不连接服务器，不在仓库里写任何真实地址、密码、密钥。
- 不改 Caddy 配置、不碰服务器上别的项目。
- D2 未经工头确认不改 `docs/api.md`、不动前端。

## 测试
- D1：本机没有 Docker，用 `docker compose config` 的等价检查做不到时，至少：nginx 配置用 `nginx -t` 能检查就检查（本机没有 nginx 时写明没检查）；`deploy.sh --dry-run` 跑通并贴输出；`bash -n` 检查所有脚本语法；prod 配置用 H2 模拟一次启动（缺环境变量时报错信息清楚、齐全时能起来）。
- D2：单元测试覆盖映射正确、同一场固定顺序、判分映射回原选项、未打乱时行为不变。
- D3：贴 `docs/performance.md` 的表和生成命令。

## 交付
- D1、D2（提案确认后）、D3 分别单独 commit，各自报审。D1 先交。
