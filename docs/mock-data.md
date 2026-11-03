# 演示数据（mock）约定

目的：让线上演示环境像一个真实运营了 4 个月的 App：300 个学员从 2026-07-01 起陆续注册，每天有人刷题、考试、收藏、纠错，管理员陆续加题、出卷、处理纠错。所有记录都有真实日期，统计、打卡、排行榜、后台看板都有内容。

分工：

- **数据（产品经理）**：`backend/src/main/resources/mock/` 下的 JSON，只写数据，不提交脚本。
- **模拟器（后端）**：读这些 JSON，按天推进时钟，**调用后端自己的业务代码**（注册、开练习、提交、交卷、收藏、纠错、后台加题出卷……）生成全部记录。
  不直接往表里插业务数据，这样错题本、统计、打卡、成绩都和真实使用时一样一致。
- **核对（测试）**：在本机库上只读核对一致性和真实感。

## 1. 文件

全部放在 `backend/src/main/resources/mock/`，UTF-8：

| 文件 | 内容 |
| --- | --- |
| `users.json` | 全局参数 + 300 个学员画像 |
| `questions.json` | 管理员后来陆续加的题，格式同 `docs/seed-format.md` 的 `questions`，每题多 `createdOn`、`status` |
| `papers.json` | 管理员后来出的试卷，格式同 `docs/seed-format.md` 的 `papers`（只用 `RULE`），每份多 `createdOn`、`online` |
| `texts.json` | 收藏笔记、纠错内容、管理员回复的文字池 |

分类一律用**名称路径**引用，如 `"计算机考研 408 / 数据结构 / 线性表"`，必须和示例题库里的分类名称完全一致。mock 不新增分类。

## 2. `users.json`

```json
{
  "randomSeed": 20261103,
  "startDate": "2026-07-01",
  "users": [
    {
      "username": "chen_yuxuan01",
      "nickname": "陈雨萱",
      "target": "计算机考研 408",
      "registeredOn": "2026-07-03",
      "lastActiveOn": null,
      "persona": "STEADY",
      "activeDaysPerWeek": 5.5,
      "weekdayWeights": [1, 1, 1, 1, 0.8, 1.2, 1.2],
      "questionsPerDay": [20, 45],
      "hours": [8, 12, 21, 22],
      "accuracy": { "start": 0.48, "end": 0.71, "subjects": { "计算机网络": -0.06, "数据结构": 0.05 } },
      "habits": {
        "daily": 0.8, "random": 0.5, "sequential": 0.3, "wrongReview": 0.4, "recite": 0.05,
        "shuffleOptions": 0.2, "favorite": 0.02, "feedback": 0.001, "examsPerMonth": 3
      },
      "switchTarget": null,
      "wrongRemoveThreshold": 3,
      "avatar": true,
      "disabled": false
    }
  ]
}
```

| 字段 | 说明 |
| --- | --- |
| `randomSeed` | 模拟器的随机种子；同一份数据、同一个种子，生成的结果完全一样 |
| `startDate` | 最早的注册日；结束日由模拟器取运行时的“现在”，不会生成未来的记录 |
| `username` | 3–20 位字母、数字、下划线，全局唯一；不能以 `qa_`、`pm_`、`fe_`、`be_`、`perf_`、`deploy`、`admin` 开头 |
| `nickname` | 1–30 字 |
| `target` | 目标考试（一级分类名称） |
| `registeredOn` | 注册日（北京时间），`startDate` 到 2026-11-02 之间 |
| `lastActiveOn` | 最后活跃日；`null` 表示一直用到今天。流失用户填一个过去的日期 |
| `persona` | `TOP`（尖子）、`STEADY`（稳定）、`CRAMMER`（考前突击）、`WEEKEND`（周末党）、`CASUAL`（偶尔）、`DROPOUT`（流失）、`NEWBIE`（近两周新来）；只用于统计和核对，行为由下面的数值决定 |
| `activeDaysPerWeek` | 平均每周学习几天，0.3–7 |
| `weekdayWeights` | 可选，周一到周日 7 个非负数，表示哪几天更常学；不写等于全是 1。模拟器按它分配学习日，总量仍由 `activeDaysPerWeek` 决定。周末党写成类似 `[0.2, 0.2, 0.2, 0.2, 0.4, 3, 3]` |
| `questionsPerDay` | 学习的那天做多少题 `[最少, 最多]` |
| `hours` | 常在哪几个小时学习（北京时间 0–23），模拟器在这些小时附近取时间 |
| `accuracy` | 正确率从 `start`（注册时）线性涨到 `end`（今天或最后活跃日）；`subjects` 按科目名称加减。模拟器再按题目难度修正（难度 1 约 +0.15，难度 5 约 −0.2），答对过的题再做更容易对 |
| `habits.daily` | 学习的那天先做每日一练的概率 |
| `habits.random` / `sequential` / `wrongReview` | 当天其余题量里随机练习、顺序练习（按章节往下做）、错题练习的相对比重 |
| `habits.recite` | 某次练习用背题模式的概率 |
| `habits.shuffleOptions` | 开练习时打开选项打乱的概率 |
| `habits.favorite` | 每做一道题顺手收藏的概率；收藏时约一半带笔记 |
| `habits.feedback` | 每做一道题提交纠错的概率 |
| `habits.examsPerMonth` | 每月模拟考试次数（可以是小数） |
| `switchTarget` | 中途换目标考试：`{"on": "2026-09-10", "to": "Java 程序设计"}`，不换填 `null` |
| `wrongRemoveThreshold` | 错题连续答对几次移出，1–10 |
| `avatar` | 是否上传头像；模拟器生成几何图案头像，走真实上传接口 |
| `disabled` | 是否被管理员禁用；禁用发生在最后活跃日之后 |

