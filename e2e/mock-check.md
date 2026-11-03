# MOCK-QA：300 人演示数据核对清单

状态：**待执行**。本次只编写清单，未连接 MySQL、未导出数据库、未启动模拟器或 API。

依据：[mock-data.md](../docs/mock-data.md) 第 2、6 节（`dfa4fdd`，已纳入 `5b88a33` 的 `weekdayWeights`）、[api.md](../docs/api.md) 第 0、4–7、9 节、V1–V3 表结构。字段和查询口径已对照当前 `StatsService`、`WrongBookService`、`ExamSettler`、相关 Mapper。模拟器交付后再核对新增迁移和运行汇总。

## 1. 执行边界与证据

- [ ] **M00** 收到工头“库已跑好，可以执行”的通知后才开始。记录 main/模拟器 commit、四份 JSON 的 SHA-256、randomSeed、模拟起始日、运行截止时刻 `cutoff_utc`、模拟器汇总和退出码。300 个用户名以 `users.json` 为准；不凭用户名风格猜哪些是 mock 用户。
- [ ] **M01** 原库固定 `127.0.0.1:13306/quiz_mock`，只执行 SELECT、会话变量和只读事务；不运行模拟器、不重置库、不停止共享 MySQL。保存逐表行数、最早/最晚行为时间和异常行主键；不导出密码散列到报告。
- [ ] **M02** 按工头消息 `995ad887`，QA 自己用 `mysqldump` 将原库复制到 **`quiz_mock_check`**；接口只访问连这个副本的本地后端。副本允许业务写入，原库不碰。全部测试后停止自己启动的后端，**副本保留，等工头通知清理**。
- [ ] **M03** 原库 SQL 用同一一致性快照；API 比较记录明确的北京日期 `api_today`，跨午夜/周一边界时重新取日期基准，不能把生成日永久当作接口“今天”。无未来行为用生成时的 `cutoff_utc`，不能用晚几天的验收时间掩盖问题。
- [ ] **M04** 硬性一致性要求列为“通过/失败/未执行”；“大致、约、像真人”列为“符合/需人工复核”，展示实际人数、分布、样本量和样例，不自行把近似比例改成严格等式。每项保留 SQL/请求参数、查询时刻、返回摘要和差异主键。

原库只读会话的模板（参数由届时运行清单填写；不是现在执行的脚本）：

```sql
-- 连接时明确 --host=127.0.0.1 --port=13306 --database=quiz_mock。
SELECT DATABASE(), @@port, @@session.time_zone;
SET SESSION time_zone = '+00:00';
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY;
-- @cutoff_utc：模拟器记录的截止时刻；@api_today：API 校验时的北京时间日期。
-- @uid：已按 username 对齐的用户 id；@root：明确的一级分类 id（NULL 表示不限）。
-- @profiles_json：完整 users.json，通过客户端参数传入会话变量，不建表/导入原库。
-- 查询前确认参数非空、JSON_VALID(@profiles_json)=1；不要让 NULL 参数产生假通过。
-- 以下各查询按需执行，结束用 ROLLBACK。
```

副本准备顺序：先完成原库时间/画像核对，再导出；生成程序须已退出，没有其他写入。使用 `sozai-lock light --` 排队小脚本。示意命令的文件路径用自己的 `e2e/artifacts/mock/<run>/`，目录权限 700、dump 权限 600：

```bash
# 仅在收到执行通知后运行。默认不含 CREATE DATABASE / USE 原库语句。
umask 077
mysqldump --host=127.0.0.1 --port=13306 --user=root \
  --single-transaction --skip-lock-tables --skip-add-locks --no-tablespaces \
  --set-gtid-purged=OFF --hex-blob quiz_mock > "$QA_MOCK_DUMP"
# 不加 --databases；导入前检查 dump 没有 USE/CREATE DATABASE 指回 quiz_mock。
mysql --host=127.0.0.1 --port=13306 --user=root \
  -e 'CREATE DATABASE quiz_mock_check CHARACTER SET utf8mb4'
mysql --host=127.0.0.1 --port=13306 --user=root quiz_mock_check < "$QA_MOCK_DUMP"
```

若副本已存在，先记录状态；重灌只重建 `quiz_mock_check`，绝不重建 `quiz_mock` 或 `quiz_test`。导入后、启动应用前核对所有业务表行数及关键聚合与原快照一致。dump 含认证散列，不提交、不发送、不当作测试附件；完成后仅清理自己的临时 dump，副本库仍保留。

