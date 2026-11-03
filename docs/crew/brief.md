# 班子 qzd-5b1e · 在线刷题 App 开发

## 总任务
实现仓库 `RichZDS/javaweb`（主检出 `/data/workspace/javaweb`，分支 main）：Spring Boot 后端、uni-app 手机 App（Android + iOS）、Vue 3 + Element Plus 管理后台，以及原创示例题库。下面四份文件是唯一依据：

- `docs/spec.md`：功能和三批优先级。三批都要做，先保证第一批跑通。
- `docs/api.md`：接口约定。前后端都按它实现。发现它有问题、有缺口，先报工头，由工头改，不要各改各的。
- `backend/src/main/resources/db/migration/V1__init.sql`：建表脚本。要改表就新建 `V2__xxx.sql`，并在报审时说明。
- `docs/seed-format.md`：示例题库文件格式。

## 分工

| 席位 | label | CLI | 负责目录 | 分支 / 工作树 |
| --- | --- | --- | --- | --- |
| 工头 | qzd-lead-5b1e | Claude | 派单、验收、合并 main、推送、部署 | main / `/data/workspace/javaweb` |
| 后端 | qzd-be-5b1e | Claude | `backend/`（`seed/` 除外） | `feat/backend` / `/data/worktrees/javaweb-be` |
| 前端 | qzd-fe-5b1e | Claude | `app/`、`admin/`、`docs/release.md` | `feat/frontend` / `/data/worktrees/javaweb-fe` |
| 产品经理 | qzd-pm-5b1e | GPT | `backend/src/main/resources/seed/`、`scripts/validate-seed.mjs`、`docs/acceptance.md` | `feat/product` / `/data/worktrees/javaweb-pm` |
| 测试 | qzd-qa-5b1e | GPT | `e2e/`、`docs/test-report.md` | `feat/qa` / `/data/worktrees/javaweb-qa` |

只改自己目录里的文件。确实要动别人的目录或 `docs/` 下别人的文件，先报工头。

## 节奏
- 每个人按自己单子里的阶段（M1、M2…）交付。每到一个阶段：commit，然后报审。
- 工头验收后合并到 main 并推送，CI 会跑。合并后工头会通知大家；需要最新代码的人在自己分支上 `git merge main`。
- 测试和产品经理按 main 上的代码验收，发现问题报给工头，由工头转派给写代码的人。写代码的人之间、写代码的人和测试之间不直接派活。

## 验收标准
1. 单子「交付」节列的东西都在，提交信息用 Conventional Commits（`feat(backend): …`）。
2. 「测试」节列的命令都跑过，报审时贴真实退出码和关键输出；没跑的写明没跑及原因。
3. 不说没验证过的话：「应该可以」不算完成。
4. 代码清楚，必要处有注释，没有大段死代码和调试输出。

## 红线
- **不 push、不改远程、不开 PR**。只在自己工作树的分支上 commit，推送只由工头做。
- 不用 `git config` 改任何配置。提交身份仓库里已经配好。
- 不登录、不连接任何外部服务器，包括部署服务器。
- 不提交任何密钥、密码、证书。示例配置里的密码写成占位符，或从环境变量读。
- 不复制商业题库或真题原文。
- 重任务一律走闸门：`heavy <命令>`，见 `resource-rules.md`。
- 只清理自己产生的文件，不动本机其他项目和其他人的工作树。

## 红线补充（2026-11-02，预发布上线后）
- “不登录、不连接任何外部服务器”指 SSH、服务器后台、数据库、容器等管理权限，只有工头能碰。
- 预发布网站 https://43.142.166.108/quiz/ 可以像普通用户一样使用：自己注册测试账号，用户名用本席位前缀（fe_、qa_、pm_、be_）。管理员账号只给测试用于后台用例。

## 演示数据（2026-11-03）
- 约定：`docs/mock-data.md`。产品经理新增负责 `backend/src/main/resources/mock/`（只放数据，不提交脚本）；后端写模拟器；测试只读核对。
- 本机演示库：MySQL 13306 上的 `quiz_mock`，后端负责建和重建；测试只读。线上灌数据只由工头做。
