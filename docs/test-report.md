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
- **QA-003，轻微 / 后端，第一轮待修复，第二轮已验证修复**（按工头 QA-003-ACK 定级）：注册登录后 `PUT /api/me/settings` 发送 `{"wrongRemoveThreshold":"2"}`，预期请求体数字字段只接受原生 JSON 数字并返回 400；第一轮实际 200 `OK`，设置保存为 2。原证据在 `repro-final.log`；db63cdc 的修复证据见下方 QA-RUN-02。此约定针对请求体，查询参数不受影响。

修复版本的 App 主流程 **1/1 通过，退出 0**，执行约 21.4 秒。实际用户 24、练习 28：十题含四题型，答对九题、错误题 197，结果页 90%；错题练习 29 答对后结果 100%，`correctStreak=1`，无 pageerror。结果核对 JSON：`artifacts/retest-01/walk-result.json`。两次首页统计请求均为预期未实现的 404，按 M1 范围记录为阶段限制。

17 张稳定截图位于 `/data/crew/qzd-5b1e/shots/qa-retest-01/2026-11-02T10-56-22-506Z/app-iphone13/`。已视觉核对主页、首题错答与正确选项标记、90% 结果、错题本及100% 重练结果；截图等待实际 Toast/加载动画消退，不隐藏页面元素。主要文件：`02_AC-08_选择Java考试.png`、`05_PR-12_结果90百分比.png`、`06_WR-01_进入错题本.png`、`08_WR-03_重练结果与连对计数.png`。

环境收尾：后端 PID 194047、App PID 194367 已退出；状态文件和临时 H2 目录已删除；8080、4173 均已重新绑定验证可用。没有留下 watch、服务或构建进程。此次仅修改 `e2e/` 和本报告，不修改业务实现；测试/报告 commit 见班子 worklog-qa.md。后续等待 QA-003 修复及 M2/M3 合并通知再扩展验证。

## 2026-11-02 · QA-R1-OK 后 M2/M3 用例预编写

基线 `main f549fb5`。本轮按工头指令先准备接口用例，**没有启动产品环境，没有执行 M2/M3 业务测试**；下述 skipped 是显式“仅收集”，不能作为功能通过证据，也不同于首轮按未实现范围跳过的三组标记。

| 检查 | 通过 | 失败 | skipped | 退出码 | 证据（e2e/ 下） |
| --- | ---: | ---: | ---: | ---: | --- |
| `QA_SCOPE=m2 QA_COLLECT_ONLY=1 sozai-lock light -- npm run test:api` | 0 | 0 | 12 | 0 | `artifacts/m2-m3-prep/m2-collection.log` |
| `QA_SCOPE=m3 QA_COLLECT_ONLY=1 sozai-lock light -- npm run test:api` | 0 | 0 | 15 | 0 | `artifacts/m2-m3-prep/m3-collection.log` |
| `sozai-lock light -- node --test scripts/check-harness.mjs` | 8 | 0 | 0 | 0 | `artifacts/m2-m3-prep/harness.log` |
| `sozai-lock light -- node --test scripts/check-offline-proxy.mjs` | 1 | 0 | 0 | 0 | `artifacts/m2-m3-prep/proxy.log` |
| `QA_SCOPE=m2 npm run test:api`（无服务预检） | 0 | 0 | 0 | 2 | `artifacts/m2-m3-prep/no-service.log`；提示服务未启动，未运行业务场景 |

新增文件 `api/m2.test.mjs`（12 场景）、`api/m3.test.mjs`（15 场景）。M2 使用临时种子建立三份固定/规则卷，包括一分钟卷，避免依赖 M3；M3 通过管理员接口独立建数据。编排与代理检查使用假服务，仅验证测试工具自身。

M2 对应 EX-01/02/03/07/08/09/10/11/12/13/14/15/16/19/20/21/23/24、ST-01/02/03/04/07/08/09、DY-08、NF-01 的相关接口行为：断开传输与补发、版本防覆盖、并发交卷幂等、加权分数、未答排除、截止拒收、后台结算、记录/最好成绩、规则去重、统计对账和周榜。M3 对应 AD-01～11、AD-13～22 与 EX-17/18/21/22、CA-04/09、NF-01 的相关接口行为；没有覆盖上传图片 UI 的 AD-12。

边界说明：断网代理只验证服务器在收到/未收到请求时的行为，不声称测过 App 离线队列。自动结算场景不提前读取记录详情，先检查统计；真实执行时还需确认统计读取不会主动触发结算。跨北京时间零点、周界、历史连续打卡仍待受控时间/历史夹具，当前没有证据。API 没有规定部分业务限制的精确 HTTP 错误码时，用例只接受 4xx 并验证没有完成非法发布或修改，不把 500 当作合法拒绝。

所有新增 JS 通过语法检查，`git diff --check` 退出 0。无产品截图、无新增业务缺陷结论。等待后端 M2/QA-003、M3 合入后的实跑通知。

## 2026-11-02 · QA-RUN-02 M1＋M2 接口与 App 考试实测

产品基线 **main `db63cdc`**。已阅读 `api.md` 0.1、0.2、6 节新增约定，重新构建后端/H5，使用临时 H2：原始 396 题加 20 道验收题，原始 6 卷加 3 份验收卷，导入日志为 **416 题、9 份试卷**。管理接口仍在 M3，不执行、不算失败。

### 实际执行与计数

命令在 `e2e/`，BASE_URL 为 `http://127.0.0.1:8080`。下列为每次执行的原始汇总，重复覆盖的用例不相加为总通过数。

