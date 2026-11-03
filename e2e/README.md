# 本地端到端测试

独立 Node 22 工程，接口用 `node:test` + `fetch`，页面用 Playwright 1.59.1（匹配本机 chromium 1217）。默认对本地隔离环境运行；预发布仅按明确授权使用下述专用入口；会创建测试账号、分类、题目、试卷和作答记录。

## 首次准备

```bash
source /data/crew/qzd-5b1e/bin/env.sh
cd e2e
heavy sozai-lock heavy -- npm ci
```

浏览器已在 `/data/ms-playwright`，不用下载。`PLAYWRIGHT_BROWSERS_PATH` 可以覆盖。`app-iphone13` 使用 Chromium 的 iPhone 13 尺寸、触屏和 UA 模拟，**不代表 iOS 真机或 WebKit 验证**；后台用 1440×1000 桌面尺寸。

## 起停环境与运行

第一轮 M1（只有后端和 App，尚无管理后台）使用显式范围：

```bash
export QA_SCOPE=m1 QA_STAGE=run-01
npm run stack:up
sozai-lock light -- npm run test:api
heavy sozai-lock heavy -- npm run test:ui
npm run stack:down
```

M1 会把现有种子复制到临时目录，再追加 20 道原创验收题，供 API 场景共享只读题库；每个场景仍独立注册用户。通过现有 `app.seed.location` 导入，不依赖未实现的后台接口，不改业务源码。H5 走查使用原始 Java 示例题库与真实页面；地址由栈随机分配并自动读取。考试、统计、管理功能在 M1 API 报告中标记为本分组未覆盖，UI 只跑注册到错题重练。

M1 的两项缺陷和未知排序值可单独复测：`sozai-lock light -- node scripts/repro-m1-issues.mjs`，只输出接口状态与相关非认证字段，不记录密码或令牌。

M2/M3 接口用例已预编写，收到对应阶段合并通知后再实跑：

```bash
QA_SCOPE=m2 npm run stack:up
QA_SCOPE=m2 sozai-lock light -- npm run test:api
npm run stack:down

# M3 可复用仅后端+App的 M2 栈，避免把后台页面是否到位当成接口前置。
QA_SCOPE=m2 npm run stack:up
QA_SCOPE=m3 sozai-lock light -- npm run test:api
npm run stack:down
```

第二轮可运行 M1＋M2，并用单浏览器脚本走查真实考试（含一分钟自动交卷）：

```bash
QA_SCOPE=m2 npm run stack:up
QA_SCOPE=m1+m2 sozai-lock light -- npm run test:api
QA_SCOPE=m2 QA_STAGE=run-02 QA_MAIN_COMMIT=<产品commit> sozai-lock light -- npm run test:ui
npm run stack:down
```

此处 UI 只有一个浏览器/一个脚本，按资源规则走 light；默认全套多用例/多浏览器仍用 heavy。`app-m2.spec.mjs` 使用 `context.setOffline` 真正断开浏览器网络，验证待同步时阻止交卷、恢复补传、关页新开恢复草稿、未答提醒取消、手动与到时交卷，并逐项核对统计页面。H5 关页重开不替代真机杀进程测试。结果附件可交给 `node scripts/check-result-fields.mjs <walk-result.json>` 核对成绩单新增字段；原生字符串类型可用 `node scripts/repro-native-strings.mjs` 单独复现。

第三轮全部接口与已校准页面流程（按顺序执行，避免看板增量遇到并发写入）：

```bash
QA_SCOPE=all npm run stack:up
QA_SCOPE=all sozai-lock light -- npm run test:api
QA_SCOPE=m3 QA_STAGE=admin sozai-lock light -- npm run test:ui
QA_SCOPE=pm QA_STAGE=pm sozai-lock light -- npm run test:ui
QA_SCOPE=settings QA_STAGE=settings sozai-lock light -- npm run test:ui
npm run stack:down
```

`all` 不再跳过 M3；后台走查含代码和图片题、正确/错误混合 Excel、规则缺口、禁用与看板对账。`pm` 覆盖 PM-M3-01/02/03；`settings` 覆盖 PM-M3-04/05 及 H5 可验证的设置。故障复测只拦截指定分类/薄弱点请求，其余调用真实后端。新注册的测试账号统一以 `qa_` 开头。

M2 在临时种子中加入三份固定/规则验收卷，含一分钟卷，因此不依赖 M3 建卷接口。M3 各场景通过管理接口建立独立分类、题目和试卷。M3 看板增量用例应在隔离栈顺序执行，期间不要同时跑 UI 或其他写入者。

仅做用例收集（不连接后端、不执行用例、不算通过）：

