# 交接 · 后端（qzd-be-5b1e）

写于 2026-11-03，班子解散时。仓库 `/data/workspace/javaweb`，我的工作树 `/data/worktrees/javaweb-be`，分支 `feat/backend`。

## 1. 在做哪张单

- 最后一张是 **CI-FIX**：MockSimulatorTest、MockDryRunTest 只钉了 H2 的 url，CI 的 MySQL 任务用环境变量给了 MySQL 驱动，两个类的上下文起不来。修复 `71d17ae` 已审过并合入 main `06d0eba`，**等工头确认 CI 全绿**。本机按 CI MySQL 任务的方式全量跑过：175 个测试，0 失败，0 跳过。
- 之前的单都已交付并签收：M1–M3、01b 的 D1–D3（部署包、选项打乱、性能实测）、自查和几次 BUG 修复、PM-M3-12（练习 / 考试记录按目标考试过滤）、MOCK-BE（演示数据模拟器，本机整库跑了 v1、v2 两次）。

## 2. 做到哪一步（可验证）

- `git -C /data/worktrees/javaweb-be status` 干净；`feat/backend` 和 main 都在 `06d0eba`，没有未合入的 commit。
- 最近几个关键 commit（都已在 main 上）：
  - 并发和事务：`c840358`（可重复读下先锁再读）、`9637e66`（真删 vs 没做完的练习 / 考试 → 归档）、`2df1a01`（BUG-QA-004：结算先锁 user，交卷 / 查看记录不再整段包成一个事务）、`23e06ff`（改资料 / 改密码只写改了的列）、`eb2c35d`（考试答案、错题本先 INSERT，避免间隙锁死锁）。
  - 接口：`a5a0362`（继续练习按目标考试过滤）、`9ff8470`（练习 / 考试记录的 categoryId、all 参数，共用组件 RootScope）。
  - 模拟器：`1424803`（RandomSource）、`e7c921f`（模拟器本体 + 文档第 7 节）、`85775e1`（读取 ref / mode）、`de7e580`（同一个人的学习时段不重叠）。
  - 测试：`bec7cd5`（INNODB_TRX 轮询间隔）、`71d17ae`（CI-FIX）。
- 测试状态：
  - 本机全量，用 CI MySQL 任务的环境变量：175 / 0 / 0（`be-evidence/CI-FIX-cifix-full-mysql.log`）。
  - mock 三个类在 H2 上 18 个全过；同一个种子跑两次，结果指纹相同。
- 本机 MySQL（127.0.0.1:13306）上我建的库：
  - `quiz_mock`：v1，测试拿它核对过，**不要动**。
  - `quiz_mock_v2`：v2，修复后的版本，197575 条作答，自查全为 0。
  - `quiz_mock_check` 是测试自己的副本，不是我的。
- 详细的报审记录在 `board/worklog-be.md`（最新的在最上面），证据在 `board/be-evidence/`（日志里的令牌、密码都已打码）。

## 3. 动了哪些文件

- `backend/` 几乎全部是我写的：
  - 业务：`service/`、`exam/`、`admin/`、`controller/`、`dto/`、`security/`、`config/`、`common/`。
  - 新增 `mock/` 包。
  - 测试：Flyway 迁移 `V1–V3`，测试代码在 `src/test/`（包括 `support/ApiTestBase`、各个 ApiTest、`mock/` 测试），测试数据在 `src/test/resources/mock-test/`。`seed/` 目录除外。
- `deploy/`：docker-compose、nginx、`deploy.sh`、`backup.sh`、env 示例、README、下载页。
- 文档：`docs/api.md`（只改了授权过的部分：选项打乱、几次接口约定）、`docs/api-proposal-shuffle.md`、`docs/performance.md`、`docs/mock-data.md` 第 7 节「怎么运行」。
- `board/`：`worklog-be.md`、`be-evidence/*`、本文件，以及 `mock-local-creds.txt`（见第 4 节）。

## 4. 没来得及做的

