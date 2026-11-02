# 本地端到端测试

独立 Node 22 工程，接口用 `node:test` + `fetch`，页面用 Playwright 1.59.1（匹配本机 chromium 1217）。只对本地隔离测试环境运行；会创建测试账号、分类、题目、试卷和作答记录。

## 首次准备

```bash
source /data/crew/qzd-5b1e/bin/env.sh
cd e2e
heavy sozai-lock heavy -- npm ci
```

浏览器已在 `/data/ms-playwright`，不用下载。`PLAYWRIGHT_BROWSERS_PATH` 可以覆盖。`app-iphone13` 使用 Chromium 的 iPhone 13 尺寸、触屏和 UA 模拟，**不代表 iOS 真机或 WebKit 验证**；后台用 1440×1000 桌面尺寸。

## 起停环境与运行

收到工头合并通知后在 `feat/qa` 执行 `git merge main`，再运行：

```bash
npm run stack:up
# stack.sh 内部获取 heavy 和 sozai-lock heavy；外层不要再套锁。
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

默认端口为后端 `8080`、App `4173`、后台 `4174`。启动前检查端口，冲突时报错，不停止别人服务。关闭时校验 PID 的 Linux 进程启动标识，只停止本栈进程，删除有本栈标记的临时目录。构建或启动失败会自动回收；日志保留在 `e2e/artifacts/stack/`。`bash scripts/stack.sh status` 可查看本栈状态。

端口覆盖示例（起栈和测试地址要一起设置）：

```bash
export API_PORT=18080 APP_PORT=14173 ADMIN_PORT=14174
export BASE_URL=http://127.0.0.1:18080
export APP_URL=http://127.0.0.1:14173 ADMIN_URL=http://127.0.0.1:14174
npm run stack:up
```

后端 API 默认 `BASE_URL=http://localhost:8080`。本地 dev 管理员为任务单提供的 `admin` / `admin123`，可用 `ADMIN_USERNAME` / `ADMIN_PASSWORD` 覆盖；不要向日志或报告打印。每个 API 场景单独创建随机用户名、密码和分类树，不依赖前一个场景或示例题顺序。结束整个测试栈会销毁这些数据。

## 用例与证据

- `api/contract.test.mjs`：账号、分类/搜索、练习、四题型判分和幂等、背题、顺序/每日、错题、RETRY、收藏、考试/版本/快照/截止、统计、权限、管理 CRUD、纠错、Excel 部分成功。Excel 文件由用例现场生成，无需手工附件。
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
node node_modules/@playwright/test/cli.js test --list
```

`npm run test:api` / `npm run test:ui` 在依赖服务未启动时给出一条中文提示，退出 **2**，不假报通过。直接 `node --test` 也能运行：服务缺失时只注册一个失败的前置条件，退出 **1**，无成片请求堆栈。

**当前 M1 限制**：业务代码尚未合入 main，API 场景尚未针对真实实现运行；UI 控件名是待校准的场景骨架，前端合并后需要按实际页面校准 `ui/`。栈的真实 Maven/uni-app 构建和应用配置也需在首次集成时验证。不得据工程自检将任何业务验收项标为通过。