| 命令 / 检查 | 通过 | 失败 | skipped | 退出码 | 证据（e2e/ 下） |
| --- | ---: | ---: | ---: | ---: | --- |
| `QA_SCOPE=m2 bash scripts/stack.sh up` | — | — | — | 0 | `artifacts/run-02/stack-up.log` |
| `QA_SCOPE=m1+m2 sozai-lock light -- npm run test:api`（已有核心用例） | 32 | 0 | 1（M3） | 0 | `artifacts/run-02/api-initial.log`；约 175.9 秒 |
| `sozai-lock light -- node scripts/repro-m1-issues.mjs` | 7 请求断言 | 0 | 0 | 0 | `artifacts/run-02/qa003-retest.log` |
| `QA_SCOPE=m1+m2 sozai-lock light -- node --test api/m1.test.mjs`（加入原生字符串校验后） | 19 | 1 | 1（M3） | 1 | `artifacts/run-02/m1-with-new-contract.log` |
| `sozai-lock light -- node scripts/repro-native-strings.mjs` | 0 | 1 | 0 | 1 | `artifacts/run-02/native-string-gap.log` |
| `QA_SCOPE=m2 QA_STAGE=run-02-stable QA_MAIN_COMMIT=db63cdc sozai-lock light -- npm run test:ui` | 1 | 0 | 0 | 0 | `artifacts/run-02/ui-stable.log`、`ui-stable-results.json`；约 71 秒 |
| `sozai-lock light -- node scripts/check-result-fields.mjs artifacts/run-02/walk-result.json` | 0 场 | 2 场响应未符合 | 0 | 1 | `artifacts/run-02/result-field-gap.log` |
| `bash scripts/stack.sh down` 及清理复核 | — | — | — | 0 | `artifacts/run-02/cleanup.log` |

核心 M1 的 19 项和 M2 的 13 项均通过。后加的原生字符串契约校验未通过；另有成绩单新增字段未交付，详见已知差距表。因此不能将本轮描述为“全部接口约定已通过”。M1 重跑只针对有新增断言的文件，M2 复用同一产品代码本轮已通过的 13 项，不重复等待全部截止用例。M3 仅保留一组明确的范围 skip。

**QA-003 已修复**：`wrongRemoveThreshold:"2"` 返回 400 `VALIDATION_FAILED`，fields 正确指向字段。原 QA-001 四种非法关键词、QA-002 小数、未知 sort 同样返回 400，共七个请求断言通过。

M2 已验证：进行中同卷复用且不计已考次数、答案保密/查题限制、旧/同版本拒绝、重连补传、并发交卷幂等、1/2/3/4 分加权判分、未答不计正式作答/错题、考试 RETRY、截止拒收、周期结算、最好成绩、规则交集去重、所有权、分类/趋势/薄弱点、并列排名（1、1、3）、统计参数边界。截止用例真实等待约 62 秒；后台结算用例真实等待约 95 秒且期间不调用记录详情/交卷。已检查 `StatsService`：统计只读取已结束记录，没有调用结算逻辑，所以查询统计没有替代定时任务完成该证明。超时 `submittedAt` 与 `deadlineAt` 完全一致。

### App 真实用户走查

单个 Chromium 浏览器，iPhone 13 尺寸，真实本地 API；通过 UI 登录、开考、选项/填空、标记、交卷和跳转。API 只用于准备隔离账号/试卷与核对结果，没有替代作答操作。

稳定轮用户 67、固定卷记录 13、一分钟卷记录 14：

1. 首题保存并标记“稍后再看”，服务器版本 2。
2. `context.setOffline(true)` 后答多选 A/C，页面显示未同步，服务器未收到该题。
3. 点交卷并确认，出现“还不能交卷”；未发出 submit 请求，服务器仍 IN_PROGRESS。
4. 恢复网络后自动补传，多选答案 A/C、版本 2，页面变为“答案已保存”。
5. 再次离线答第三题，关闭页面，再恢复网络、新开页面，从“继续考试”返回同一 record；本地待同步答案自动上传，标记仍在，截止时间不变。
6. 未答一题时交卷提醒可取消，补齐填空后手动交卷，4 对、10/10 分。
7. 统计页六项分别为累计 4、正确率 100%、今日 4、学习 1 天、连续打卡 0 天、考试 1 次，与 API 一致。
8. 开一分钟卷只答首题，真实等待到时，浏览器自动发出第二次 submit，成绩 1/10 分、1 对、3 未答。`submittedAt=deadlineAt=2026-11-02T11:28:32.323Z`，页面显示北京时间 19:28。
9. 统计页变为累计 5、正确率 100%、今日 5、学习 1 天、打卡 0 天、考试 2 次；3 道未答不进入分母。排行榜本人 5 次、排名 10，与接口一致，列表条数一致。无 pageerror。

15 张稳定截图：`/data/crew/qzd-5b1e/shots/qa-run-02-stable/2026-11-02T11-27-23-827Z/app-iphone13/`。已视觉检查未同步状态、交卷拦截弹窗、重开恢复标记、手动成绩、自动成绩和统计页面。关键文件 `05_EX-08_未同步交卷被拦截.png`、`08_EX-12_关页重开恢复原场并补传.png`、`13_EX-06_真实等待截止自动交卷.png`、`14_ST-02_到时交卷后统计再次对账.png`。截图将有限 CSS 动画推进到结束，避免截在弹窗透明/路由过渡帧；没有隐藏或改写产品内容。第一次走查亦通过，原始截图和日志保留在 `qa-run-02/` 与 `ui-initial.log`。

结构化证据 `artifacts/run-02/walk-result.json` 含记录号、版本、两次 submit 路径、统计响应和两个真实成绩单，不含令牌或密码。关页重开是 H5 浏览器上下文内的恢复，不代表真机杀进程或设备重启验证。

### 已知待交付约定与限制