本地后端使用执行时工头确认的 **main jar**，记录构建 commit；需要构建时 `source /data/crew/qzd-5b1e/bin/env.sh` 后走 `heavy sozai-lock heavy -- mvn -B -ntp -DskipTests package`，不把打包称为业务测试。配置明确指向 `jdbc:mysql://127.0.0.1:13306/quiz_mock_check`（UTC、MySQL driver），绑定 `127.0.0.1`，用 QA 随机分配的空闲端口；**`app.mock.enabled=false`、`APP_SEED_ENABLED=false`**。默认 dev 会覆盖数据源，启动前确认最终生效的 URL/driver/seed，不使用现有 H2 栈脚本代替副本。为稳定对账设置 `app.exam.auto-settle=false`，不新建用户或作答。上传目录使用自己的本地目录；若要核对头像文件，由工头提供模拟器上传目录的副本。密码/JWT 只从进程环境读取，不打印值。

显式覆盖项（端口和上传目录届时填写；不依赖dev的数据源默认值）：

```text
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:13306/quiz_mock_check?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=
SPRING_DATASOURCE_DRIVER_CLASS_NAME=com.mysql.cj.jdbc.Driver
APP_MOCK_ENABLED=false
APP_SEED_ENABLED=false
APP_EXAM_AUTO_SETTLE=false
SERVER_ADDRESS=127.0.0.1
SERVER_PORT=<本次QA分配的空闲端口>
APP_UPLOAD_DIR=<QA自己的本地上传目录>
```

**GET 并非天然只读**：`JwtAuthFilter.touchLastActive` 会写活跃时间，考试列表/详情可能结算过期记录。因此所有 API（包括登录与 GET）都只能打副本；原库画像/流失/时间核对不从被登录过的副本取证。API 前后记录副本作答/考试行数；若读取触发结算，明确差异，不能拿结算后的 API 对账结算前原库却宣称一致。进程用现有 `lib/process-owner.mjs` 登记 PID、cwd、启动时间，停止前逐一核验，不按名字/端口杀进程。

## 2. SQL：人口、时间与引用完整性

| 编号 | 核对内容 | 通过条件/证据 |
| --- | --- | --- |
| S01 | 300 学员及画像一一对应 | JSON username 唯一；与 `app_user.role='USER'` 双向差集为空；300 学员 + 原有 1 管理员，无 QA/性能测试污染。用户名3–20位、昵称1–30字、禁用前缀均符合约定；不读取 password_hash。 |
| S02 | 注册、目标、设置 | 北京注册日等于 registeredOn，位于 startDate 至 2026-11-02；目标是一级分类；最终目标为已发生 switchTarget.to，否则原 target；错题阈值等于画像且在1–10。禁用3–5人、换目标10–20人；头像为空约40%。JSON另查activeDaysPerWeek在0.3–7、questionsPerDay有序非负整数、hours为0–23整数、weekdayWeights若提供须7个非负值；全零权重需模拟器说明处理，不能默认为周末行为。 |
| S03 | 无未来行为、无早于注册 | 下列 events 查询各项 future_count、before_registration 均为0；时间未填不能以0异常代替通过。原库 `last_active_at` 也不能被 QA 登录污染。 |
| S04 | 行为时间先后、流失 | 练习完成/更新不早于开始，收藏更新不早于创建，纠错处理不早于提交，考试保存/交卷不早于开考；学员主动行为不晚于 lastActiveOn 北京日末。管理员后来处理该学员纠错、延迟自动结算等不误算学员又活跃。禁用动作必须在最后活跃日之后，结合模拟日志核对（表无独立 disabled_at）。 |
| S05 | 题目/试卷可用时间 | 新题不能在 createdOn 前被抽到，新卷不能在创建前开考；草稿不可供正式作答。当前归档/下架不等于历史作答非法，按当时发布/上下架事件或快照判定；表中没有完整历史时列“缺事件证据”，不倒推历史状态。 |
| S06 | 引用与重复 | answer_record 的用户、题目、来源练习/考试均存在且来源属于本人；来源题属于 question_ids/考试快照，背题无 answer_record；每个(source,source_id,question_id)仅1行。wrong_question/favorite/feedback、考试答案均无孤儿引用，分类快照属于对应根。 |
| S07 | 总量/逐表汇总 | `answer_record` 全部行数15万–25万，按 PRACTICE/EXAM 分列，同时列 answered=true 数量，不能把考试未答行算正式作答。所有业务表实际行数/日期范围与模拟器汇总一致；不要用 information_schema.TABLE_ROWS 的估值代替 COUNT(*)。 |

S03 时间扫描（UTC DATETIME；仅有实际发生时间的字段进入 events）：