- **本机测试用的东西还没清理**：
  - 本机 MySQL 上的 `quiz_mock`、`quiz_mock_v2` 两个库，等测试和工头确认不再需要再删（`DROP DATABASE`；只删这两个，`quiz_test`、`quiz_mock_check` 不是我的）。
  - `board/mock-local-creds.txt`（权限 600）：里面是本机一次性的学员密码、管理员密码和 bcrypt 哈希，给测试登录本机副本用的，不再需要时删掉。**正式服务器用的学员密码由工头自己定、自己保管，跟这个文件无关。**
- 自查时记下、没改的设计层面问题（都写在 worklog 里）：
  - 改密码后旧令牌在 7 天内照样能用，要做的话需要 `password_changed_at` 列和 V4 迁移。
  - 上传没有每人的总量限制。
  - 管理员删题和学员开练习如果同时发生，有极小的窗口仍会真删。
  - 考试截止时没有宽限，这是按约定来的。
- 模拟器的已知限制：
  - persona 只用于统计，行为完全由数值决定，CRAMMER 的“考前突击”没有单独的时间曲线。
  - 头像文件名（UUID）和密码哈希的盐不随种子固定，其余都是确定的。
  - 没有按服务器的 JVM 参数（SerialGC、-Xmx320m）实测内存；工头说不需要，存活数据大约 40 MB。

## 5. 坑（下家会踩的）

- **H2 不能代替 InnoDB 验证并发**：
  - MySQL 可重复读的快照在第一次普通读时建立，所以加锁一定要放在事务的第一条语句，否则会读到旧数据。
  - InnoDB 在“UPDATE 没命中”时会加间隙锁，“先 UPDATE、没有再 INSERT”在多个用户之间也会死锁，要写成先 INSERT。
  - H2 的可重复读更严：等锁期间这一行被别人改过，会直接报 40001。
  - 改锁和事务一定要在本机 MySQL 13306 上跑，命令和规则在 `board/resource-rules.md` 最后一节：`heavy sozai-lock heavy -- bash -c 'mysql-reset-test && mvn …'`，重置和测试放在同一个闸门里；`quiz_test` 同一时间只能一个人用；不要停这个 MySQL。
- **加锁顺序**：一律先锁 user，再锁练习 / 考试记录。结算（`ExamSettler.settle`）和练习提交的第一句都是 `userService.lockRow`。新写的路径不要反过来。
- **测试里给自己换数据库时，`spring.datasource` 的 url、driver-class-name、username、password 四项要一起钉死**，否则会被 CI 的 `SPRING_DATASOURCE_*` 环境变量带歪。报审前要用 CI 同款环境变量跑一遍全量。
- `information_schema.INNODB_TRX` 是缓存，0.1 秒内没人读过才刷新，轮询要比这个慢（比如 200ms）。本机 MySQL 关了 performance_schema，`data_lock_waits` 永远是空的。
- **模拟器**（`docs/mock-data.md` 第 7 节）：
  - 只能在“只有示例题库 + 管理员”的库上跑，否则拒绝。中途失败会留下一半数据，只能整库重建后重跑。
  - 服务器上跑之前**先停正在运行的后端**，否则它的定时结算会用真实时间去结算模拟里的考试。
  - `docker compose run` 会替换 compose 里的 command，要把整条 `java …` 写全。
  - 学员密码只从环境变量 `MOCK_USER_PASSWORD` 读，模拟器不保存。以后要登录学员账号，就自己先把密码记在安全的地方（v1 就是因为没保存，再也登录不了）。
  - `app.mock.until` 只用于试跑，不能晚于现在；数据里的日期按“现在”校验。
  - 测试 profile 的种子目录是小样例 `seed-sample/`，mock 测试要显式改成 `classpath:seed/`。
  - 启动顺序：AdminBootstrap 10 → SeedLoader 20 → MockRunner 100。
- **停进程只按自己记下的 PID 停**，先核对 `/proc/<pid>/cwd` 是自己的工作树；禁止用 pgrep / pkill / killall 这类按名字匹配的方式（我早期误停过产品经理的后端）。mvn、跑 JVM 这类重任务都走 heavy 闸门。
- 规范：
  - 提交用 Conventional Commits，结尾带 `Co-Authored-By`。
  - 不 push、不改远程、不开 PR，推送只由工头做。
  - herdr-msg 返回 rc 4 表示消息在排队，不要重发。