两项均是工头已告知后端、计划随 M3 补齐的新约定，本轮保留未符合证据，不重复分派为新缺陷号。

| 编号 | 程度 / 归属 | 复现 / 期望 | 实际 / 证据 | 状态 |
| --- | --- | --- | --- | --- |
| R2-K01 | 轻微 / 后端 | 开练习后提交 `userAnswer:[1]`；api 0.1 要求每项原生字符串，应 400 且不记作答 | 第二轮 200 `OK`，回传 `userAnswer:["1"]`；原证据 `native-string-gap.log` | 第三轮 **已修复**：400，字段 userAnswer[0]，未增加作答 |
| R2-K02 | 轻微 / 后端 | 查看已结算成绩单；api 6 新增 items.blankCount、imageUrl 字段应完整返回 | 第二轮两份成绩单均缺两个字段；原证据 `result-field-gap.log` | 第三轮 **已交付**：接口断言通过，带图题成绩单也返回真实上传路径 |

本轮未测 M3 管理/Excel、已另行交给前后端修复的 PM-M3 问题、跨日/跨周历史边界、原生 App 真机/安装包。没有把这些缺证据项标为通过。清理已确认：后端 PID 238463、App PID 238666 退出，状态文件及临时 H2 删除，8080/4173 可重新绑定；所有测试进程均已结束。此次只改 e2e/ 和本报告。

## 2026-11-02 · QA-RUN-03 本地全接口、管理后台与 M4 补测

接口产品基线 `00d806c`；随后按 QA-INFO-02 同步前端 M4 `a05d377` 并重建 UI。两基线间后端生产源码和 pom 未变（只新增性能测试类），因此复用已通过的全接口结果，没有重复跑未变代码。选项打乱和预发布验证按后续通知另节记录，不混入本节。

| 命令 / 检查 | 通过 | 失败 | skipped | rc | 证据（e2e/ 下） |
| --- | ---: | ---: | ---: | ---: | --- |
| `sozai-lock light -- node --test scripts/check-harness.mjs` | 11 | 0 | 0 | 0 | `artifacts/rule-kill/harness.log` |
| `QA_SCOPE=all bash scripts/stack.sh up` | — | — | — | 0 | `artifacts/run-03/stack-up.log`、`artifacts/run-03-m4/stack-up.log` |
| `QA_SCOPE=all sozai-lock light -- npm run test:api` | 48 | 0 | 0 | 0 | `artifacts/run-03/api-initial.log`；约179.4秒 |
| `sozai-lock light -- node scripts/repro-native-strings.mjs` | 1 | 0 | 0 | 0 | `artifacts/run-03/native-strings.log` |
| `QA_SCOPE=m3 QA_STAGE=run-03-admin QA_MAIN_COMMIT=a05d377 sozai-lock light -- npm run test:ui` | 1 | 0 | 0 | 0 | `artifacts/run-03-m4/admin-ui.log`、`admin-ui-results.json`、`admin-walk.json` |
| `QA_SCOPE=pm QA_STAGE=run-03-pm QA_MAIN_COMMIT=a05d377 sozai-lock light -- npm run test:ui` | 1 | 0 | 0 | 0 | `artifacts/run-03-m4/pm-ui.log`、`pm-walk.json` |
| `QA_SCOPE=settings QA_STAGE=run-03-settings QA_MAIN_COMMIT=a05d377 sozai-lock light -- npm run test:ui` | 1 | 0 | 0 | 0 | `artifacts/run-03-m4/settings-ui.log`、`settings-walk.json` |

全接口 **48/48，通过且0跳过**，包括 M1、M2、全部 M3 管理接口、Excel四题型/错误行/模板/无效文件。R2-K01 原生字符串已返回400并指明 `userAnswer[0]`；R2-K02 在手动/超时成绩单字段断言中通过，后台走查带图题的成绩单返回 `blankCount=0` 和真实 `imageUrl`。本地没有新增已确认业务缺陷。

**后台实证**：普通用户网页登录被拒且管理接口403；UI建立分类63/64/65，录入题417，Java代码预览高亮，图片真实上传并显示。Excel三行中两行成功、一行失败（第4行，答案Z不存在）。固定卷10上架；规则卷11要求5道单选但仅2道，显示缺口并返回409，修改为2道后成功上架。纠错采纳并保存回复。用户2被禁用后新登录及旧令牌均403。看板真实值为用户1、已发布题419、试卷11、今日作答1、待处理0，五项与API一致且相对于本轮初值增量正确；固定卷1次考试、均分5、及格率100%。

**PM复测**：

- PM-M3-01 已修复：同一用户在两个考试各有一道错题/收藏；切目标后列表、分组、收藏和“重练全部”均只含当前考试那道题。
- PM-M3-02 已修复：已结束练习回看未答题显示“未作答”，无提交按钮；点击选项不发提交请求，作答数保持1，顶栏可返回原结果。
- PM-M3-03 已修复：仅中断新目标分类请求，新目标下不残留旧科目，显示原因和重试；恢复请求后只显示新目标科目。
- PM-M3-04 已修复：成绩单展开未答填空题显示中性的“— 未作答”，不再显示“回答错误”；总览0错/3未答保持正确。
- PM-M3-05 已修复：A考试已有5题40%的薄弱点；切到B并仅中断weak请求，B总览0作答、薄弱点为空且显示B专属失败/重试，不残留A弱项；恢复后单模块重试得到B空结果。
- 设置页已测：字号从16px变为19px，夜间模式作用于页面与导航；错题移出阈值从3改4，重载后字号/夜间/阈值均保持；错旧密码返回400，正确改密后旧密码401、新密码可登录。H5显示“网页版总是最新”和“不支持提醒”的说明；原生升级安装/系统通知没有伪称通过。