```bash
QA_SCOPE=m2 QA_COLLECT_ONLY=1 sozai-lock light -- npm run test:api
QA_SCOPE=m3 QA_COLLECT_ONLY=1 sozai-lock light -- npm run test:api
```

`QA_COLLECT_ONLY` 只支持 m2/m3，结果全部标记 skipped。M2 的截止和周期结算场景分别真实等待约 62 秒、95 秒；不修改系统时间。断网场景实际切断本地代理到 API 的请求，重连后由测试显式补发，验证服务器版本/截止规则，**不代替 App 离线缓存、杀进程或断网 UI 验收**。周期结算先从统计观察结果，再读取记录详情，避免用记录读取触发结算而误报后台已自动结算。下一轮还需确认统计读取没有代替定时任务结算；跨日/跨周与历史连续打卡需受控时间或历史数据，不能从当天用例推断通过。

选项打乱：`QA_SCOPE=shuffle sozai-lock light -- npm run test:api`，页面使用同一 scope 的 `npm run test:ui`。新标签正确/旧字母错答必须从实际映射取得换位样本；随机没有换位时不能假装做过差分验证。

已授权预发布的应用级入口是 `scripts/staging.mjs`。它清除代理变量，浏览器直接连接指定IP，TLS保持正常校验；凭证只在需要管理员的进程中读取权限0600的 `STAGING_CREDENTIAL_FILE`（默认班子 staging-admin.txt），不会回显、写入状态文件或报告。所有新账号以qa_开头。

```bash
sozai-lock light -- node scripts/staging.mjs prepare
sozai-lock light -- node scripts/staging.mjs api
sozai-lock light -- node scripts/staging.mjs shuffle-api
sozai-lock light -- node scripts/staging.mjs app
sozai-lock light -- node scripts/staging.mjs exam
# 以下按已经部署的版本运行
sozai-lock light -- node scripts/staging.mjs shuffle-ui
sozai-lock light -- node scripts/staging.mjs repro-submit
sozai-lock light -- node scripts/staging.mjs upload
```

prepare 新建独立20题/3卷夹具，并把非认证的根名称写入被忽略的 `.staging-state.json`。复测复用它；数据库重置后才重新准备。可用 `STAGING_COMMIT` 标记工头确认的部署版本。共享预发布会有其他验收写入，因此全站题量/作答/待处理增量不做严格前后相等，日志明确说明；自己章节、账号、试卷的断言保留。这些全站增量不能记成线上已验证。`QA_TEST_NAME` 可按Node正则只复跑命中的场景（例如 `QA_TEST_NAME=EX-01/02/03/19`）；复测日志和首轮日志分别保留。`concurrency-api` 单独跑3项练习并发场景，`admin-shuffle-ui` 跑后台到App的选项打乱流程（需管理员凭证）；单浏览器脚本使用light档。预发布导航等待90秒，仅为容纳跨境静态资源传输，不改变业务断言。设置 `QA_API_TIMINGS=/绝对路径/已有目录/api-timings.jsonl` 可追加API方法、无查询参数路径、HTTP状态和完整响应往返毫秒数，不记录请求体、令牌、响应体或静态资源。此时间包含网络，不能当作服务端处理耗时。

`repro-submit` 在同一账号连续3场并发交卷，随后立即GET，并对照作答/考试/错题计数，避免只看到不一致响应却无法判断持久化状态。

收到工头合并通知后在 `feat/qa` 执行 `git merge main`，再运行：

```bash
npm run stack:up
# stack.mjs 对每个构建命令获取闸门；外层不要再套锁。
sozai-lock light -- npm run test:api
heavy sozai-lock heavy -- npm run test:ui
npm run stack:down
```

完整一轮可用 trap 保证测试失败时也关闭环境：

```bash
trap 'npm run stack:down' EXIT INT TERM
npm run stack:up
sozai-lock light -- npm run test:api
heavy sozai-lock heavy -- npm run test:ui
```

`stack:up` 打后端 jar（`-DskipTests`，不是后端单测验收），对前端执行 `npm ci` 和构建，启动 dev 配置后端和两个静态服务，等待就绪。H2 文件、上传目录及后端工作目录在独立临时目录中；静态服务将 `/api/**` 和 `/uploads/**` 转发到该后端。后端、App、后台均只绑定 `127.0.0.1`。

每个 Maven/npm 构建命令在 `stack.mjs` 内检查 PATH：有 `heavy` 就经过班子闸门，有 `sozai-lock` 就同时经过本机 heavy 档，顺序固定为 `heavy → sozai-lock`。没有这些工具时直接执行，便于其他机器运行。`stack.sh` 不在外层重复加锁，直接调用 `node scripts/stack.mjs up` 也使用相同的构建闸门；服务运行期间不占构建锁。

