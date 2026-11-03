# 01 后端 · assignee: qzd-be-5b1e · 状态: doing

## 现场 / 锚点
- 工作树 `/data/worktrees/javaweb-be`，分支 `feat/backend`。目前只有 `backend/src/main/resources/db/migration/V1__init.sql`。
- 接口：`docs/api.md`。库表：`V1__init.sql`。功能和规则：`docs/spec.md`。CI：`.github/workflows/ci.yml`（会用 H2 和 MySQL 8 各跑一遍 `mvn -B -ntp verify`）。
- 工具：`source /data/crew/qzd-5b1e/bin/env.sh` 后有 JDK 17.0.21、Maven 3.9.16。本机没有 MySQL。

## 做法

### 工程
- Maven 单模块，`backend/pom.xml`，groupId `com.richzds`，artifactId `quiz-backend`，包名 `com.richzds.quiz`，打成可执行 jar（`target/quiz-backend.jar`，用 `<finalName>`）。
- 父 POM `spring-boot-starter-parent` 3.5.16，Java 17。依赖版本已查过，直接用：`mybatis-plus-spring-boot3-starter` 3.5.18.1（分页插件另加 `mybatis-plus-jsqlparser` 同版本）、`jjwt-api/impl/jackson` 0.13.0、`springdoc-openapi-starter-webmvc-ui` 2.10.0、`commonmark` 0.32.0（加表格扩展）、`poi-ooxml` 5.5.1。Flyway 用 Boot 管理的版本（`flyway-core` + `flyway-mysql`）。数据库驱动 `mysql-connector-j`，H2 作运行期和测试依赖。不用 Lombok。
- 分层：`controller`（只做参数和返回）→ `service`（业务规则）→ `mapper`（MyBatis-Plus）。`dto` 放请求和返回对象（优先用 record）。统一返回 `ApiResponse`，统一异常处理 `GlobalExceptionHandler`，错误码与 `docs/api.md` 第 0 节一致。
- 配置文件：
  - `application.yml`：公共配置，默认激活 `dev`。JVM 默认时区设为 UTC（在 `main` 里 `TimeZone.setDefault`），JDBC 也用 UTC。
  - `application-dev.yml`：H2 文件库 `./data/quiz`（MySQL 模式），开启示例题库导入，管理员 `admin` / `admin123`（仅开发用）。
  - `application-test.yml`：H2 内存库。数据源只写在这个文件里，CI 会用环境变量 `SPRING_DATASOURCE_*` 覆盖成 MySQL，所以不要在代码里写死 H2。
  - `application-prod.yml`：全部从环境变量读：`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`JWT_SECRET`（≥ 32 字节）、`ADMIN_USERNAME`、`ADMIN_PASSWORD`、`UPLOAD_DIR`、`APP_SEED_ENABLED`、`APP_RELEASE_*`。缺 `JWT_SECRET` 或管理员密码时启动失败，并说清缺什么。
- 时间：实体里存 UTC，接口输出 ISO-8601 带 `Z`。按天统计一律用 `Asia/Shanghai`。注入一个 `java.time.Clock` bean，业务代码只从它取时间，方便测试改时间。

### 功能（按 `docs/api.md` 全部实现）
1. **账号和权限**：注册、登录、JWT（7 天）、`/api/me`、改密码、文件上传（存 `UPLOAD_DIR`，按日期分目录，文件名随机，校验类型和大小，经 `/uploads/**` 访问）。Spring Security 无状态；`/api/admin/**` 只给 ADMIN；被禁用的用户每次请求都拒绝。启动时没有管理员就按配置建一个。
2. **富文本**：`RichTextRenderer`，用 commonmark 把 Markdown 转 HTML，原始 HTML 一律转义（防 XSS）。代码块输出带内联样式的 `<pre><code>`（等宽字体、浅灰底、可横向滚动），对 java、c、python、sql 做简单的关键字、字符串、注释着色，同样用内联样式（App 的 rich-text 不认 class）。
3. **分类和搜题**：分类树带本人进度；搜题；题目详情；考试中禁止查看相关题（403 `EXAM_IN_PROGRESS`）；纠错反馈。
4. **判分**：`GradingService` 是判分的唯一实现，规则见 api.md 第 1 节。
5. **练习**：六种模式、三种范围、题不够的处理、顺序练习和每日一练的复用、每题只提交一次、进度、结束、历史、当前未完成练习。提交时更新错题本（规则见 api.md 第 4 节末尾）。
6. **错题本和收藏**：按 api.md 第 5 节。
7. **考试**：试卷列表和须知、开考（快照、复用进行中的、规则卷抽题和缺口）、答案带版本保存、截止后拒收、交卷只结算一次（用条件更新 `WHERE status='IN_PROGRESS'` 保证）、每 30 秒的过期结算任务、成绩单（按题型、按章节）、考试记录。结算时写作答记录、错题入库。
8. **统计**：api.md 第 7 节全部接口，口径严格按文档。
9. **App 版本**：读配置返回。
10. **管理后台接口**：api.md 第 9 节全部，包括 Excel 模板下载和导入（第 10 节格式，逐行报错）。
11. **示例题库导入**：`SeedLoader`，`app.seed.enabled=true` 且分类表为空时，按文件名顺序读 `classpath:seed/*.json`（格式见 `docs/seed-format.md`），建分类、题目（状态 PUBLISHED）、试卷（状态 ONLINE）。文件由产品经理在另一个分支写，你这边先自己放一个小样例文件 `seed/00-sample.json`（3 个分类、每种题型各 2 道、1 份固定卷、1 份规则卷）用于开发和测试；合并时产品经理的文件会替换它。
12. **Swagger**：`/swagger-ui.html`、`/v3/api-docs` 可匿名访问。
13. CORS：开发时允许 `http://localhost:*`；生产走同域，不开跨域。

### 阶段
- **M1**：工程、配置、账号权限、富文本、分类和搜题、判分、练习、错题、收藏、种子导入、App 版本。
- **M2**：考试（含定时结算）、统计、排行榜。
- **M3**：管理后台全部接口、Excel 导入导出、上传。

每个阶段完成就 commit 并报审，不用等全部做完。

## 明令不做
- 不改 `docs/api.md`。发现接口约定有问题或缺字段，写进报审的「疑问」，由工头决定。
- 不用 Redis、消息队列、微服务拆分。
- 不写前端代码，不写示例题库正文（`seed/00-sample.json` 除外）。
- 不往 `V1__init.sql` 里改东西；要改表就加 `V2__…sql`。

## 测试
- 用 JUnit 5 + Spring Boot Test + MockMvc，`test` 配置（H2 内存库）。每个阶段至少覆盖：
  - 判分：四种题型，多选少选、错选、空选，填空忽略大小写和空格的开关、多个可接受答案。
  - 练习：题不够返回 409 和 `available`；`acceptFewer`；同一题重复提交只算一次；看别人的练习返回 404；背题模式不能提交、不计统计。
  - 错题：答错收录、再错累加、错题练习连对 3 次移出、移出后再错恢复。
  - 考试：开考复用进行中的；旧版本答案被拒；截止后保存返回 409；交卷两次只结算一次；到时自动结算（用可调的 Clock）；改题后旧成绩单不变；进行中的考试查题返回 403。
  - 统计：按北京时间跨日（UTC 16:00 前后）；连续打卡今天没做时算到昨天；薄弱点至少 5 道不同题。
  - 权限：未登录 401、普通用户访问 `/api/admin/**` 403、禁用用户 403。
  - 导入：正确行导入、错误行逐行报原因。
  - 富文本：`<script>` 被转义，代码块有内联样式。
- 命令：开发中 `heavy mvn -B -ntp -Dtest=<类名> test`；报审前 `heavy mvn -B -ntp verify` 全绿。
- 每个阶段报审前，再用 `dev` 配置真的起一次服务（`java -jar target/quiz-backend.jar`），用 curl 走一遍本阶段的主流程（注册 → 登录 → 取分类 → 开练习 → 提交 → 看错题……），贴出命令和返回，然后停掉服务。

## 交付
- 每个阶段一笔或少量几笔 commit，在 `feat/backend` 上。
- 报审内容：阶段名、commit id、`mvn verify` 的退出码和测试数、curl 走查记录、改动了 api.md 之外的什么约定（应为无）、疑问。
- 写 `backend/README.md`：怎么本地跑、配置项清单（环境变量表）、怎么打包。
