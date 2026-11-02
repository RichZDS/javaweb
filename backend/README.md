# 后端（quiz-backend）

Spring Boot 3.5 + Spring Security（JWT）+ MyBatis-Plus + Flyway。接口约定以 [`../docs/api.md`](../docs/api.md) 为准，
建表脚本在 `src/main/resources/db/migration/`。

## 本地运行

需要 JDK 17、Maven 3.9。默认 `dev` 配置：H2 文件库 `./data/quiz`（MySQL 兼容模式），首次启动时导入
`src/main/resources/seed/` 下的示例题库，并创建管理员 `admin` / `admin123`（仅本机开发用）。

```bash
cd backend
mvn spring-boot:run
# 或者先打包再运行
mvn -B -ntp package -DskipTests
java -jar target/quiz-backend.jar
```

- 接口文档：<http://localhost:8080/swagger-ui.html>，OpenAPI JSON：`/v3/api-docs`。
- 想重新导入示例题库：停掉服务，删掉 `backend/data/`，再启动（只有分类表为空时才导入）。
- dev 下允许 `http://localhost:*`、`http://127.0.0.1:*` 跨域，方便 H5 / 管理后台的开发服务器直连。

## 测试

```bash
mvn -B -ntp verify                 # 全部测试 + 打包，test 配置用 H2 内存库
mvn -B -ntp -Dtest=PracticeApiTest test   # 只跑一个测试类
```

CI 还会用 MySQL 8 再跑一遍：通过环境变量 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、
`SPRING_DATASOURCE_PASSWORD`、`SPRING_DATASOURCE_DRIVER_CLASS_NAME` 覆盖 test 配置里的数据源，本地有 MySQL 时同样可用。
测试里的时间由可调时钟 `MutableClock` 控制，默认 `2026-11-02T08:00:00Z`。

## 打包

```bash
mvn -B -ntp package
# 产物：target/quiz-backend.jar（可执行 jar）
```

## 生产运行

```bash
SPRING_PROFILES_ACTIVE=prod \
DB_URL='jdbc:mysql://127.0.0.1:3306/quiz?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC' \
DB_USERNAME=quiz DB_PASSWORD=<数据库密码> \
JWT_SECRET=<至少 32 字节的随机串> ADMIN_PASSWORD=<管理员初始密码> \
UPLOAD_DIR=/var/lib/quiz/uploads \
java -jar quiz-backend.jar
```