本机 QA 默认由系统随机分配三个空闲端口，地址和所属工作树写入 `.stack-state.json`；API、Playwright 和复现脚本自动读取同一份地址，不必手工复制端口。若显式指定端口，冲突只报错，不停止占用者。启动时记录准确 PID、cwd 和 Linux 启动时间；停止前逐个核验 cwd 必须仍在本工作树且与登记一致、启动时间相同，任何不匹配都会拒绝并保留状态与数据。后端 cwd 是本工作树的 backend，H2/上传仍指向绝对临时路径。

构建取消和测试信号转发同样核验已登记根 PID；只从它的 `/proc/.../children` 获取子进程，逐个登记、核验，再发正 PID 信号，不使用名字/端口匹配或进程组停止。登记证据在 `artifacts/stack/processes/`、`artifacts/processes/`，关闭清单在 `artifacts/stack/stop-processes.json`。构建或启动失败会回收已确认属于本栈的进程；身份不明时保留状态，不能假报清理成功。`bash scripts/stack.sh status` 可查看本栈状态。

有明确需要时可指定空闲端口，测试仍自动读取状态中的地址；默认不设置这些变量：

```bash
export API_PORT=18080 APP_PORT=14173 ADMIN_PORT=14174
npm run stack:up
```

显式 `BASE_URL` / `APP_URL` / `ADMIN_URL` 优先于本栈地址；没有状态文件时 API 才退回原来的 `http://localhost:8080`。本机走查使用脚本起栈并自动读地址。本地 dev 管理员为任务单提供的 `admin` / `admin123`，可用 `ADMIN_USERNAME` / `ADMIN_PASSWORD` 覆盖；不要向日志或报告打印。每个 API 场景单独创建随机用户名、密码和分类树，不依赖前一个场景或示例题顺序。结束整个测试栈会销毁这些数据。

## 用例与证据

- `api/contract.test.mjs`：账号、分类/搜索、练习、四题型判分和幂等、背题、顺序/每日、错题、RETRY、收藏、考试/版本/快照/截止、统计、权限、管理 CRUD、纠错、Excel 部分成功。Excel 文件由用例现场生成，无需手工附件。
- `api/m1.test.mjs`：M1 独立用户 + 临时种子夹具；账号、分类/搜索、判分、练习/RETRY、错题、收藏、设置、权限、纠错，不依赖管理接口。
- `api/m2.test.mjs`：13 个考试、断网补发、版本、截止、自动结算、统计、排行榜和参数边界场景。
- `api/concurrency.test.mjs`：同练习同题、同题跨练习、同练习不同题的并发提交，每种3轮；核对响应、持久化结果、统计和错题次数。`QA_SCOPE=all` 包含这3个新增场景。
- `ui/admin-shuffle.spec.mjs`：后台页面把自建卷从不打乱改为打乱并上架，再用同一个浏览器的手机上下文作答；11对1错，核对新标签、原选项和错题本。
- `api/m3.test.mjs`：15 个管理权限/CRUD、试卷限制/缺口、快照、纠错、禁用、Excel、看板场景。
- `ui/app-m1.spec.mjs`：M1 真实页面操作和逐步截图；答案通过题目详情校验，注册、选择、答题、提交、跳页全部经 UI 操作，不用 API 代替操作。
- `ui/app-m2.spec.mjs`：考试断网拦截/恢复、关页重开、手动/自动交卷、统计六项和排行榜本人数据对账。
- `ui/app.spec.mjs`：注册 → 选考试 → 随机十题（故意错一道）→ 结果 → 错题本重练 → 固定卷 → 成绩单 → 统计。
- `ui/admin.spec.mjs`：登录 → 新建分类 → 新建题目 → 新建固定卷并上架。
- `scripts/check-harness.mjs`：隔离假构建/假后端仅用于验证启停、端口保护、静态资源与代理，不是业务通过证据。

考试截止场景通过管理员 API 创建 **1 分钟** 的测试卷，并等待真实服务器截止，不修改系统时间。整轮 API 会因此至少持续一分钟。

UI 每一步截图保存 `/data/crew/qzd-5b1e/shots/qa-m1/<运行时间>/<浏览器项目>/`。可通过 `QA_STAGE=m2` 改阶段，或 `SHOT_DIR` 改根目录；失败步骤也截图。Playwright JSON 在 `e2e/artifacts/ui-results.json`；关闭 trace 和录像以避免把认证网络数据写进分享材料。

验收编号映射（覆盖部分行为不等于整条已通过）：