```sql
WITH events(kind, id, uid, at_utc) AS (
 SELECT 'user.created', id, id, created_at FROM app_user UNION ALL
 SELECT 'user.updated', id, id, updated_at FROM app_user UNION ALL
 SELECT 'user.active', id, id, last_active_at FROM app_user UNION ALL
 SELECT 'category.created', id, NULL, created_at FROM category UNION ALL
 SELECT 'question.created', id, NULL, created_at FROM question UNION ALL
 SELECT 'question.updated', id, NULL, updated_at FROM question UNION ALL
 SELECT 'paper.created', id, NULL, created_at FROM exam_paper UNION ALL
 SELECT 'paper.updated', id, NULL, updated_at FROM exam_paper UNION ALL
 SELECT 'practice.started', id, user_id, started_at FROM practice_session UNION ALL
 SELECT 'practice.completed', id, user_id, completed_at FROM practice_session UNION ALL
 SELECT 'practice.updated', id, user_id, updated_at FROM practice_session UNION ALL
 SELECT 'answer.graded', id, user_id, graded_at FROM answer_record UNION ALL
 SELECT 'wrong.created', id, user_id, created_at FROM wrong_question UNION ALL
 SELECT 'wrong.last', id, user_id, last_wrong_at FROM wrong_question UNION ALL
 SELECT 'wrong.removed', id, user_id, removed_at FROM wrong_question UNION ALL
 SELECT 'favorite.created', id, user_id, created_at FROM favorite UNION ALL
 SELECT 'favorite.updated', id, user_id, updated_at FROM favorite UNION ALL
 SELECT 'exam.started', id, user_id, started_at FROM exam_record UNION ALL
 SELECT 'exam.submitted', id, user_id, submitted_at FROM exam_record UNION ALL
 SELECT 'exam.received', a.id, r.user_id, a.received_at
   FROM exam_answer a JOIN exam_record r ON r.id=a.record_id UNION ALL
 SELECT 'feedback.created', id, user_id, created_at FROM question_feedback UNION ALL
 SELECT 'feedback.handled', id, user_id, handled_at FROM question_feedback
)
SELECT e.kind, COUNT(*) event_count, MIN(e.at_utc) first_utc, MAX(e.at_utc) last_utc,
       SUM(e.at_utc > @cutoff_utc) future_count,
       SUM(CASE WHEN u.role='USER' AND e.at_utc<u.created_at THEN 1 ELSE 0 END) before_registration
FROM events e LEFT JOIN app_user u ON u.id=e.uid
WHERE e.at_utc IS NOT NULL GROUP BY e.kind ORDER BY e.kind;
```

`exam_record.deadline_at` 是计划截止时刻，另核对等于开考时间加时长，不混入“未来行为”。列出仍进行中及超期未结算的场次，交给模拟器汇总解释。手动交卷必须在截止前；TIMEOUT 的 submitted_at 等于 deadline_at。若生成截止时尚未到计划截止，不能将未来 deadline 误报为未来作答。管理员历史动作是否早于其可用时间要查模拟日志，不能仅因学员表一致就假称已核对管理员动作。

S06/S07 基础查询（其余关联按表结构补相同的 LEFT JOIN 缺失检查）：

```sql
SELECT role, status, COUNT(*) n FROM app_user GROUP BY role,status;
SELECT source, answered, COUNT(*) n, MIN(graded_at), MAX(graded_at)
FROM answer_record GROUP BY source,answered;
SELECT source,source_id,question_id,COUNT(*) n FROM answer_record
GROUP BY source,source_id,question_id HAVING COUNT(*)<>1;
SELECT a.id,a.source,a.source_id FROM answer_record a
LEFT JOIN app_user u ON u.id=a.user_id LEFT JOIN question q ON q.id=a.question_id
LEFT JOIN practice_session p ON a.source='PRACTICE' AND p.id=a.source_id
LEFT JOIN exam_record e ON a.source='EXAM' AND e.id=a.source_id
WHERE u.id IS NULL OR q.id IS NULL OR a.source NOT IN ('PRACTICE','EXAM')
   OR (a.source='PRACTICE' AND (p.id IS NULL OR p.user_id<>a.user_id OR p.recite_mode=TRUE))
   OR (a.source='EXAM' AND (e.id IS NULL OR e.user_id<>a.user_id OR e.status<>'FINISHED'));
```

## 3. SQL + 接口：统计、错题、考试、打卡、排行榜、看板

### 3.1 统计基准（S08 / A02）