稳定截图共 **30张**（已查看关键页面）：

- 后台12张：`/data/crew/qzd-5b1e/shots/qa-run-03-admin/2026-11-02T12-24-12-851Z/admin-desktop/`
- PM-M3-01/02/03八张：`/data/crew/qzd-5b1e/shots/qa-run-03-pm/2026-11-02T12-26-26-916Z/app-iphone13/`
- 设置及PM-M3-04/05十张：`/data/crew/qzd-5b1e/shots/qa-run-03-settings/2026-11-02T12-30-46-343Z/app-iphone13/`

后台前两次退出1是测试定位校准：级联标签带题量后缀，复选框的原生input隐藏、可见外层才是点击目标。均保留原日志，未当作产品缺陷；校准后完整流程通过。

**RULE-KILL-01 已落实**：QA随机端口和地址自动发现；服务PID/cwd/启动时间登记并逐个核验，cwd必须属于本工作树，发现错配保留状态拒绝停止；取消构建/测试同样只对已验证进程树逐个发正PID信号，不按名字/端口或负PID组杀。隔离自检包含另一模拟工作树的同名服务保护。后端cwd改为本工作树backend，数据/上传仍在独立临时目录。新用户名统一qa_前缀。最终清理证据追加在对应阶段日志，后续选项打乱使用新环境。

## 2026-11-02 · 选项打乱本地补测

产品基线 `a0dae28`（后端打乱 `e884c9f`，前端打乱已合入），独立新栈。

| 检查 | 通过 | 失败 | rc | 证据（e2e/ 下） |
| --- | ---: | ---: | ---: | --- |
| `QA_SCOPE=shuffle sozai-lock light -- npm run test:api` | 4 | 0 | 0 | `artifacts/shuffle/api.log` |
| `QA_SCOPE=shuffle QA_STAGE=shuffle QA_MAIN_COMMIT=a0dae28 sozai-lock light -- npm run test:ui` | 1 | 0 | 0 | `artifacts/shuffle/ui.log`、`walk-result.json` |

API核对新标签与选项内容/原标签为一一映射，判断/填空不打乱；新标签判分正确、原正确字母在换位后判错，错题本保留原标签；练习改选项数量后回退原序，非法标签判错；考试保存、快照、结算和成绩单映射一致。实际差分样本为“原A正确，显示A对应原B，因此显示B才正确”。

页面实际样本：练习映射 `{A:C,B:B,C:A}`，选显示C判对，旁注“原选项A/对应解析里的A”；考试12道单选按显示标签全部答对，12/12分，成绩单展开也能对照原选项。6图位于 `/data/crew/qzd-5b1e/shots/qa-shuffle/2026-11-02T12-49-55-697Z/app-iphone13/`，已查看练习解析和成绩单。未将随机恰好保持原位的题当作换位证明。本地栈已关闭，`artifacts/shuffle/cleanup.log`。

## 2026-11-02 · STAGING-01 预发布首轮（历史失败，QA-004复测见下节）

