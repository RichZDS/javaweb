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