启动前会检查必填的环境变量（`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`JWT_SECRET` 至少 32 字节、`ADMIN_PASSWORD`），
缺几个就在一条错误里全部列出。生产不开跨域（同域部署），并按 `X-Forwarded-*` 头识别外层的 https 和 `/quiz` 前缀
（`server.forward-headers-strategy=framework`）。服务器上的 docker compose 部署见 [`../deploy/README.md`](../deploy/README.md)。

### 配置项（prod 全部从环境变量读）

| 环境变量 | 对应配置 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | — | 是 | `dev` | 生产填 `prod` |
| `DB_URL` | `spring.datasource.url` | 是 | — | MySQL 8 JDBC 地址，建议带 `serverTimezone=UTC` |
| `DB_USERNAME` | `spring.datasource.username` | 是 | — | |
| `DB_PASSWORD` | `spring.datasource.password` | 是 | — | |
| `JWT_SECRET` | `app.jwt.secret` | 是 | — | HS256 密钥，至少 32 字节 |
| `ADMIN_USERNAME` | `app.admin.username` | 否 | `admin` | 启动时库里没有管理员就用它创建 |
| `ADMIN_PASSWORD` | `app.admin.password` | 是 | — | 管理员初始密码 |
| `UPLOAD_DIR` | `app.upload.dir` | 否 | `/var/lib/quiz/uploads` | 上传文件目录，经 `/uploads/**` 访问 |
| `APP_SEED_ENABLED` | `app.seed.enabled` | 否 | `false` | 为 `true` 且分类表为空时导入示例题库 |
| `APP_RELEASE_ANDROID_LATEST_VERSION` | `app.release.android.latest-version` | 否 | `1.0.0` | `GET /api/app/version` 返回值 |
| `APP_RELEASE_ANDROID_MIN_VERSION` | `app.release.android.min-version` | 否 | `1.0.0` | 低于它的版本应强制升级 |
| `APP_RELEASE_ANDROID_DOWNLOAD_URL` | `app.release.android.download-url` | 否 | 空 | 安装包下载地址 |
| `APP_RELEASE_ANDROID_NOTES` | `app.release.android.notes` | 否 | 空 | 更新说明 |
| `APP_RELEASE_IOS_LATEST_VERSION` 等 | `app.release.ios.*` | 否 | 同上 | 四项与 Android 一一对应 |
| `SERVER_TOMCAT_THREADS_MAX` | `server.tomcat.threads.max` | 否 | `50` | 小内存服务器上限制请求线程数 |

其他配置（一般不用改）：`app.jwt.expire-days`（默认 7）、`app.exam.auto-settle`（默认 `true`，每 30 秒结算到时的考试；
test 配置里为 `false`，测试直接调用结算任务）、`app.seed.location`（种子文件目录，默认
`classpath:seed/`，也可以写 `file:/path/to/seed/`）、`app.cors.allowed-origin-patterns`（默认空，即不开跨域）、
`server.port`（默认 8080）。

## 代码结构

```
com.richzds.quiz
├── config/      配置属性、安全、Jackson（时间输出带毫秒和 Z）、MyBatis-Plus 分页、Clock
├── common/      统一返回 ApiResponse、错误码、全局异常处理、分页、时间和 JSON 工具
├── security/    JWT 签发校验、鉴权过滤器（每次请求查库，禁用立即生效）
├── controller/  只做参数和返回
├── service/     业务规则：判分 GradingService、练习、错题本、收藏、分类进度……
├── richtext/    Markdown 渲染（转义原始 HTML）和代码高亮（内联样式）
├── exam/        考试快照和“考试中禁看题”检查
├── seed/        示例题库导入 SeedLoader
├── domain/      题型、答案、校验规则等领域类型
├── entity/      表实体（时间字段都是 UTC 的 LocalDateTime）
└── mapper/      MyBatis-Plus Mapper 和聚合查询
```

## 实现约定（api.md 未写明处）

- 时间：库里一律存 UTC；接口输出形如 `2026-11-02T08:30:00.000Z`；“哪一天”按 `Asia/Shanghai`。
- 分页：`page` 小于 1 按 1，`size` 超过 100 按 100。
- 注册成功返回 HTTP 201（第 0 节“新建 201”）；登录密码错返回 401 `UNAUTHORIZED`；改密码时原密码错返回 400，`fields` 指向 `oldPassword`。
- 练习：`SEQUENTIAL` 需要且只能给一个分类，忽略 `count`、`types`、`scope`；`RANDOM`、`TAG` 没给 `categoryIds` 时用本人目标考试；
  `DAILY` 题库不足 10 道时用实际数量（0 道返回 409）；`RETRY` 只取仍是已发布状态的题。
- 统计和进度只算正式作答（`answer_record.answered = TRUE`），背题模式不产生作答记录。
- 考试：开考时把标题、题目、标准答案、分值存进 `exam_record.question_snapshot`，成绩单只从快照和结算结果算；
  超时结算的交卷时间记为截止时间；结算时每题都写一条作答记录，没答的 `answered = FALSE`、不进错题本。
  同一人并发开考按用户行加锁排队；保存答案和结算用 `SELECT … FOR UPDATE` 互斥。
- 规则卷：规则分类可以是 1–3 级，必须属于试卷所在考试；规则之间不重复抽题（`RuleDrawer`，导入和开考共用）。
- 上传：`POST /api/files` 只收 jpg / png / webp / gif（按文件头判断）且 ≤ 2 MB，存到 `UPLOAD_DIR/yyyy/MM/dd/<随机名>`，
  返回 201 和 `/uploads/...` 站内路径；`/uploads/**` 匿名可读。
- 后台题目：草稿可以先不填答案和解析，发布时必须完整；不适用当前题型的答案字段忽略；删除时没人做过、
  没被试卷引用、没人纠错过才真删（连同收藏），否则归档。分类树题量含草稿、不含归档。
- Excel 导入：只读第一张表，表头必须与模板一致；整行空的行跳过；正确的行以“已发布”状态导入，
  错误行返回 Excel 行号（表头是第 1 行）和原因；一次最多 2000 行。
- 后台试卷：新建为下架；`attempts` 数全部考试记录（含进行中），有记录就只能改标题和描述、不能删除；
  上架校验失败：固定卷没题或及格分超过总分返回 409 `CONFLICT`，规则卷题不够返回 409 `POOL_INSUFFICIENT` 带 `gaps`。
- 纠错列表项的 `type` 是纠错类型，另带 `questionType` 表示题型。
- 统计：“哪一天”在 SQL 里按 `graded_at + 8 小时` 取日期（Asia/Shanghai 自 1991 年起无夏令时）；
  排行榜和其他统计一样默认按目标考试，只算正常状态的用户，并列同名次。