每个查询固定 `@uid`、`@root`。`@root` 为显式 categoryId；省略接口参数时取当前 target_category_id；两者都没有才不限。换目标用户另外核对旧考试，不能用当前目标过滤掉历史再声称所有历史一致。比率按API保留4位小数，零分母为NULL，不是0；正确率分子是全对条数。

```sql
SELECT COUNT(*) answer_count, COALESCE(SUM(is_correct),0) correct_count,
       ROUND(SUM(is_correct)/NULLIF(COUNT(*),0),4) correct_rate,
       COUNT(DISTINCT DATE(TIMESTAMPADD(HOUR,8,graded_at))) study_days,
       COALESCE(SUM(DATE(TIMESTAMPADD(HOUR,8,graded_at))=@api_today),0) today_answer_count
FROM answer_record WHERE user_id=@uid AND answered=TRUE
 AND (@root IS NULL OR root_category_id=@root);

SELECT DATE(TIMESTAMPADD(HOUR,8,graded_at)) day_bj, COUNT(*) answers,
       SUM(is_correct) correct, ROUND(SUM(is_correct)/COUNT(*),4) rate
FROM answer_record WHERE user_id=@uid AND answered=TRUE
 AND (@root IS NULL OR root_category_id=@root)
 AND graded_at>=TIMESTAMPADD(HOUR,-8,CAST(DATE_SUB(@api_today,INTERVAL 29 DAY) AS DATETIME))
GROUP BY day_bj ORDER BY day_bj;

SELECT category_id_snapshot, COUNT(DISTINCT question_id) done_count,
       COUNT(*) answer_count, ROUND(SUM(is_correct)/COUNT(*),4) correct_rate
FROM answer_record WHERE user_id=@uid AND answered=TRUE
 AND (@root IS NULL OR root_category_id=@root) GROUP BY category_id_snapshot;

SELECT COUNT(*) exam_count FROM exam_record r JOIN exam_paper p ON p.id=r.paper_id
WHERE r.user_id=@uid AND r.status='FINISHED' AND (@root IS NULL OR p.category_id=@root);
```

- [ ] daily 在客户端补齐含今天的30天，缺日为0/0/NULL，顺序从早到晚；同时核对整段和 overview 的对应时间窗口。
- [ ] 统计 categories 的 doneCount 按不同题、answerCount 按条数，历史归属用 `category_id_snapshot`；questionCount 用当前 `question.status='PUBLISHED'` 的章节题数，包含未做章节的0行。
- [ ] **分类树是另一口径**：`GET /api/categories?rootId=` 的已做/正确率只计仍已发布题、按题目当前章节；不要直接套统计 categories 的历史快照SQL。
- [ ] weak：窗口内先按 `(graded_at DESC,id DESC)` 对每个 question_id 取最后一条 answered=true；再按该条 category_id_snapshot 分组，样本至少5道；按四位正确率升序、样本降序、分类树顺序取前N。与 `/api/stats/weak` 对账，不用所有重复作答平均数代替。

### 3.2 错题本（S09 / A03）

```sql
WITH wrongs AS (
 SELECT user_id,question_id,COUNT(*) n FROM answer_record
 WHERE answered=TRUE AND is_correct=FALSE GROUP BY user_id,question_id
)
SELECT a.user_id,a.question_id,a.n,w.wrong_count
FROM wrongs a LEFT JOIN wrong_question w USING(user_id,question_id)
WHERE w.id IS NULL OR w.wrong_count<>a.n
UNION ALL
SELECT w.user_id,w.question_id,0,w.wrong_count
FROM wrong_question w LEFT JOIN wrongs a USING(user_id,question_id)
WHERE a.user_id IS NULL;
```

- [ ] 上述差异0行，包括已移出的历史错题，不仅检查当前列表；考试未答 answered=false 不能增加 wrong_count。
- [ ] 读取同一(user_id,question_id)的全部正式作答，以业务提交顺序重放（单用户加锁；先以 answer_record.id 排序，再与模拟器事件顺序核实）。任何来源答错：wrong_count+1、streak=0、removed=false；只有 PRACTICE 且模式 WRONG/RETRY 答对，在错题仍未移出时 streak+1，达用户阈值移出；普通练习/考试答对不增加 streak。核对 correct_streak、removed、removed_at。
- [ ] last_wrong_answer 等于最后一次有效答错的**原标签**数组，比较解析后的JSON，不比较空格；created_at/last_wrong_at/removed_at 对齐对应操作时间。TIMEOUT 的 answer_record.graded_at 记截止时刻，wrong_book 更新可能稍后发生，不能机械要求两个时间相等；要用模拟器结算事件核对，证据缺失时单列。
- [ ] 若存在手动移出/中途改阈值，用事件记录参与重放；没有历史不能凭最终阈值猜整个过程。
- [ ] API列表只取 removed=false，按题目当前章节落在请求分类子树（默认当前目标）；RECENT 按last_wrong_at/id倒序，MOST先wrong_count倒序。核对所有分页、total、分组和每条次数/连对/原标签。当前实现不额外过滤PUBLISHED，不能偷偷增加该过滤条件。

