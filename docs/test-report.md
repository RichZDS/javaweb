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
