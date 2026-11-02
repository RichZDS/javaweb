# 部署

一台 Ubuntu 服务器上用 docker compose 跑三个容器：`db`（MySQL 8.0）、`backend`（Java 17 跑现成的 jar）、`web`（Nginx）。
只有 `web` 在本机 `127.0.0.1:18090` 开端口；服务器上已有的 Caddy 把 `https://<服务器>/quiz/` 转发到这里。

```
浏览器 / App ──https──> Caddy（80/443，另一个项目的）──> 127.0.0.1:18090  web(Nginx)
                                                         ├─ /quiz/api/、/quiz/uploads/ ─> backend:8080（去掉 /quiz）
                                                         ├─ /quiz/admin/   管理后台静态文件
                                                         ├─ /quiz/download/ 安装包和下载页
                                                         └─ /quiz/         H5 版 App
                                       backend ──> db:3306（内部网络，不通外网）
```

| 文件 | 作用 |
| --- | --- |
| `docker-compose.yml` | 三个服务、内部网络、数据卷 `quiz-mysql` / `quiz-uploads`、内存上限（512m / 640m / 64m） |
| `nginx.conf` | 部署后是 `nginx/default.conf`：路径转发、SPA 回退、gzip、缓存头、上传大小 6 MB |
| `.env.example` | 服务器上 `.env` 的模板（数据库密码、JWT 密钥、管理员、版本信息） |
| `env.deploy.example` | 本机 `deploy/.env.deploy` 的模板（SSH 目标、目录、安装包路径） |
| `deploy.sh` | 本机运行：构建、打包、传到服务器、启动、健康检查；`--dry-run` 只打印 |
| `backup.sh` | 服务器上运行：`mysqldump` 到 `backups/`，保留 7 份 |
| `download/index.html` | 安装包下载页 |

服务器上的目录 `~/quiz-javaweb/` 部署后长这样：`docker-compose.yml`、`.env`（手写，不会被覆盖）、`nginx/`、`app/quiz-backend.jar`、
`www/`（H5，`www/admin/` 后台，`www/download/` 下载页和 `quiz.apk`）、`backup.sh`、`backups/`、`releases/`。

## 前提

- 服务器：Docker 和 `docker compose`（v2 起支持 `--wait`）、`curl`；SSH 用密钥登录。
- 本机：JDK 17、Maven、Node 22、`rsync`、`ssh`。`deploy.sh` 在本机构建，服务器上不编译。
- Caddy（工头负责）要**保留 `/quiz` 前缀**转发，例如 `handle /quiz/* { reverse_proxy 127.0.0.1:18090 }`，
  不要用会去掉前缀的 `handle_path`；并带上 `X-Forwarded-Proto`（Caddy 默认会带）。后端按
  `server.forward-headers-strategy=framework` 读取这些头。

## 首次部署

1. 本机：`cp deploy/env.deploy.example deploy/.env.deploy`，填 `DEPLOY_HOST` 等（这个文件不进仓库）。
2. 服务器：`mkdir -p ~/quiz-javaweb`，把 `.env.example` 复制成 `~/quiz-javaweb/.env` 填好真实值，`chmod 600 .env`。
   首次部署把 `APP_SEED_ENABLED=true`，用来导入示例题库（396 题、6 份卷）。
3. 本机先看一遍要做什么：`deploy/deploy.sh --dry-run`，再正式执行：`deploy/deploy.sh`。
   脚本会依次：打 jar（含测试）、构建 H5 和管理后台、整理文件、在服务器 `releases/` 存一份旧版本、
   `rsync` 同步（不碰 `.env`、`backups/`、`releases/`）、等数据库健康后重建 `backend` 和 `web`、轮询健康接口。
4. 确认导入成功：`docker compose logs backend | grep 示例题库导入完成`，应看到 `396 道题，6 份试卷`。
5. 把服务器 `.env` 里的 `APP_SEED_ENABLED` 改回 `false`，执行 `docker compose up -d backend` 让它生效
   （题库只在分类表为空时导入，不改回也不会重复导入，改回是为了以后清库时不会意外导入）。
6. 用 `.env` 里的管理员账号登录 `/quiz/admin/`，第一次登录后建议改密码。

## 更新

本机直接再跑一次 `deploy/deploy.sh`。数据库结构变化由 Flyway 在后端启动时自动迁移（`V1__init.sql`、`V2__…`）。
更新前建议先在服务器上手动跑一次 `./backup.sh`。

## 回滚

`deploy.sh` 每次同步前把当时的 `docker-compose.yml`、`nginx/`、`app/`、`www/` 打包到 `releases/<时间>.tgz`（保留 3 份）。

```bash
cd ~/quiz-javaweb
ls -1t releases/                       # 找到要回去的版本
tar xzf releases/20261102-153000.tgz   # 覆盖回旧文件（.env、backups 不受影响）
docker compose up -d --force-recreate backend web
```

Flyway 迁移只会往前走：如果新版本加过表或列（目前的迁移都是只加不删），旧 jar 照样能跑；
如果回滚后报数据库版本相关的错，用下面的备份恢复到更新前的库。

## 备份和恢复

```bash
cd ~/quiz-javaweb
./backup.sh                            # 生成 backups/quiz-<时间>.sql.gz，只保留最近 7 份
crontab -e                             # 每天凌晨 3 点自动备份：
# 0 3 * * * ~/quiz-javaweb/backup.sh >> ~/quiz-javaweb/backups/backup.log 2>&1
```

恢复（会覆盖当前数据，先停后端）：

```bash
cd ~/quiz-javaweb
docker compose stop backend
gunzip -c backups/quiz-20261102-030000.sql.gz | \
  docker compose exec -T db sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$MYSQL_DATABASE"'
docker compose start backend
```

上传的图片在数据卷 `quiz-uploads` 里，`backup.sh` 不备份它；需要时：
`docker run --rm -v quiz-uploads:/data -v "$PWD/backups":/b alpine tar czf /b/uploads-$(date +%F).tgz -C /data .`

## 常用排查

```bash
docker compose ps                      # 三个容器都应是 healthy / running
docker compose logs --tail 200 backend # 启动失败时看这里，缺环境变量会一次列出缺哪些
curl -s http://127.0.0.1:18090/quiz/api/app/version?platform=android
```

内存：MySQL 限 512 MB（缓冲池 128 MB、关闭 performance_schema、最多 50 个连接），
后端限 640 MB（堆 128–384 MB、SerialGC、Tomcat 最多 50 个线程），Nginx 限 64 MB，合计约 1.2 GB。