### 3.3 考试分数与每题记录（S10 / A04）

```sql
WITH sums AS (
 SELECT r.id,r.score,r.total_score,r.pass_score,r.passed,
        COUNT(*) snapshot_count, SUM(j.max_score) max_sum,
        SUM(COALESCE(ea.score,0)) saved_score_sum,
        SUM(CASE WHEN ar.answered=TRUE AND ar.is_correct=TRUE THEN j.max_score ELSE 0 END) record_score_sum,
        COUNT(ar.id) graded_rows
 FROM exam_record r
 JOIN JSON_TABLE(r.question_snapshot,'$.questions[*]' COLUMNS (
   question_id BIGINT PATH '$.questionId', max_score INT PATH '$.score')) j ON TRUE
 LEFT JOIN exam_answer ea ON ea.record_id=r.id AND ea.question_id=j.question_id
 LEFT JOIN answer_record ar ON ar.source='EXAM' AND ar.source_id=r.id AND ar.question_id=j.question_id
 WHERE r.status='FINISHED' GROUP BY r.id,r.score,r.total_score,r.pass_score,r.passed
)
SELECT * FROM sums WHERE score<>saved_score_sum OR score<>record_score_sum
 OR total_score<>max_sum OR snapshot_count<>graded_rows
 OR passed<>(score>=pass_score) OR score IS NULL OR passed IS NULL;
```

- [ ] 差异0行；另查空/无效快照、重复题、快照外 exam_answer、负分及超过满分，防止内连接遗漏坏记录。未保存的题允许没有 exam_answer，折合0分，但已结算每道题都应有 answer_record；未答为 answered=false/is_correct=false。
- [ ] 保存答案 received_at 不晚于deadline；TIMEOUT submitted_at=deadline，MANUAL started_at≤submitted_at<deadline；每道EXAM作答 graded_at=submitted_at。核对试卷快照满分/及格线，不拿后来编辑过的试卷定义重算旧卷。
- [ ] 分层抽样独立按快照标准答案核对四题型（多选少选0分、填空归一化）；打乱卷把展示标签按optionMapping还原后比库内原标签。接口 items[].score 求和=总score，按题型/章节分数也相等，正确/错误/未答之和=快照题数。

### 3.4 每日一练与连续打卡（S11 / A05）

```sql
SELECT s.id,s.user_id,s.root_category_id,s.daily_date,s.completed_at,
       JSON_LENGTH(s.question_ids) expected_count, COUNT(a.id) submitted_count
FROM practice_session s LEFT JOIN answer_record a
 ON a.source='PRACTICE' AND a.source_id=s.id AND a.answered=TRUE
WHERE s.mode='DAILY'
GROUP BY s.id,s.user_id,s.root_category_id,s.daily_date,s.completed_at,s.question_ids
HAVING (s.completed_at IS NOT NULL AND submitted_count<>expected_count)
 OR (s.completed_at IS NULL AND submitted_count=expected_count AND expected_count>0);

WITH done AS (
 SELECT DISTINCT daily_date d FROM practice_session
 WHERE user_id=@uid AND mode='DAILY' AND completed_at IS NOT NULL
   AND (@root IS NULL OR root_category_id=@root)
), anchor AS (
 SELECT CASE WHEN EXISTS(SELECT 1 FROM done WHERE d=@api_today)
             THEN @api_today ELSE DATE_SUB(@api_today,INTERVAL 1 DAY) END d
), ordered AS (
 SELECT x.d,ROW_NUMBER() OVER(ORDER BY x.d DESC) n
 FROM done x CROSS JOIN anchor a WHERE x.d<=a.d
)
SELECT COUNT(*) expected_streak FROM ordered x CROSS JOIN anchor a
WHERE DATEDIFF(a.d,x.d)=x.n-1;
```

- [ ] 首条差异0行；每日一练(user_id,root_category_id,daily_date)唯一，daily_date是开练习的北京日期且不在未来；少于10题的合法题池按实际题数，不硬写10。
- [ ] streakDays用第二条SQL；今天未完成可从昨天接续，今天和昨天都未完成为0；普通练习有作答只增加 studyDays，不能冒充打卡。抽取已完成今天、仅完成昨天、中断、换考试、新人/流失等现有样本，不为制造样本写数据。