真实感要求（产品经理按这个写）：

- 三个考试的人数大致 408 占 50%、Java 30%、数据库 20%。
- 注册不是均匀的：7 月暑假起量、9 月开学一个高峰、国庆有波动、11 月 408 考前冲刺明显增多；近两周仍有新人。
- 画像比例大致：`TOP` 8%、`STEADY` 30%、`CRAMMER` 12%、`WEEKEND` 15%、`CASUAL` 15%、`DROPOUT` 15%、`NEWBIE` 5%，同一画像内部也要有差异。
- 正确率合理：408 普遍偏低（起点 0.40–0.60），Java、数据库稍高；尖子生终点可到 0.85，流失用户进步小。
- 用户名、昵称像真人：拼音 + 数字、英文名、学号风格、网名都有，不要有规律的编号；昵称有中文、英文、少量带符号。
- 学习时段像学生：晚上最多，午休、早上、深夜少一些；周末党集中在周六日。
- 数据量：全部作答记录（练习 + 考试）合计 15 万–25 万条。模拟器的预演会打印估算值，超出范围就调 `activeDaysPerWeek` 和 `questionsPerDay`。
- 禁用账号 3–5 个；中途换目标考试 10–20 人；不上传头像的人占 40% 左右。

## 3. `questions.json`

```json
{
  "questions": [
    {
      "chapter": "Java 程序设计 / 集合框架 / List 与 ArrayList",
      "createdOn": "2026-08-14",
      "status": "PUBLISHED",
      "type": "SINGLE",
      "stem": "……", "options": ["……"], "answer": ["B"], "analysis": "……",
      "difficulty": 3, "tags": ["ArrayList"]
    }
  ]
}
```

- 80–120 道原创题，规则同 `docs/seed-format.md`（看代码写输出的题必须真跑过），不能和示例题库重复。
- `status`：大部分 `PUBLISHED`；约 10 道 `DRAFT`（管理员还没审完）、约 5 道 `ARCHIVED`（下架的旧题）。
- `createdOn` 分布在 7 月到 11 月；模拟器在那天用管理员身份加题，发布的题从那天起才会被学员抽到。

## 4. `papers.json`

```json
{
  "papers": [
    {
      "title": "408 冲刺卷（一）", "exam": "计算机考研 408", "createdOn": "2026-10-20", "online": true,
      "durationMin": 60, "passScore": 60, "shuffleOptions": true, "description": "……",
      "rules": [ { "category": "计算机考研 408 / 数据结构", "type": "SINGLE", "count": 10, "score": 3 } ]
    }
  ]
}
```

- 4–6 份，每个考试至少一份；至少一份 `online: false`（下架）。规则题量不能超过那天已发布的题数。

## 5. `texts.json`

```json
{
  "favoriteNotes": { "数据结构": ["……"], "*": ["……"] },
  "feedback": {
    "ANSWER_WRONG": ["……"], "STEM_WRONG": ["……"], "ANALYSIS_UNCLEAR": ["……"], "OTHER": ["……"]
  },
  "adminReplies": { "RESOLVED": ["……"], "REJECTED": ["……"] }
}
```

- `favoriteNotes` 按科目名称分组，`*` 是通用的；每组至少 15 条，像学生自己的话（记口诀、标易错点、写“再看一遍”）。
- `feedback` 每类至少 15 条，具体、口语化；可以带 `{option}`、`{chapter}` 占位符，模拟器替换成那道题的选项字母和章节名。
- 管理员处理：约 65% 在 1–3 天内 `RESOLVED`（附回复），10% `REJECTED`（附理由），其余仍 `PENDING`。

## 6. 模拟器（后端）

- 开关：`app.mock.enabled=true` 时启动后跑一次模拟然后退出；平时不加载。`app.mock.dry-run=true` 只打印计划（人数、每天活跃人数、预计作答条数、考试场次），不写库。
- 学员密码统一从环境变量 `MOCK_USER_PASSWORD` 读，不写进仓库。
- 只能在“只有示例题库、只有管理员一个账号”的库上跑，否则直接报错退出，防止重复灌数据。
- 按天推进可替换的时钟；每个学员当天的行为按 `hours` 排开时间，所有记录的时间都是模拟时间，不晚于运行时的“现在”。
- 管理员的动作（加题、出卷、上下架、处理纠错、禁用账号）用库里已有的管理员账号做。
- 结束时打印汇总：每张表的行数、作答条数、日期范围、每个考试的人数，并按画像列出平均正确率。