| 场景 | 相关编号 |
| --- | --- |
| 账号和权限 | AC-01/02/04、AD-01/19、NF-01/02 |
| 分类/搜索/进度 | AC-08、CA-02/03/04/06/07 |
| 随机与判分/幂等/背题 | PR-01/02/03/04/06/07/08/09/11/12/13/15/17/19/21 |
| 错题与收藏、RETRY | WR-01/03/04/05/07/10/11/12、PR-24 |
| 考试 | EX-01/02/03/07/11/14/15/16/17/19/20/23/24 |
| 统计和每日一练 | ST-02/03/07/08、DY-01/03/08 |
| 管理端 | AD-02/03/04/05/06/07/08/14/16/17/18/22、CA-09 |

阶段报告写在 `docs/test-report.md`。只记录实际执行的通过/失败数、main commit、截图和返回证据；未执行项目标为未测。H5 测试不能代替 Android/iPhone 真机、安装包或发布验收。

## 工程自检与无服务行为

```bash
sozai-lock light -- node --test scripts/check-harness.mjs
sozai-lock light -- node --test scripts/check-offline-proxy.mjs
node node_modules/@playwright/test/cli.js test --list
```

`npm run test:api` / `npm run test:ui` 在依赖服务未启动时给出一条中文提示，退出 **2**，不假报通过。也可直接指定 `node --test api/m1.test.mjs`：服务缺失时只报告一个失败的前置条件，退出 **1**，无成片请求堆栈。M1 推荐设置 `QA_SCOPE=m1` 使用 npm 脚本，以免把尚未实现的全范围用例一起收集。

M1 接口和页面执行证据以 `docs/test-report.md` 为准。默认全范围的考试/后台场景仍待 M2/M3 合入校准，不能把 M1 结果作为全功能验收结果。

## 演示数据库核对（MOCK-QA）

仅在工头通知生成完成后执行，范围和判定见 [mock-check.md](mock-check.md)。原库 `quiz_mock` / `quiz_mock_v2` 只读；接口固定在自己的副本 `quiz_mock_check`，不使用预发布网址。脚本依赖本机 `/data/tools/mysql-8.0/bin/{mysql,mysqldump}` 和 Python 标准库，无额外依赖。

```bash
# cutoff 使用本次生成日志对应的UTC边界，不用验收时的当前时间。
sozai-lock light -- python3 scripts/mock-snapshot.py quiz_mock artifacts/mock-v1
sozai-lock light -- python3 scripts/mock-audit.py artifacts/mock-v1 <cutoff-UTC>
sozai-lock light -- python3 scripts/mock-checklist-sql.py quiz_mock artifacts/mock-v1 <cutoff-UTC>
sozai-lock light -- python3 scripts/mock-realism.py artifacts/mock-v1
```

`mock-snapshot.py` 在一个只读事务中导出13张业务表，排除密码散列；`mock-audit.py` 对全库做时间、引用、独立判分、错题状态重放和日练检查；`mock-checklist-sql.py` 实跑清单中的10组SQL；`mock-realism.py` 输出画像、时段、学习进步及相邻练习A-B-A交替证据。SQL语句与实际结果均留在对应artifact目录。

副本与接口脚本在项目根目录运行：`mock-copy.py` 固定从quiz_mock导入quiz_mock_check，已有副本时拒绝覆盖。按本轮工头明确授权，只改副本USER的password_hash，凭证/BCrypt从0600的board/mock-local-creds.txt内部读取；原库不改，临时dump删除。先从当前main构建自己的jar（按资源规则走heavy闸门），再 `sozai-lock light -- node e2e/scripts/mock-stack.mjs up`。该服务只连副本，关闭mock/seed/自动结算，PID/cwd/启动时间登记在artifacts/mock-v1。

接口核对：`mock-api.py e2e/artifacts/mock-v1`；后台定点范围加 `--admin-only`。补充详情用 `mock-api-details.py e2e/artifacts/mock-v1`，它读取保留的 `api-audit-initial.json` 里的样本集合。使用light锁；不自动重试。日志不保存令牌、密码或管理员登录响应。首轮与定点复测结果分开保存，不能用后者覆盖报告里的首轮失败。

结束时分别将原库、副本只读导出到 `artifacts/mock-source-after` / `artifacts/mock-copy-after`，运行 `mock-integrity.py e2e/artifacts/mock-v1` 比较：源表不变；副本仅允许授权密码字段及鉴权last_active_at变化。`mock-stack.mjs down` 只停止登记的自己的后端，副本保留到工头要求清理。v2仅重跑前述只读导出/SQL/一致性/真实感脚本，输出改用artifacts/mock-v2，原v1证据不覆盖。