### 3.5 本周排行榜（S12 / A06）

```sql
SET @week_start_utc=TIMESTAMPADD(HOUR,-8,
 CAST(DATE_SUB(@api_today,INTERVAL WEEKDAY(@api_today) DAY) AS DATETIME));
WITH counts AS (
 SELECT a.user_id,COUNT(*) n FROM answer_record a JOIN app_user u ON u.id=a.user_id
 WHERE a.answered=TRUE AND a.graded_at>=@week_start_utc AND u.status='ACTIVE'
   AND (@root IS NULL OR a.root_category_id=@root) GROUP BY a.user_id
), ranked AS (
 SELECT user_id,n,RANK() OVER(ORDER BY n DESC) rank_no FROM counts
)
SELECT r.*,u.nickname,u.avatar_url FROM ranked r JOIN app_user u ON u.id=r.user_id
ORDER BY r.n DESC,r.user_id ASC LIMIT 20;
```

- [ ] 北京周一00:00起、按正式作答条数而非不同题数；只含当前ACTIVE用户，禁用用户历史仍保留在个人统计但不进周榜。
- [ ] 并列名次1、1、3；排序计数相同按user_id，不把id放进RANK的窗口排序破坏并列。取前20行，不是前20名全纳入。
- [ ] `me` 用未截断的ranked查询本人；不在前20仍可有名次，本周无作答为 `{rank:null,answerCount:0}`。三个考试分别对账；不依据用户当前目标排除其在旧考试做过的题。

### 3.6 后台看板（S13 / A07）

```sql
SELECT (SELECT COUNT(*) FROM app_user WHERE role='USER') user_count,
       (SELECT COUNT(*) FROM question WHERE status='PUBLISHED') question_count,
       (SELECT COUNT(*) FROM exam_paper) paper_count,
       (SELECT COUNT(*) FROM answer_record WHERE answered=TRUE
         AND DATE(TIMESTAMPADD(HOUR,8,graded_at))=@api_today) today_answer_count,
       (SELECT COUNT(*) FROM question_feedback WHERE status='PENDING') pending_feedback_count;
SELECT DATE(TIMESTAMPADD(HOUR,8,graded_at)) day_bj,COUNT(*) answer_count,
       COUNT(DISTINCT user_id) active_users FROM answer_record
WHERE answered=TRUE
 AND graded_at>=TIMESTAMPADD(HOUR,-8,CAST(DATE_SUB(@api_today,INTERVAL 13 DAY) AS DATETIME))
GROUP BY day_bj ORDER BY day_bj;
SELECT p.id,COUNT(r.id) attempts,ROUND(AVG(r.score),2) avg_score,
       ROUND(SUM(r.passed)/NULLIF(COUNT(r.id),0),4) pass_rate
FROM exam_paper p LEFT JOIN exam_record r ON r.paper_id=p.id AND r.status='FINISHED'
GROUP BY p.id ORDER BY p.id;
```

- [ ] `userCount=300`（含禁用学员，不含管理员），paperCount包含下架卷；14日曲线补零且顺序正确，activeUsers按有正式作答的不同用户数；无考试的卷attempts=0、avgScore/passRate=NULL。
- [ ] 三个考试都有真实作答与考试数据，看板曲线、试卷统计、待处理纠错有内容；不能只看返回200/列表非空。若模拟运行日今天尚无学习（如凌晨），todayAnswerCount为0要记录当前时刻和计划，不能为“有数”制造未来记录。

## 4. SQL + JSON：画像比例、正确率与学习时段

persona、hours、weekdayWeights、目标切换计划不在数据库中，按username关联原JSON，**不向quiz_mock加辅助表/列**。例如把JSON作为会话变量，以下CTE可以用于画像聚合：

```sql
WITH profiles AS (
 SELECT * FROM JSON_TABLE(@profiles_json,'$.users[*]' COLUMNS (
   username VARCHAR(20) PATH '$.username', persona VARCHAR(12) PATH '$.persona',
   target_name VARCHAR(50) PATH '$.target', registered_on DATE PATH '$.registeredOn',
   last_active_on DATE PATH '$.lastActiveOn', start_acc DOUBLE PATH '$.accuracy.start',
   end_acc DOUBLE PATH '$.accuracy.end')) p
), rates AS (
 SELECT user_id,COUNT(*) n,SUM(is_correct) good FROM answer_record
 WHERE answered=TRUE GROUP BY user_id
)
SELECT p.persona,COUNT(*) users,COUNT(r.user_id) users_with_answers,
       SUM(COALESCE(r.n,0)) answers,AVG(r.good/NULLIF(r.n,0)) mean_user_accuracy,
       SUM(r.good)/NULLIF(SUM(r.n),0) weighted_accuracy
FROM profiles p JOIN app_user u ON u.username=p.username
LEFT JOIN rates r ON r.user_id=u.id GROUP BY p.persona ORDER BY p.persona;
```