## 7. 怎么运行（后端）

模拟器打包在后端 jar 里（`com.richzds.quiz.mock`），数据文件随 jar 一起打进 `classpath:mock/`。平时不加载，只有带 `--app.mock.enabled=true` 启动时才跑一次，跑完打印汇总并退出（成功退出码 0，出错 1）。

### 7.1 参数

| 参数 / 环境变量 | 说明 |
| --- | --- |
| `--app.mock.enabled=true` | 打开模拟器 |
| `--app.mock.dry-run=true` | 只校验数据、打印计划（人数、每天学习人数、按月曲线、预计作答条数、考试场数），不写库，不需要密码 |
| `MOCK_USER_PASSWORD` | 学员统一密码（6–32 位），只从环境变量读，不进仓库 |
| `--app.mock.location=…` | 数据目录，默认 `classpath:mock/`；本机改了数据想不重新打包时用 `file:/路径/mock/` |
| `--app.mock.until=…` | 只在本机试跑用：模拟到这个时间为止（ISO 时间，如 `2026-08-21T00:00:00Z`），不能晚于现在。不写就是运行时的“现在” |

要求和行为：

- 库里必须已经有示例题库（第一次部署时 `APP_SEED_ENABLED=true` 导入过）和管理员账号，并且**没有管理员以外的账号**，否则直接报错退出、不写任何数据。
- 开始前把示例题库、管理员账号的创建时间改到 `startDate` 前 14 天，免得它们比学员的记录还“新”。
- 每个动作都调用真实的业务代码、各自一个事务提交。中途失败时，库里会留下已经完成的部分，而防重复检查会拒绝在这样的库上再跑：要整库重建后重跑。
- 随机数都来自 `randomSeed`（包括抽题、选项打乱、规则卷抽题），同一份数据、同一个种子生成的记录一样；头像文件名和密码哈希的盐除外。
- 运行期间关闭定时结算；中途离开的考试，由模拟器把时钟推到截止时间之后再触发结算。
- 本机实测（MySQL 8.0.44，`-Xmx384m`）：每秒约 130 条作答；最大常驻内存约 400 MB，GC 后堆占用不到 40 MB。全量（约 19 万条作答）预计 25 分钟左右，服务器可能更慢。

### 7.2 本机

```bash
cd backend && mvn -B -ntp package -DskipTests
# 预演（不写库）
java -jar target/quiz-backend.jar --spring.profiles.active=dev --server.port=0 \
  --app.mock.enabled=true --app.mock.dry-run=true
# 整库跑：先建一个空库（这里用本机 MySQL 13306 的 quiz_mock），第一次启动会建表、导入示例题库和管理员
MOCK_USER_PASSWORD='自己定' java -Xmx384m -jar target/quiz-backend.jar --spring.profiles.active=dev --server.port=0 \
  --spring.datasource.url='jdbc:mysql://127.0.0.1:13306/quiz_mock?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC' \
  --spring.datasource.username=root --spring.datasource.password= \
  --spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver \
  --app.mock.enabled=true
```

`--server.port=0` 让它随便挑一个空闲端口，不和本机别的服务抢 8080。

### 7.3 服务器（docker compose，一次性运行）

在服务器的部署目录（`deploy.sh` 用的 `DEPLOY_DIR`）里，用和平时同一个后端镜像、同一套环境变量、同一个上传目录，起一个跑完就删的容器。服务器上的 docker 需要 sudo 时，下面的 `docker` 换成 `sudo docker`。

```bash
# 1. 先确认库是新的：只有示例题库和管理员（否则模拟器会拒绝运行）。要重灌，先用 deploy/backup.sh 备份，再按 deploy/README.md 重建库。
# 2. 停掉正在运行的后端，免得它的定时结算用真实时间去结算模拟里的考试
docker compose stop backend
# 3. 预演
docker compose run --rm --no-deps backend java -Xmx384m -XX:+UseSerialGC -Duser.timezone=UTC \
  -jar /app/quiz-backend.jar --app.mock.enabled=true --app.mock.dry-run=true
# 4. 正式跑（密码只放在这一条命令的环境变量里）
MOCK_USER_PASSWORD='…' docker compose run --rm --no-deps -e MOCK_USER_PASSWORD backend \
  java -Xms128m -Xmx384m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC -Xss512k -XX:+ExitOnOutOfMemoryError \
  -Duser.timezone=UTC -jar /app/quiz-backend.jar --app.mock.enabled=true
# 5. 跑完（看到“演示数据汇总”、容器退出码 0）再把后端起来
docker compose start backend
```

- `docker compose run` 会替换 compose 里的 `command`，所以要把整条 `java …` 写全；环境变量、上传目录卷、内存限制（640 MB）、网络都和平时的后端一样，头像会写进同一个上传目录。
- `--no-deps`：数据库已经在跑，不用再拉起依赖。
- 汇总打印在容器的标准输出里，建议加 `| tee mock-run.log` 留一份。
