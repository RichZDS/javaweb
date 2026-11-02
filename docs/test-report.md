# 测试报告

## 2026-11-02 · M1 测试工程预检

- 验收基线：`main a2f5225`（已含 API-01、API-02 与 137 条验收清单）。此基线只有文档和 SQL，尚无后端、App、后台实现。
- 环境：Node 22；Playwright 1.59.1；Linux。本轮没有真实产品页面截图，没有 Android/iOS 真机执行记录。
- 测试代码：`feat/qa` 的本次 M1 提交，最终 commit 记录在班子 `worklog-qa.md`。

| 命令 | 通过 | 失败 | 退出码 | 实际结论 |
| --- | ---: | ---: | ---: | --- |
| `heavy sozai-lock heavy -- npm install --ignore-scripts --no-audit --no-fund` | — | — | 0 | 依赖与 lock 生成成功，196 packages |
| `sozai-lock light -- node --test scripts/check-harness.mjs` | 4 | 0 | 0 | 假构建/假后端的启停、代理、资源、端口保护、失败与中断清理自检通过；不算业务测试 |
| `node node_modules/@playwright/test/cli.js test --list` | 0 | 0 | 0 | 收集 2 个 UI 场景，未执行 |
| `npm run test:api` | 0 | 0 | 2 | 服务预检阻止执行，API 业务场景未测 |
| `npm run test:ui` | 0 | 0 | 2 | 后端/H5/后台预检阻止执行，UI 业务场景未测 |
| `sozai-lock light -- node --test` | 0 | 1 | 1 | 仅一个“本地 API 服务已启动”前置条件失败，未运行业务场景，无请求堆栈洪泛 |
| `bash scripts/stack.sh up` | — | — | 1 | 清楚报告缺少 backend/pom.xml；未产生常驻服务 |

证据：`e2e/artifacts/m1/harness.log`、`e2e/artifacts/m1/node-test-no-service.log`、`e2e/artifacts/m1/stack-missing-implementation.log`（本机产物，不提交依赖/日志）；其余真实命令输出摘录和完整报审记录见 `/data/crew/qzd-5b1e/board/worklog-qa.md`。

已编写的用例覆盖映射见 `e2e/README.md`。全部业务验收项本轮仍为 **未测**，原因是实现尚未合入；不存在可报告的业务通过率。UI 截图预定位置 `/data/crew/qzd-5b1e/shots/qa-m1/`，本轮没有截图。

问题记录：尚未对业务实现执行测试，**无已确认业务缺陷**。缺少实现是阶段依赖，不报成后端故障。API-01 已补齐管理端写入对象；API-02 的 RETRY 已加入测试。UI 控件定位、真实栈构建和 dev 配置参数仍待首次 main 集成校准。

下一轮在收到阶段合并通知后同步 main，运行完整 API/UI，按 `docs/acceptance.md` 编号逐项记录。发现缺陷使用 `QA-001` 起的编号，并写严重程度、步骤、期望、实际、证据、归属；修复后追加复测结论。

## 2026-11-02 · QA-M1-OK 构建闸门返修

- 基线：`main bf8bb69`，M1 工程已由工头签收。
- `stack.mjs` 的 Maven 打包、两个 npm 安装、两个前端构建各自检测 PATH 并使用可用闸门，固定顺序 `heavy → sozai-lock`；没有闸门时直接运行。移除 `stack.sh` 外层重复加锁，避免嵌套等待。
- `sozai-lock light -- node --test scripts/check-harness.mjs`：**通过 6 / 失败 0 / 退出码 0**。新增假闸门调用记录证明五个构建命令都被封装、顺序正确，启动和关闭服务不使用构建闸门；原有无闸门启动、失败、中断和端口保护用例均通过。
- `node --check`、`bash -n`、`git diff --check`：退出码均为 0。
- 证据：`e2e/artifacts/m1-gate/harness.log`。这些仍是编排自检，不是应用业务结果；本轮 API/UI 未重跑，无产品截图，等待后端阶段合并通知。

## 2026-11-02 · QA-RUN-01 首轮 M1 实测

产品基线 `main af09a2d`；测试期间仅同步了接口文档 `4f81677`（明确搜题关键词必填），运行中的 jar/H5 仍是 af09a2d 代码。范围为账号、分类、搜题、四题型判分、练习/练习来源 RETRY、错题、收藏、设置、已有权限与纠错接口。考试及考试来源 RETRY、统计接口、管理 CRUD/禁用/Excel 尚未实现，本轮跳过；章节进度通过分类接口验证。