| 编号 | 内容 | 核对方法/判定 |
| --- | --- | --- |
| R01 | 画像比例、考试比例 | 300人；画像参考人数TOP24、STEADY90、CRAMMER36、WEEKEND45、CASUAL45、DROPOUT45、NEWBIE15，初始目标参考408 150/Java90/数据库60。分别列JSON初始目标与数据库当前目标，因10–20人切换不要求两表比例完全相同。比例按“大致”审阅，人数硬要求另列。 |
| R02 | 人名和画像内部差异 | 查重用户名、禁用前缀；各画像抽至少5人，查看昵称/用户名/活跃天数/题量/时段/正确率，不能同一模板连续编号或所有画像内部参数相同。不要在样例中带密码或令牌。 |
| R03 | 注册节奏、新人、流失、禁用 | 原库按北京日/月汇总 created_at；7月起量、9月峰值、国庆变化、11月408冲刺及近两周新人。结合截止日已过去天数计算日均，不能拿11月两三天总数和整月比较。DROPOUT有过去lastActiveOn且之后无主动行为；3–5个disabled在其后禁用（需事件日志）。 |
| R04 | 正确率分布 | 按考试×画像、用户、题型、难度列作答数、全对数和比率；每用户率P10/P50/P90、0–20%/20–40%/40–60%/60–80%/80–100%分桶、零作答人数。均值与按作答量加权率分别列，不能只报一个总平均掩盖所有人相同或极端值。 |
| R05 | 学习进步和科目差异 | JSON中408起点通常0.40–0.60、Java/数据库略高、TOP终点可约0.85、流失进步较小。对有足够样本（首末窗口各≥50条）的用户比较活跃期首14日/末14日，按题型/难度/科目分层；不足样本单列，不判失败。实际正确率受随机、难度、重做加成影响，不要求每人精确等于 accuracy.end；若大群体相反或完全同率，回查数据及模拟器。 |
| R06 | 小时与星期分布 | 下面SQL给出北京小时和周一=0的星期分布；对齐hours及weekdayWeights（缺省全1），检查晚间占比最高、早晨/午休/深夜较少，WEEKEND周六日明显集中；列各画像的实际分布与学习日机会数，不只看总量。检查时间不是都在00:00/同一整点。 |
| R07 | 活跃频率和每日题量 | 每人每周有作答日期数、每日正式作答条数的分位数；对照 activeDaysPerWeek、questionsPerDay 和存续日数、lastActiveOn、周末权重。边界残周与考试未答不误报；抽题不足/未完成的偏差需要模拟器汇总解释。 |
| R08 | 行为多样性与展示内容 | 有DAILY/RANDOM/SEQUENTIAL/WRONG、背题但无正式作答、打乱练习、考试、收藏/部分带笔记和各类纠错；频次与habits量级相称。查看重做正确率变化、顺序章节推进、不同画像考试次数。禁用/头像/换目标数量按S02；头像有文件证据则核验可读，没有文件只能确认DB路径。 |

R06小时/星期SQL应分开“行为”与“结算”：考试 graded_at 是交卷时统一记分，不能把几十道同时记分解释为同秒做题。分别看练习提交、练习开始、考试开始和答案接收：

```sql
WITH actions AS (
 SELECT user_id,'practice_submit' kind,graded_at ts FROM answer_record
 WHERE source='PRACTICE' AND answered=TRUE UNION ALL
 SELECT user_id,'practice_start',started_at FROM practice_session UNION ALL
 SELECT user_id,'exam_start',started_at FROM exam_record UNION ALL
 SELECT r.user_id,'exam_save',a.received_at FROM exam_answer a JOIN exam_record r ON r.id=a.record_id
)
SELECT kind,HOUR(TIMESTAMPADD(HOUR,8,ts)) hour_bj,
       WEEKDAY(TIMESTAMPADD(HOUR,8,ts)) weekday_monday0,
       COUNT(*) events,COUNT(DISTINCT user_id) users
FROM actions GROUP BY kind,hour_bj,weekday_monday0 ORDER BY kind,hour_bj,weekday_monday0;
```

