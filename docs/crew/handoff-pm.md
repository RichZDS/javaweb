# qzd-pm-5b1e 收尾交接

## 1 在做哪张单

当前无在途施工，按解散通知收尾。最后一单是 `board/tasks/05-mock-pm.md`（MOCK-PM：演示数据），M1、M1b、M2 均已签收。此前完成 `03-product.md`、`03b-product-rework.md` 的验收清单、原创种子题库与返修，以及 PM-RUN-02 产品走查和三项定向复核。

工作树 `/data/worktrees/javaweb-pm`，分支 `feat/product`。本席位只提交，合并、推送、部署由工头负责。

## 2 做到哪一步（可验证事实）

- **收尾核对**：2026-11-03 16:06（北京时间），HEAD 为 `7c3d8cfd832aee98be68c1dc3882b59f47868b9c`；`git status --porcelain` 为空，工作树干净。当前分支未继续同步工头后续提交，接手要先核对 main。
- **MOCK-PM M1**：`4e1234d`，300 学员及文字池；M1b `60a81da` 修复画像模板化，工头确认合入 `main 21cea0a`。最终用户名半数无数字尾数、星期权重300种、时段223种、同组合最多2人；短活跃期涨幅已限速。三个考试150/90/60人，15人换目标、4人禁用、120人无头像；322条文字池内容。
- **MOCK-PM M2**：`7c3d8cf`，工头确认合入 `main e69b726`。90道原创新题覆盖39章（75发布、10草稿、5归档），6份规则卷、每考试2份、含1份下架。16段输出题（Java11/Python2/MySQL3）真实执行匹配答案，另26项计算/算法独立校验通过；65条规则按创建日检查题量和同卷不重叠抽题范围。
- **验收**：`4c2bfed` 记录82e795f预发布最终走查；`2340f3a` 记录3f78323上 PM-M3-12、PM-M3-13、CA-06 三项复核全部通过，工头确认已合入 main 并推送。记录默认目标/全部考试来源、切换目标及科目章节搜题均有真实UI与接口证据。原清单142项登记23通过、0不通过、119没测或仅部分覆盖；不是全量发布验收通过率。
- **种子题库**：396题、39章、6卷，质量返修提交 `060d744` 已获签收（合入 `55bf366`）。34道输出题与返修92项程序验证有源码和输出证据。验收清单首版为 `8c6ff41`。
- **验证退出码**：mock最终字段/日期/题量校验、16段程序最终证据、26项计算校验、文档检查及提交前 `git diff --check` 均为0。早期脚本故障日志保留，不能把初轮失败当最终通过证据。
- **进程**：本人任务服务、浏览器与验证脚本均已结束。历史服务PID 322716、322715、305297、247982、240387现均不存在；按cwd与任务路径检查无遗留任务进程。仍在的是本窗格shell/Codex，由工头关窗，本次未杀它们、未停共享MySQL或其他席位进程。核对证据：`board/handoff-pm-check.json`。两轮线上验收临时私密登录态已清理，mock SQL验证的独立临时schema已删除。

证据入口：

- 完整报审日志：`/data/crew/qzd-5b1e/board/worklog-pm.md`（从顶部向下看，后面有已过时的“进行中”旧记录）。
- mock：`/data/crew/qzd-5b1e/evidence/pm/mock-data/`，重点 `m1b-audit.json`、`m2-audit.json`、`program-results.json`、`math-results.json`、`originality-review.json`；源码/stdout在 `program-proof/`。
- 最终走查：`/data/crew/qzd-5b1e/evidence/pm/final-82e795f/`、`retest-3f78323/`；对应截图在 `/data/crew/qzd-5b1e/shots/pm-final-82e795f/`、`pm-retest-3f78323/`。
- 种子验证：`/data/crew/qzd-5b1e/qb-verify/`，返修重点 `rework/final-evidence.json`。

## 3 动了哪些文件

- `docs/acceptance.md`：142项验收定义、演示脚本、分版本最终验收和证据登记。
- `backend/src/main/resources/seed/`：分类、三考试原创题库、六份种子卷；`scripts/validate-seed.mjs`：无依赖静态校验。
- `backend/src/main/resources/mock/users.json`、`texts.json`、`questions.json`、`papers.json`：四份演示数据。
- 仓库外：本席位worklog、采证截图/JSON、程序验证源码与输出、临时生成校验脚本、本交接及收尾核对JSON。生成脚本未提交；未改前后端业务代码或模拟器。

## 4 没来得及做的

- 本席位被派发的交付均完成并签收，无未提交修改。
- 300人实际作答记录的生成、最终数量与一致性核对、线上导入由后端/测试/工头执行；本席位没有执行或复核其最终生产结果。约204740次只是PM规划估算，不能当作最终入库行数；接手查他们的报告。
- 真实Android/iOS设备、TestFlight/App Store等不在已完成的H5验收证据范围内，不能用浏览器模拟视口替代。
- PM-M3-08/09/10/11是早期本地走查的导入旧结果残留、挑题跨页选择、部分成功重传说明、错题原字母提示意见；本席位未获最终线上复验派单。状态和历史证据已留在acceptance，不声称当前版本仍存在，也不声称已经修好。正式清库后旧pm_账号/记录不应再被当成可复用夹具。

## 5 坑（下家会踩的）

- **锁顺序**：重任务 `heavy sozai-lock heavy -- ...`，不能反过来；单个java/python/node/SQL或单浏览器脚本只用 `sozai-lock light -- ...`。持有sozai-lock时再等heavy曾造成死锁。
- **停进程**：只按自己记录的PID核对cwd和启动时间后停止。禁止模糊pkill/pgrep匹配停服务，曾发生误停本席位后端。共享13306 MySQL不属于本席位，勿停或重置他人库。
- **工具链**：先source `/data/crew/qzd-5b1e/bin/env.sh`；mysql客户端不在该PATH，实际是 `/data/tools/mysql-8.0/bin/mysql`。SQL证明只用独立自建schema。
- **生成脚本不是最终数据的单一步骤**：初版build-users后还有rework-users，初版build-questions后还有revise-overlaps。直接重跑初稿会覆盖已签收的真实感/去重修订；以提交JSON及最终SHA/验证证据为准。
- **数据口径**：mock使用精确名称路径和日期；weekdayWeights是周一至周日，persona只是统计标签。正确率涨幅按注册至最后活跃日计算；流失者不能用今天拉长时长。规则范围可为一级分类，但同卷多规则不得把同一题重复计入容量。
- **线上采证**：曾需绕开本机代理；严格TLS仍可用。跨境导航慢、图片响应头已到但图尚未加载，不能直接判缺陷；等实际内容/图片加载完成后截图。弹窗动画结束再截图。已执行的最终指令是停止预发布操作；交接不构成再次访问线上或写后台的授权。
- **验收口径**：章节正确率用全部正式作答；薄弱点按近期开过的不同题的最近一次结果；每日10题全交才打卡。原选项映射要分显示字母与原字母。历史失败与新版复核要按commit区分，不把局部走查当全量通过。
- **通信**：只向工头报审。herdr-msg返回pending/rc4时保留delivery_id，不重发；blocked状态不能投递。凭证不在交接、代码、日志中流转。