本地栈用临时 H2，复制原始 396 道题和 6 份卷后追加 20 道 QA 隔离题，实际导入 416 题。API 各场景独立注册用户，共享只读题库；App 走查使用原始 Java 程序设计题库。后端 Maven 打包、App npm ci 和 H5 构建均退出 0；管理后台未构建。

| 验证 | 通过 | 失败 | 跳过 | 退出码 | 证据 |
| --- | ---: | ---: | ---: | ---: | --- |
| M1 API 最终轮（17 个业务场景） | 15 | 2 | 3 组未实现功能 | 1 | `e2e/artifacts/run-01/api-final.log` |
| 两缺陷最小复现，含关键词缺失/空串对照 | 0 | 3 个请求断言 | 0 | 1 | `e2e/artifacts/run-01/repro-current-contract.log` |
| 编排自检（含 M1 无后台启动、临时种子、/quiz 路由） | 7 | 0 | 0 | 0 | `e2e/artifacts/run-01/harness.log` |
| App M1 主流程（Chromium，iPhone 13 尺寸） | 1 | 0 | 0 | 0 | `e2e/artifacts/run-01/ui-final.log`、`ui-final-results.json` |

接口计数以 node:test 汇总为准：`tests 20 / pass 15 / fail 2 / skipped 3`；3 个 skipped 是范围标记，不代表三条业务用例已通过。最初夹具省略 keyword，造成同一搜题缺陷连带 13 个场景失败；随后保留独立失败用例，夹具显式提供关键词后得到表中结果。收藏清空回传 `null` 或空串都符合“清空”约定，已去掉仅接受空串的过严断言，未将其记录为产品缺陷。

### 已确认问题

| 编号 | 程度 / 归属 | 复现 | 期望 | 实际与证据 | 状态 |
| --- | --- | --- | --- | --- | --- |
| QA-001 | 一般 / 后端 | 注册登录后 GET `/api/questions/search?page=1&size=1`；再传 `keyword=` | 按 4f81677，关键词缺失、空串均返回 400 `VALIDATION_FAILED`，fields 指向 keyword | 缺失返回 500 `INTERNAL_ERROR`，空串返回 200。后端日志 `QuestionService.escapeLike:135` NPE，`search:71` 在判断条件前求值参数；独立请求证据见 repro-current-contract.log | **已修复**：1d8b92f 复测通过，见下节 |
| QA-002 | 一般 / 后端 | 注册登录后 PUT `/api/me/settings`，JSON `{"wrongRemoveThreshold":1.5}` | 非整数返回 400 `VALIDATION_FAILED`，不保存 | 返回 200 `OK`，回传并保存阈值 1；见 repro-current-contract.log | **已修复**：1d8b92f 拒绝小数且设置保持 3；数字字符串另记 QA-003 |

可重跑：在本地栈启动后执行 `BASE_URL=http://127.0.0.1:8080 sozai-lock light -- node scripts/repro-m1-issues.mjs`。输出不含密码、令牌或认证头。未修改任何业务代码。

### App 走查证据

在真实 H5 页面操作注册、选择 Java 程序设计、勾选基础语法章节、随机十题、逐题提交并看解析、看结果、进入错题本、重练。全部写操作经 UI；接口仅作为判定结果与计数的核对来源。实际抽到单选、多选、判断、填空四题型，故意错一道填空，其余九题正确：练习 54 结果为 10 题 / 9 对 / 90%，错题本仅题 204，错题练习 55 答对后 `correctStreak=1`。未捕获 pageerror。

17 张逐步截图：`/data/crew/qzd-5b1e/shots/qa-run-01/2026-11-02T10-49-24-995Z/app-iphone13/`。已查看主页、填空错答与解析、90% 结果、错题本、100% 重练结果。按 AC-01/08、PR-01/06/12、WR-01/03 核对，主流程可完成。

阶段限制：首页会请求尚未实现的 `/api/stats/overview`，显示“接口不存在”短提示，首页统计值为空；按 QA-RUN-01 不计本轮失败。部分首轮截图截在 Toast/“结算中”消退动画内，后续截图等待实际 UI 动画结束，不遮盖或伪造页面。初次 getByPlaceholder 与 uni-input DOM 不匹配导致的超时是测试定位问题；改用字段标签定位 input 后通过，不作为产品缺陷。