hours的“附近”和画像比例没有约定严格偏差阈值；出实测分布后按目标审阅，不擅自给每个人设死线。更晚时段没发生的当日计划不能纳入实际数。切换后的作答按记录root汇总，并核对开练习/开考当时目标；已有旧练习跨切换继续作答需单独解释，不能一律视为错根。

## 5. 接口核对矩阵（仅 quiz_mock_check）

先以普通mock账号登录取得临时令牌（MOCK_USER_PASSWORD由环境传入）、后台使用工头提供的本地管理员凭证；不打印或保存认证响应。默认顺序执行，记录BASE_URL、副本名、接口日期及对应SQL结果；分页取完，不能只校验第一页。禁用用户登录应403，不将其当通用请求失败。

| 编号 | 接口/样本 | 核对 |
| --- | --- | --- |
| A01 | `/api/me`；三个根分类；禁用账号登录 | username对应正确用户，目标/阈值/头像与画像及表一致；禁用状态拒绝。原库last_active_at不因登录变化。 |
| A02 | `/api/stats/overview`、`daily?days=30`、`categories`、`weak?days=30&limit=5` | 按S08逐字段比SQL；全部正常学员核对overview默认目标，三个考试显式categoryId按分层样本；至少覆盖7画像、3考试、所有换目标用户、新人、流失、0作答（若存在）。缺某类样本标无样本，不创建。 |
| A03 | `/api/wrong-questions?sort=RECENT/MOST&page=&size=100`、`/groups` | 按S09核对total、分页顺序、全部可见错题、组数、原标签；样本含已移出再错、连对临界值及打乱题。选用现有记录。 |
| A04 | `/api/exam-records?all=true&page=&size=100`、已FINISHED的`/{id}`、`/api/stats/exams?paperId=` | S10对账；各考试、四题型、手动/超时、有未答、打乱卷、不同分数档至少各有样本；若某类不存在单列，不假造覆盖。成绩单逐题合计、统计场次/均分/最好成绩与SQL一致。 |
| A05 | `/api/practice/sessions?all=true&page=&size=100`、DAILY详情、overview.streakDays | S11；历史列表范围与当前目标/显式根/all=true分别核对，返回所有考试的历史，不通过PUT切目标制造检查条件。 |
| A06 | `/api/stats/leaderboard?period=WEEK&categoryId=` | S12，三个根各核对Top20和本人；覆盖榜首、并列、榜外本人、无当周作答（按现有样本）。 |
| A07 | `/api/admin/dashboard`、用户/题目/试卷/纠错分页列表 | S13五项、14日曲线和每卷统计逐字段一致。用户列表300学员与画像相符；查看收藏笔记、纠错/回复的真实文本，非空且无占位符未替换。 |

完整SQL一致性扫描覆盖全库；接口抽样范围、用户名数量、覆盖组合和无样本项在报告中写明，不能把部分字段/抽样通过说成300人每个接口全量通过。误差规则：计数/日期/布尔/标签精确一致，四位比率比较到0.0001，均分两位；不使用宽松百分比容忍数据错误。

## 6. 模拟器交付证据与完成标准

- [ ] **G01** 检查后端提供的 dry-run 日志：300人、每日活跃计划、预估作答15万–25万及考试场次；明确“估算”与生成后实际数不同。QA本轮不重跑生成器。
- [ ] **G02** 审阅仅种子题库/唯一管理员的前置检查、污染库拒绝、正常不开mock、执行一次后退出的测试/运行日志；记录来源commit和退出码。不能单凭结果库声称这些启动保护已实测。
- [ ] **G03** 证据表明生成调用现有业务代码、模拟时钟同时驱动作答/结算/上传/纠错等，而非直接INSERT业务表；由后端实现及测试日志佐证。学员密码只来自MOCK_USER_PASSWORD，日志/仓库不含明文。
- [ ] **G04** 重现性要求：相同JSON/seed/截止时刻的两次生成比较业务汇总及有序记录；若仅交付一次，则标“未验证重现性”。结束日由运行现在决定，换日运行不能只因结果不同就判随机种子失效；认证散列/令牌不进入报告。QA不为此重灌quiz_mock。
- [ ] **G05** 完成报告逐项写S/A/R/G编号、查询/接口总数及失败数、样本数量、异常主键、分布表、未执行项。真数据错误与软性真实感建议分列，报工头，不直接修库。
- [ ] **G06** 原库保持不变；副本核验用到的鉴权活跃时间变化/意外结算明确留痕。停止已登记的本地后端，保存退出码与进程核验；`quiz_mock_check`保留。所有待执行勾选只有得到证据后才改成完成。