目标为已授权的 [App H5](https://43.142.166.108/quiz/) 和 `/quiz/api`。工头 QA-004-ACK 确认本轮预发布版本为 **e884c9f**。curl使用 `--noproxy '*'`，Node清除代理变量，Chromium使用 `--no-proxy-server`；HTTPS正常验证通过，没有忽略证书。凭证只由测试进程读取，没有打印或写入报告；新测试账号均qa_前缀。仅用应用HTTP接口，没有服务器/数据库管理操作。

通过管理API新增独立夹具根 `QA staging a6854f4ea6`（7分类/20题/3卷），没有改动已有题库。共享环境有人同时验收，线上不严格比较全站题数、今日作答和待处理纠错的前后增量；仍验证本测试章节、本账号、本试卷，且日志显式注明该限制。本地对应全站增量已有通过证据。

| 命令（前缀均 `sozai-lock light -- node scripts/staging.mjs`） | 通过 | 失败 | rc | 证据（e2e/ 下） |
| --- | ---: | ---: | ---: | --- |
| `prepare` | — | — | 0 | `artifacts/staging/prepare.log` |
| `api` | 47 | 1 | 1 | `artifacts/staging/api.log`；0 skip，约440.5秒 |
| `shuffle-api` | 4 | 0 | 0 | `artifacts/staging/shuffle-api.log` |
| `app` | 1 | 0 | 0 | `artifacts/staging/app.log`、`app-walk.json`；约44.7秒 |
| `exam` 首次 | 0 | 1 | 1 | `artifacts/staging/exam.log`：重开时导航等待20秒超时 |
| `exam` 保持原超时复跑 | 1 | 0 | 0 | `artifacts/staging/exam-retry.log`、`exam-walk.json`、`exam-network.json`；约152.9秒 |
| `repro-submit` 三场独立复现 | 0 | 3 响应一致性检查 | 1 | `artifacts/staging/qa004-repro.log` |
| `upload` | 1 | 0 | 0 | `artifacts/staging/upload.log`：201上传/200静态读取，608字节SHA一致 |

**QA-004，一般 / 后端 / 首轮失败（本次复测见下节）**：同一场考试保存单选正确、多选少选、判断正确、填空未答，预期4/10分；同时发送两个 `POST /api/exam-records/{id}/submit`，两份响应应与随后GET一致。首轮record12返回4分与0分不一致。为区分返回过期与重复结算，新注册一次性账号，在record23/24/25连续三场重现：一份 `FINISHED/4分/2正确`，另一份 `IN_PROGRESS/0分/submittedAt=null`；随后GET每次都是 `FINISHED/4分/2正确`。作答次数按3/6/9、考试次数按1/2/3增长，未观察到这些计数重复；证据支持“返回过期视图”。原record12的临时令牌未保存，**没有冒称直接GET过12**。工头已转后端，待新版本部署后复测；脚本后续也加入错题次数核对。

线上App已实际完成注册→Java目标→随机十题→9对1错/90%→错题重练，及考试断网拦截→恢复补传→关页重开→手动10分→一分钟到时1分→统计4次/1场到5次/2场的对账。考试重跑无pageerror。首轮重开导航20秒超时在重跑中未复现，保留原失败证据。此前记录的JS下载13.1秒/15.5秒属于新加坡至上海的跨境静态传输，不能作为接口性能证据，也不记为产品缺陷。按工头补充，浏览器取得gzip包且有缓存；本轮只记录API请求往返耗时，页面导航等待调整为90秒以容纳静态资源传输。

已查看关键截图：App17张 `/data/crew/qzd-5b1e/shots/qa-staging-app/2026-11-02T13-11-15-665Z/app-iphone13/`；考试成功轮15张 `/data/crew/qzd-5b1e/shots/qa-staging-exam/2026-11-02T13-24-35-256Z/app-iphone13/`。预发布此版本还未确认部署前端打乱，因此该项线上UI等待新版通知，本地已通过。没有留下本地产品服务；线上测试数据按工头正式发布前重置安排保留，未擅自删除共享数据。

## 2026-11-02 · 82e795f 预发布 QA-004 与选项打乱复测

工头确认部署基线 **82e795f**（insert-first、提交/交卷用户锁、V3选项乱序迁移），本工作树已合入。沿用独立夹具根 `QA staging a6854f4ea6`；新增账号仍为qa_前缀。只通过获授权的应用HTTP接口与页面操作，代理绕过、TLS正常校验；没有读取服务器数据库或更改部署。

**QA-004交卷已复测通过**（EX-07/15/16/24、ST-02/03）：独立脚本连续三场 record211/212/213，各同时提交两次。6份响应及3份后续GET都为 `FINISHED/score=4/correctCount=2`，完整对象相同。统计累计作答3/6/9、正确2/4/6、考试1/2/3；同一道多选错题次数1/2/3，没有旧响应，也未重复计数。正式M2套件另一次record216同样两响应/GET均4分。旧版e884c9f的首轮失败证据保留在上一节。

本次API原套件启动时为48场景；开发期间新增的3项练习并发另行运行，每项3轮，不用“重新跑过全套”的说法覆盖首轮。后续 `QA_SCOPE=all` 已包含这3项；4项选项打乱仍是独立分组。每次只对失败或新增范围做补测。

| 命令（前缀均 `STAGING_COMMIT=82e795f sozai-lock light -- node scripts/staging.mjs`） | 通过 | 失败 | skipped | rc | 证据（e2e/artifacts/staging-82e795f/） |
| --- | ---: | ---: | ---: | ---: | --- |
| `repro-submit` | 3轮 | 0 | 0 | 0 | `qa004-exam.log` |
| `api` 原48场景首轮 | 47 | 1 | 0 | 1 | `api.log`，578.0秒 |
| `QA_TEST_NAME=EX-01/02/03/19 … api` 定点复测 | 1业务场景 | 0 | — | 0 | `api-retest.log` |
| `concurrency-api` | 3（共9轮） | 0 | 0 | 0 | `concurrency.log`，28.3秒 |
| `shuffle-api` | 4 | 0 | 0 | 0 | `shuffle-api.log`，36.7秒 |

**接口总数与失败数**：55个不同业务场景（原48 + 新并发3 + 打乱4）。首轮54通过/1请求失败；失败项定点复测通过后，55项均有通过证据，剩余失败0。独立交卷3轮是额外重复证明，不重复计入55。定点复测的Node汇总显示4个通过节点，其中3个是无匹配用例的文件容器，只有1个实际业务用例；没有把空容器算作业务覆盖。

原48场景唯一首轮失败为 EX-01/02/03/19：准备夹具时 `GET /api/questions/402` 没取得响应，尚未进入考试业务断言；没有收到错误业务结果。初版catch未保留异常类型，不能把它精确归因为超时或服务端故障。保持12秒单次API限制与原业务断言，完整重跑该场景通过；新增请求错误类型/耗时日志用于后续区分，未设置自动重试掩盖错误。本轮未新增已确认业务缺陷。

**练习并发实证**（PR-12、WR-03、ST-03）：

- 同练习同题（session296/297/298），同时提交A与B：每轮两份判分/答案完全一致，一份 `alreadySubmitted=false`、一份true；GET保留同一答案，完成数1，累计统计1/2/3。
- 同题跨两份练习（299–304）同时答错：每份均只记一次，两次独立有效提交都保留，错题次数和累计作答均为2/4/6。
- 同练习不同题（305/306/307）同时答对：每份answeredCount/correctCount均为2，结束结果一致，累计作答/正确均为2/4/6，没有丢失更新。

**仅API计时**：本轮Node接口调用记录了1018个完整HTTP响应，往返耗时中位257ms、P95 1331ms、最大5769ms，汇总为 `api-timing-summary.json`；原始文件为 `api-timings.jsonl`、`concurrency-timings.jsonl`、`shuffle-timings.jsonl`、`api-retest-timings.jsonl`。其中包含72个用例预期的4xx校验响应；唯一未取得响应的请求不在完整响应分布中，已单列失败记录。这些数值包括新加坡至上海的网络传输，且混合了不同读写接口，不代表服务端处理性能，也不据此评判服务端0.5秒指标。静态JS下载不纳入统计、不列缺陷。

**后台 → App 选项打乱页面通过**（AD-08、EX-07/10、D2）：`admin-shuffle-ui` 最终1通过/0失败/0跳过，rc0，98.9秒。通过管理员页面编辑本人新建的卷27，将 `shuffleOptions=false` 改为true，保存并上架；列表出现“选项随机”。随后同一浏览器切换到手机尺寸的独立学员上下文，用户149在App看到随机选项须知，开始考试record229。

第1题（question550）实际显示顺序为 **A乙（原B）、B甲（原A）、C丙（原C）**。页面逐项核对内容与映射；故意选显示A，其余11题按映射选原正确项，交卷得到 **11/12分、11对1错、0未答**，后续GET完整成绩单与交卷响应一致。错题解析中显示A为红色“你选的”、显示B为绿色“正确答案”，B旁注明“原选项A”，答案栏注明“对应解析里的A”；错题本 `lastWrongAnswer=["B"]` 保留原标签。无pageerror。

最终证据：`admin-shuffle-ui-final.log`、`admin-shuffle-final-results.json`、`admin-shuffle-walk.json`。六张稳定截图目录 `/data/crew/qzd-5b1e/shots/qa-staging-admin-shuffle-ui/2026-11-02T14-25-30-216Z/admin-desktop/`；已查看后台上架、总分、展开错题解析。目录名沿用本脚本的admin项目名，第3–6图实际是同一浏览器中的手机上下文。后台身份栏已遮挡，认证响应不附入证据。

UI前两次尝试也保留：第一次rc1是隐藏的Element Plus原生switch输入框无法点击，校准为可见外层；第二次rc1是登录成功后等待SPA菜单15秒未出现。源码确认此处存在异步页面模块，给这些导航边界使用90秒等待后全流程通过；没有据此认定静态资源为产品缺陷，也没有删除原日志 `admin-shuffle-ui.log` / `admin-shuffle-ui-retry.log` 与对应initial/navigation结果JSON。

范围限制延续：共享环境不严格断言全站增量；H5手机模拟不替代真机。此轮不重复未变化的注册/错题重练全流程（上一预发布轮已有实证），重点完成本次并发和打乱任务。结束时核对本轮15份登记进程均已退出，本工作树没有残留浏览器（`cleanup.json`），没有启动本地产品服务；线上夹具保留供审阅，重置由工头安排。

## 2026-11-03 · MOCK-QA 第一版演示库核对

**结论：原库数据一致性及副本接口对账通过；已知学习时段重叠保留为待v2复测项，不重复报缺陷。** 核对依据为 `e2e/mock-check.md`。原库 `127.0.0.1:13306/quiz_mock`，生成jar为工头提供的 `85775e1`；接口用本工作树main基线 **e69b726** 构建的jar，连接固定副本 `quiz_mock_check`。本轮无线上请求，也没有重跑生成器。

### 执行、计数与校准记录

证据均在 `e2e/artifacts/mock-v1/`，源库/副本结束快照另存 `artifacts/mock-source-after/`、`artifacts/mock-copy-after/`。小脚本均使用light锁；jar打包经heavy→sozai-lock heavy，rc0（`artifacts/mock-build.log`），不是后端单测结果。

| 项目 | 实际结果 | rc | 证据 |
| --- | --- | ---: | --- |
| M00–M03：原库一致性快照 | 13张业务表，排除密码散列，只读事务 | 0 | `snapshot-summary.json`、`snapshot-query.sql`、`snapshot.json` |
| 清单SQL | 10组实际SQL执行完成；时间、引用、错题次数、考试分、日练异常查询无异常 | 0 | `checklist-sql.log`、`checklist-sql.tsv`、`checklist-executed.sql` |
| S01–S11：全库独立核对 | **23项通过，0失败**；含全部练习/考试作答重判、24564条错题状态重放 | 0 | `sql-audit.json`、`sql-audit.log` |
| A01–A07主轮 | 1652个HTTP请求，1951次断言，1950通过/1测试期望校准 | 1 | `api-audit-initial.json`、`api-audit-initial.log`、`api-requests-initial.json` |
| A07后台定点复测 | 15请求、11断言，全通过 | 0 | `api-audit.json`、`api-retest.log` |
| A04/A05/A07补充详情 | 317请求、731断言，全通过 | 0 | `api-details.json`、`api-details.log` |
| R01–R08真实感分布 | 完成下述分布审阅；已知交替仍存在 | 0 | `realism.json`、`realism.log` |
| G06原库/副本完整性与停服 | 原库未变；副本仅授权字段变化；已停自己的后端 | 0 | `integrity.json`、`stack-down.log`、`stop-processes.json` |

API总计 **1984个请求**（含定点复测15个），主轮及补充合计 **2682次断言**；后台复测的11次不再重复计入该覆盖数。校准后无剩余接口差异。覆盖全部300学员登录状态（4禁用返回403），296正常学员资料及默认目标overview；35名分层样本覆盖7画像、3考试、全部15名换目标用户，按三个考试分别核对daily/categories/weak/overview；错题全分页及分组、历史范围、日练详情、收藏笔记、排行榜；另抽31场考试核对成绩单原标签映射，补充每卷次数/最好成绩和stats/exams；后台全分页用户/题目/试卷/纠错及看板字段对账。没有把抽样接口称作300人全部接口全量验证。

两处测试校准保留了原失败：清单的日期会话变量在CASE分支引发MySQL字符集混用，显式CAST为DATE后10组SQL全过（`checklist-sql-initial.log`）；主API错误地期待后台用户列表仅300学员，实际依法包含1管理员共301人，看板userCount才过滤管理员。改正期望后仅复测后台范围，没有放宽业务断言或自动重试掩盖失败。这两项不是数据/业务缺陷。

### 一致性实证（S01–S13、A01–A07）

- 用户301（300学员+1管理员），正常学员296、禁用4；画像与username一一对应，注册北京日期、最终目标、阈值、头像有无均对齐。无未来行为、无早于注册行为、无孤儿作答/重复来源题记录，背题没有正式作答。
- 总作答 **196919**：练习173798、考试23121；正式作答answered=true为 **195995**，考试未答924不进统计/错题。练习16846场、考试1050场、错题历史24564条、收藏4779条、纠错254条；分类55、题486、试卷12、固定卷关系60、规则77。逐表与生成汇总一致。
- 对全部练习和全部考试作答独立按四题型规则重判，无不一致；考试总分=每题得分之和=正式作答判分汇总，满分、及格、手动/超时时间和未答记录均一致。已发布后才有作答，新增90题的创建日及最终状态与JSON一致。
- 24564条错题按同用户业务顺序重放，含已移出项：错误次数、最后错答案原标签、WRONG/RETRY连对、移出/再错恢复、时间字段一致。普通练习/考试答对不误增连对。
- 日练唯一日期组、全部提交才完成，完成日与北京时间一致；overview连续打卡与独立日期集合计算一致。统计章节用历史章节，分类树用当前已发布题，未混淆两种口径。周榜以本次API日期2026-11-03的周一00:00北京时刻起算，禁用用户排除、并列名次及榜外本人均核对。
- 后台看板：学员 **300**、已发布题 **471**、试卷 **12**、当日正式作答 **445**、待处理纠错 **69**；14日曲线及12卷场次/均分/及格率全部与表聚合一致。

生成日志没有单独保存精确until。时间核对使用 **2026-11-03T03:05:14.023Z**（SeedLoader完成日志）的保守界限：SeedLoader的Order20先于MockRunner的Order100，后者再捕获until；全部实际事件最晚03:05:06，早于该界限，故没有生成截止之后的行为。考试deadline是计划时间，不当作已发生行为。原始汇总见 `board/be-evidence/MOCK-BE-full-run.log`，1353秒、rc0；原数据JSON哈希保存在 `realism.json`。

### 真实感实证（R01–R08）

| 画像 | 人数 | 正式作答数 | 按作答量加权正确率 |
| --- | ---: | ---: | ---: |
| TOP | 24 | 43310 | 80.57% |
| STEADY | 90 | 87175 | 72.30% |
| CRAMMER | 36 | 30125 | 71.51% |
| WEEKEND | 45 | 22484 | 66.66% |
| CASUAL | 45 | 8558 | 60.65% |
| DROPOUT | 45 | 3697 | 56.21% |
| NEWBIE | 15 | 646 | 62.07% |

三考试最终人数150/90/60；408、Java、数据库正式作答正确率分别 **65.86%/77.38%/77.76%**，有预期差异。每人正确率分布：20–40% 4人、40–60% 102人、60–80% 156人、80–100% 33人，5人尚无正式作答；不是所有人同一正确率。画像内均值和P10/P50/P90另见JSON，未把加权率当作平均个人率。143人满足存续至少28天、首末14日各至少50条的进步比较条件，中位提升21.5个百分点；包含难度、重做和题目选择影响，不声称等于画像概率终点。

注册月分布7月至11月为 **80/46/93/63/18**；11月仅截至3日上午，未与整月生硬比较。150个用户名无数字尾缀，学习小时组合223种、星期权重300种，15人切换目标，120人无头像。已抽看每画像的用户名/昵称和参数样例，存在中文昵称、英文名、网名及带符号昵称，参数有个体差异。

练习作答中 **56.97%（99010/173798）发生在北京18–23点**；全部提交落在画像hours本小时或相邻一小时。WEEKEND的711个有练习作答用户日中592个在周末，占 **83.26%**。日作答条数P10/P50/P90为10/22/40。考试按开始/保存时间另列，不把交卷统一记分时刻误当逐题学习时刻。

收藏4779条，其中2602带笔记；纠错155已解决、30已拒绝、69待处理，已处理185条均在提交后1–3个北京日，内容/回复与API一致；未替换的 `{option}` / `{chapter}` 为0。头像仅核对180个数据库路径及画像标记，本轮未取得生成上传目录副本，未声称文件HTTP可读。

### 已知问题、证据范围与副本改动

工头已知的学习时段重叠仍存在。本次独立定义为“同用户按(graded_at,id)排序后的相邻三条练习作答，session呈A-B-A”，检出 **124处**（示例在 `realism.json`）；与工头约123处口径略有差别，属于同一已知问题，不重复立缺陷。生成器11次提交被幂等挡下来自工头/生成日志，QA未重跑生成器。**等待quiz_mock_v2通知后，按相同算法重跑全部只读SQL与一致性/分布检查。**

按工头消息 `7cc3c3a4`，v1原始学员密码未保存，QA在**副本**执行：

```sql
UPDATE quiz_mock_check.app_user
SET password_hash = <受限文件中的 MOCK_USER_PASSWORD_BCRYPT>
WHERE role = 'USER';
```

只改300行的password_hash，供用文件中的MOCK_USER_PASSWORD登录。值在脚本内部读取，未打印、未写仓库；临时受限dump已删除。后续API鉴权另更新副本297个账号的last_active_at（296正常学员+管理员）。结束后重读13表：**原库所有导出的字段逐行未变**；副本除last_active_at外的导出字段也逐行相同，业务记录/统计未被测试改动。密码散列不导出，仅用只读相等计数确认新散列在原库匹配0行、副本USER匹配300行。

后台PID970688（本工作树backend）已按登记cwd/启动时间核验后停止，副本 `quiz_mock_check` 保留，未清理共享MySQL。G01生成预估约192403、实际196919在目标区间；G02–G04启动保护、dry-run和确定性来自后端源码/已有H2日志（MockSimulatorTest 12项通过、MockPlanner/MockDryRun日志通过），属于证据审阅，**未由QA另跑两次全量生成**。正式部署、原生App和头像文件读取不属于本次已通过范围。

## 2026-11-03 · MOCK-QA v2 只读复测

**结论：10组SQL和23项全库一致性检查通过；短时A-B-A交替为0，考试期间练习为0，已知时段重叠问题复测通过。** 不限时间的原始A-B-A还有1处，是跨天恢复顺序练习，详情如下，未把该原始计数改写为0。

目标仅为本机 `quiz_mock_v2`，生成jar **de7e580**；测试工作树已同步main **f2d7308**。本轮没有API请求、后端启动、账号更新或副本重建。证据单独保存在 `e2e/artifacts/mock-v2/`，v1原证据未覆盖；结束快照为 `artifacts/mock-v2-after/`。

| 命令/核对（小脚本均用sozai-lock light） | 结果 | rc | 证据 |
| --- | --- | ---: | --- |
| `mock-snapshot.py quiz_mock_v2 artifacts/mock-v2` | 只读事务导出13业务表，作答197575条 | 0 | `snapshot.log`、`snapshot-summary.json` |
| `mock-checklist-sql.py quiz_mock_v2 … 2026-11-03T03:34:53.286Z` | 清单10组SQL全部执行，异常查询无差异 | 0 | `checklist-sql.log/tsv`、`checklist-executed.sql` |
| `mock-audit.py … 2026-11-03T03:34:53.286Z` | **23通过、0失败** | 0 | `sql-audit.log/json` |
| `mock-realism.py artifacts/mock-v2` | 分布复核；原始ABA=1，短时ABA=0 | 0 | `realism.log/json` |
| 窗口函数直接查原库 | 原始ABA=1；首次切换间隔<10分钟的ABA=0；考试期间练习=0 | 0 | `overlap-query.sql` / `overlap-sql.tsv`、`aba-short-query.sql` / `aba-short.tsv` |
| 原始候选展开及旧快照同口径比较 | 唯一跨天续练已核对；v1短时123→v2短时0 | 0 | `aba-detail-query.sql` / `aba-detail.tsv`、`aba-cache-comparison.json` |
| 结束时再次只读导出并比较 | 13表导出字段逐行未变 | 0 | `source-after.log`、`verification-summary.json` |

### A-B-A 两种口径与唯一候选

按同一用户、`(graded_at,id)` 排序的相邻三条PRACTICE作答：

| 口径 | v1（原快照） | v2 | 说明 |
| --- | ---: | ---: | --- |
| session为A-B-A，不限时间 | 124 | 1 | 保留QA原始算法，可包含跨天恢复 |
| 同上，且首次A→B间隔<10分钟 | 123 | **0** | 对齐后端`MockSimulatorTest.aStudentDoesOneThingAtATime`，SQL为`TIMESTAMPDIFF(MINUTE,prev_t,t)<10` |

v1对比仅读取之前保存的快照，本轮未访问v1数据库。v1多出的1处是用户28隔天恢复；v2唯一候选是用户188：

| 作答记录 | 练习 | 模式 | 作答时间（UTC） |
| --- | --- | --- | --- |
| 98173 | 8433（A） | SEQUENTIAL | 2026-09-27 09:03:09 |
| 112377 | 9656（B） | RANDOM | 2026-10-03 12:55:53 |
| 112428 | 8433（A） | SEQUENTIAL | 2026-10-03 13:01:30 |

首次A→B相隔532364秒；B已于12:55:53完成，再过337秒才恢复A，A最终于13:04:15完成。因此这是复用未完成的顺序练习，没有同一时刻穿插作答。`mock-realism.py`新增第二个计数及三条作答时间，继续保留原始计数，避免把原始1处掩盖或误报为残留重叠。

### v2 数据核对结果

- 总作答 **197575**：练习 **174342**、考试 **23233**；正式作答 **196629**，考试未答 **946**。相对v1总量增加656（约0.33%），仍在15万–25万范围。练习16815场、考试1051场、错题24575条、收藏4866条、纠错248条，与后端v2汇总一致。
- 全部正式练习/已结算考试独立判分一致，错题全状态重放、日练日期及完成条件、引用唯一性、无未来行为/无注册前行为等23项均通过；清单的统计、周榜、看板SQL已重跑。用户300、禁用4、换目标15、头像180，考试人数150/90/60；题486（发布471/草稿10/归档5）、卷12，数据输入JSON的SHA-256与v1相同。
- 考试1050场已结算（超时171），另record1051在生成截止时仍进行中：03:01开考、03:41截止。它没有被QA读接口触发结算；其计划截止晚于模拟停止点，不当作未来行为。时间检查沿用保守界限：种子完成日志 **03:34:53.286Z** 先于MockRunner捕获until，全部实际事件最晚 **03:34:46Z**。
- 画像加权正确率：TOP **80.90%**、STEADY **72.08%**、CRAMMER **71.48%**、WEEKEND **66.97%**、CASUAL **59.76%**、DROPOUT **57.95%**、NEWBIE **60.88%**；各画像人数不变。周末党708个学习用户日中590个在周末（83.33%）；全部练习仍落在画像小时或相邻一小时，日作答P10/P50/P90仍为10/22/40。
- 收藏4866条、其中2628带笔记；纠错149已解决、27已拒绝、72待处理，已处理项均在提交后1–3个北京日；占位符未替换和新增题日期/状态不符均为0。

本轮数据库检查未发现新的数据一致性问题。`quiz_mock_v2`前后13表导出字段逐行未变，未读密码散列或凭证，API请求数为0；没有启动待清理的本地产品服务，也没有改动或删除保留的`quiz_mock_check`。v1接口证据保持原范围，不冒称v2重新跑过API。