本轮只有 H5 Chromium 手机尺寸走查，未测 iOS WebKit、Android/iPhone 真机、安装包、真实断网恢复或七天令牌过期。初始环境已通过 stack:down 停止并删除临时 H2，接着重建 RETEST-01 环境验证修复。

## 2026-11-02 · RETEST-01 修复复测与首轮交付

产品基线 **main `1d8b92f`**（后端修复 `5ca250d`，同时包含题库返修 `55bf366`）。重新构建 jar/H5 并初始化全新临时 H2，未复用旧进程或旧数据库。仍仅验收 M1，不连接外部服务器。

| 验证 / 命令（在 e2e 目录） | 通过 | 失败 | 跳过 | 退出码 | 证据 |
| --- | ---: | ---: | ---: | ---: | --- |
| `QA_SCOPE=m1 bash scripts/stack.sh up` | — | — | — | 0 | `artifacts/retest-01/stack-up.log` |
| `QA_SCOPE=m1 sozai-lock light -- npm run test:api` | 18 | 1 | 3 组未实现功能 | 1 | `artifacts/retest-01/api-final.log` |
| `sozai-lock light -- node scripts/repro-m1-issues.mjs` | 6 请求断言 | 1 请求断言 | 0 | 1 | `artifacts/retest-01/repro-final.log` |
| `QA_SCOPE=m1 QA_STAGE=retest-01 QA_MAIN_COMMIT=1d8b92f heavy sozai-lock heavy -- npm run test:ui` | 1 | 0 | 0 | 0 | `artifacts/retest-01/ui.log`、`ui-results.json` |
| `bash scripts/stack.sh down` 及进程/临时目录/端口复核 | — | — | — | 0 | `artifacts/retest-01/cleanup.log` |

API 汇总为 `tests 22 / pass 18 / fail 1 / skipped 3`。本次将 QA-002 原始小数复现与数字字符串变体拆为独立场景，并增加未知 sort 校验，因此业务场景从首轮 17 个增至 19 个。唯一失败是下述 **轻微 QA-003**，未将 M2/M3 的未实现功能计为失败。

- **QA-001 已修复**：缺失、空串、纯空格、51 字符四种关键词均返回 400 `VALIDATION_FAILED`，`fields` 正确指向 `keyword`；正常搜索、分页和题目详情仍通过。
- **QA-002 已修复**：小数 `1.5` 返回 400，`fields` 指向 `wrongRemoveThreshold`，原设置保持默认 3，不再截断成 1。
- **未知 sort 已验证**：`GET /api/wrong-questions?sort=UNKNOWN` 返回 400，`fields` 指向 `sort`。
- **QA-003，轻微 / 后端，待修复**（按工头 QA-003-ACK 定级）：注册登录后 `PUT /api/me/settings` 发送 `{"wrongRemoveThreshold":"2"}`，预期请求体数字字段只接受原生 JSON 数字并返回 400；实际 200 `OK`，设置保存为 2。复现及返回在 `repro-final.log`，已转后端。此约定针对请求体，查询参数不受影响。

修复版本的 App 主流程 **1/1 通过，退出 0**，执行约 21.4 秒。实际用户 24、练习 28：十题含四题型，答对九题、错误题 197，结果页 90%；错题练习 29 答对后结果 100%，`correctStreak=1`，无 pageerror。结果核对 JSON：`artifacts/retest-01/walk-result.json`。两次首页统计请求均为预期未实现的 404，按 M1 范围记录为阶段限制。

17 张稳定截图位于 `/data/crew/qzd-5b1e/shots/qa-retest-01/2026-11-02T10-56-22-506Z/app-iphone13/`。已视觉核对主页、首题错答与正确选项标记、90% 结果、错题本及100% 重练结果；截图等待实际 Toast/加载动画消退，不隐藏页面元素。主要文件：`02_AC-08_选择Java考试.png`、`05_PR-12_结果90百分比.png`、`06_WR-01_进入错题本.png`、`08_WR-03_重练结果与连对计数.png`。

环境收尾：后端 PID 194047、App PID 194367 已退出；状态文件和临时 H2 目录已删除；8080、4173 均已重新绑定验证可用。没有留下 watch、服务或构建进程。此次仅修改 `e2e/` 和本报告，不修改业务实现；测试/报告 commit 见班子 worklog-qa.md。后续等待 QA-003 修复及 M2/M3 合并通知再扩展验证。
