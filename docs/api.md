# 接口约定（唯一事实源）

后端、管理后台、手机 App 都按这份文件实现。改接口先改这里，再改代码。

## 0. 通用约定

- 前缀：App 用 `/api/**`；管理后台用 `/api/admin/**`（只允许 `ADMIN` 角色）。上传文件 `/api/files`，静态访问 `/uploads/**`。
- 认证：`Authorization: Bearer <JWT>`。令牌 7 天有效。除注册、登录、`GET /api/app/version` 外都要登录。
- 返回统一包一层：

```json
{ "code": "OK", "message": "ok", "data": { } }
```

  出错时 `code` 是下面的错误码，`data` 可以带细节，HTTP 状态码同时给出：

| HTTP | code | 场景 |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | 参数不合法，`data.fields` = `[{field, message}]` |
| 401 | `UNAUTHORIZED` | 没登录或令牌过期 |
| 403 | `FORBIDDEN` | 没权限、账号被禁用、看别人的数据 |
| 403 | `EXAM_IN_PROGRESS` | 有进行中的考试包含这道题，考试结束前不能查答案 |
| 404 | `NOT_FOUND` | 资源不存在或不属于自己 |
| 409 | `USERNAME_TAKEN` | 注册时用户名已存在 |
| 409 | `POOL_INSUFFICIENT` | 题不够。`data.available`（练习）或 `data.gaps`（规则组卷） |
| 409 | `EXAM_EXPIRED` | 已过截止时间，答案不再接收 |
| 409 | `CONFLICT` | 其他状态冲突，比如删除还有子分类的分类 |
| 500 | `INTERNAL_ERROR` | 服务器错误，不暴露堆栈 |

- 时间：一律 ISO-8601 UTC，带 `Z`，例如 `2026-11-02T08:30:00.000Z`。“哪一天”一律按 `Asia/Shanghai` 计算，日期字段写成 `2026-11-02`。
- 分页：请求 `?page=1&size=20`（size 最大 100）。返回 `{ "items": [], "total": 0, "page": 1, "size": 20 }`。
- 枚举用大写英文字符串。
- 写操作的返回：新建成功 HTTP 201，`data` 是新建后的完整对象；修改成功 200，`data` 是修改后的完整对象；删除成功 200，`data` 为 `null`（题目删除例外，见第 9 节）。
- 富文本：题干、选项、解析都按 Markdown 存储。接口同时返回原文和后端渲染好的 HTML（字段名加 `Html` 后缀）。代码块渲染成带内联样式的 `<pre><code>`，用等宽字体和简单关键字高亮（Java、C、Python、SQL）。HTML 只允许白名单标签，防 XSS。

### 0.1 补充约定（后端 M1 定稿）

- 注册成功返回 201；登录用户名或密码错误返回 401 `UNAUTHORIZED`（客户端在登录页不要因此跳转）；改密码时原密码错返回 400，`data.fields` 指向 `oldPassword`。
- 纠错提交返回 201，data 为 `{id, questionId, type, content, status, reply, createdAt, handledAt}`。
- `PUT /api/favorites/{questionId}` 新收藏和改笔记都返回 200，data 为收藏列表项；`note` 不传表示不改，传空串表示清空。
- 分页 `size` 超过 100 按 100，`page` 小于 1 按 1，不报错。
- 练习：`SEQUENTIAL` 必须且只能给一个分类；`RANDOM`、`TAG` 不给 `categoryIds` 时用目标考试；`DAILY` 题不足 10 道时按实际数量出（0 道 409）；`RETRY` 只取仍是已发布状态的题。
- 分类树上的题量、已做数、正确率只算已发布的题；所有统计只算 `answered = true` 的作答。
- 调小 `wrongRemoveThreshold` 不会立即批量移出，等这道题下一次在错题练习或重做中答对时再按新阈值判断。
- 请求体类型严格：数字字段只收 JSON 数字（小数不会截断成整数）、布尔字段只收 `true` / `false`、字符串字段只收 JSON 字符串（包括 `userAnswer` 数组里的每一项）。类型不对返回 400 `VALIDATION_FAILED`，`fields` 指出字段路径。查询参数不受此限。

### 0.2 补充约定（后端 M2 定稿）

- `myAttempts` 只数已结算的场次；`myBestScore` 没考过为 `null`。没有目标考试也没传 `categoryId` 时，`GET /api/exams` 列出全部上架试卷。
- 下架的试卷，须知和开考都返回 404；已经开考的那场仍可继续作答和交卷。
- 截止后才交卷、或被定时任务结算的，一律记为超时（`TIMEOUT`），`submittedAt` 等于截止时间；截止前手动交卷记为 `MANUAL`。
- 结算时每题都写作答记录，未答的 `answered = false`，不进统计、不进错题本。
- 排行榜默认按目标考试，只算正常状态的用户，并列同名次（1、1、3）。
- 连续打卡只看目标考试的每日一练，全部提交才算那天完成，日期取那一组的 `dailyDate`。
- `days` 取 1–365、`limit` 取 1–50，超出返回 400；`period` 目前只支持 `WEEK`。
- 章节统计按作答时题目所在的章节归属；薄弱点正确率相同时，样本多的排前面。

### 0.3 补充约定（后端 M3 定稿）

- 上传成功返回 201，data 为 `{url}`。
- 纠错列表项的 `type` 是纠错类型（`ANSWER_WRONG` 等），另有 `questionType` 表示题型。
- Excel 导入的题直接是已发布状态（模板要求答案和解析齐全）。来源可空，难度默认 3；判断题答案接受 `对`/`错`/`正确`/`错误`/`T`/`F`/`√`/`×`；多选答案可写 `AC` 或 `A,C`；一次最多 2000 行。
- 后台题目：草稿可以不填答案和解析；不适用当前题型的答案字段忽略；判断题传了选项也忽略；`imageUrl` 只能是 `/uploads/` 开头或 http(s) 地址；不传 `sort` 时新建题排到章节末尾。
- 删除题目：有作答记录、被试卷引用或被纠错过就归档，否则真删（同时删掉收藏）。
- 后台分类树的题量包括草稿和已发布；删除一级分类时，把它设为目标考试的用户目标会被清空。
- 试卷新建后是下架状态；固定卷只能选已发布的题；后台 `attempts` 数全部考试记录（含进行中）；有考试记录后只能改标题和描述，改别的返回 409。
- 看板：`userCount` 只数普通用户，`questionCount` 只数已发布，`papers[].attempts` 只数已结算场次，`avgScore` 保留 2 位小数。

## 1. 题目的表示

题型 `type`：`SINGLE` 单选、`MULTIPLE` 多选、`JUDGE` 判断、`FILL` 填空。

学员看到的题（没交答案时）：

```json
{
  "id": 101, "categoryId": 12, "categoryPath": "考研 408 / 数据结构 / 线性表",
  "type": "MULTIPLE", "difficulty": 2, "tags": ["链表"],
  "stem": "markdown", "stemHtml": "<p>…</p>",
  "options": [ { "key": "A", "text": "markdown", "html": "<p>…</p>" } ],
  "blankCount": 0, "imageUrl": null,
  "favorited": false, "note": null
}
```

- 判断题 `options` 固定为 `[{key:"T",text:"正确"},{key:"F",text:"错误"}]`。
- 填空题 `options` 为空数组，`blankCount` ≥ 1。
- 用户作答统一是字符串数组 `userAnswer`：单选和判断 `["A"]`、多选 `["A","C"]`（顺序无关）、填空每空一项 `["int","5"]`。

交卷或提交后附带的答案部分：

```json
{
  "correctAnswer": ["A", "C"],
  "acceptedAnswers": [["int", "Integer"], ["5"]],
  "fillOptions": { "ignoreCase": false, "ignoreSpaces": false },
  "analysis": "markdown", "analysisHtml": "<p>…</p>"
}
```

选择题和判断题用 `correctAnswer`，填空题用 `acceptedAnswers`（每空可接受的答案列表），另一个字段为 `null`。

判分规则（后端唯一实现，前端不判分）：
- 单选、判断：完全相同才对。
- 多选：选项集合完全相同才得分，少选、错选、空选都 0 分。
- 填空：每空先去掉首尾空白、统一换行；`ignoreCase` 为真时忽略大小写，`ignoreSpaces` 为真时去掉所有空白；命中该空任一可接受答案算这空对；所有空都对才得分。
- `correct` 只有全对才是 `true`。

## 2. 账号

| 方法 | 路径 | 请求 | 返回 data |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | `{username, password, nickname?}` | `{token, expiresAt, user}` |
| POST | `/api/auth/login` | `{username, password}` | 同上；账号禁用返回 403 |
| GET | `/api/me` | — | `User` |
| PUT | `/api/me` | `{nickname?, avatarUrl?, targetCategoryId?}` | `User`；`targetCategoryId` 必须是一级分类 |
| PUT | `/api/me/password` | `{oldPassword, newPassword}` | `null` |
| POST | `/api/files` | multipart `file`，图片 ≤ 2 MB（jpg/png/webp/gif） | `{url}` |
| PUT | `/api/me/settings` | `{wrongRemoveThreshold}`，1–10 的整数 | `User` |

`User` = `{id, username, nickname, avatarUrl, role: "USER"|"ADMIN", status, targetCategoryId, targetCategoryName, wrongRemoveThreshold, createdAt}`。`wrongRemoveThreshold` 是错题连续答对几次自动移出，默认 3。用户名 3–20 位字母数字下划线，密码 6–32 位。

上传返回的 `url` 是以 `/uploads/` 开头的站内路径。客户端把它拼在站点前缀后面使用：前缀 = 接口前缀去掉末尾的 `/api`。例如接口前缀是 `/quiz/api`，就显示 `/quiz/uploads/…`。部署时 `<前缀>/uploads/` 要转发到后端。

## 3. 题库分类与搜题

分类三级：1 = 考试，2 = 科目，3 = 章节。题只挂在第 3 级。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/categories?rootId=` | 分类树。不传 `rootId` 时 `data` 是数组，列出全部一级分类（`children` 为空数组，用于选目标考试）；传了时 `data` 是该考试的根节点对象，`children` 里是整棵树。每个节点：`{id, parentId, name, level, sort, questionCount, doneCount, correctRate, children}`。`doneCount` 是本人做过的不同题数，`correctRate` 是本人在该节点下的正确率，没作答时为 `null` |
| GET | `/api/questions/search?keyword=&categoryId=&page=&size=` | 按题干关键词搜，返回 `{id, categoryId, categoryPath, type, stemPreview}`，`stemPreview` 是去掉格式的前 80 个字。`keyword` 必填，去掉首尾空白后 1–50 个字，缺失或为空返回 400 `VALIDATION_FAILED`（`fields` 指向 `keyword`） |
| GET | `/api/questions/{id}` | 题目 + 答案 + 解析。若本人有进行中的考试包含这道题，返回 403 `EXAM_IN_PROGRESS` |
| POST | `/api/questions/{id}/feedback` | 纠错 `{type: "ANSWER_WRONG"|"STEM_WRONG"|"ANALYSIS_UNCLEAR"|"OTHER", content}`，content 1–500 字 |

只返回 `PUBLISHED` 状态的题。

## 4. 练习

### 开始一次练习

`POST /api/practice/sessions`

```json
{
  "mode": "RANDOM",
  "categoryIds": [12, 13],
  "count": 20,
  "types": ["SINGLE", "MULTIPLE"],
  "scope": "ALL",
  "reciteMode": false,
  "acceptFewer": false,
  "shuffleOptions": false
}
```

- `mode`：`RANDOM` 随机、`SEQUENTIAL` 顺序、`WRONG` 错题、`FAVORITE` 收藏、`DAILY` 每日一练、`TAG` 按标签（另带 `tag`）、`RETRY` 重做某次的错题。
- `RETRY` 另带 `fromSessionId`（某次练习）或 `fromExamRecordId`（某次已结算的考试），二选一，都必须是本人的。题目 = 那一次里提交了但没全对的题，按原顺序；考试里未答的题不算。忽略 `categoryIds`、`count`、`types`、`scope`。没有错题时返回 409 `POOL_INSUFFICIENT`，`available` 为 0。这种练习里答对时，按错题练习的规则累加 `correctStreak`。
- `categoryIds` 可以是任何层级，包含其所有子章节。`DAILY` 忽略它，用本人的目标考试。
- `count`：10 / 20 / 50，`WRONG`、`FAVORITE` 可省略（默认全部，最多 100）。`SEQUENTIAL` 忽略 count，取整章。
- `scope`：`ALL`、`UNDONE`（本人从没正式提交过的题，背题和浏览不算）、`WRONG_EVER`（本人提交过且没全对过的题，移出错题本后仍算）。
- 抽题：服务器取符合条件的题，去重后随机打乱取前 N 道；同一次练习不重复。题目列表在开始时固定。
- 题不够时：若 `acceptFewer` 为假，返回 409 `POOL_INSUFFICIENT`，`data.available` 为实际可用数；为真则用实际数量开始。0 道题始终返回 409。
- `SEQUENTIAL`：同一章节已有未完成的顺序练习时，直接返回那一次（200）。
- `DAILY`：每人每个目标考试每天一组 10 道，优先没做过的题，不够再补其他题；当天已有就返回那一组（200）。
- `reciteMode` 为真时是背题模式：题目直接带答案，不能提交，不计入统计和错题本。
- `shuffleOptions`（默认 `false`）为真时，这一次练习里每道单选、多选题的选项随机排一次并固定，规则见第 6.1 节。对所有模式有效（含背题）；返回已有练习时（同章节未完成的 `SEQUENTIAL`、当天的 `DAILY`）沿用那一次创建时的设置。
- 新建返回 201，返回已有返回 200，data 都是 `PracticeSession`。

`PracticeSession`：

```json
{
  "id": 9, "mode": "RANDOM", "reciteMode": false, "shuffleOptions": false, "status": "IN_PROGRESS",
  "questionCount": 20, "answeredCount": 3, "correctCount": 2, "currentIndex": 3,
  "startedAt": "…", "completedAt": null, "dailyDate": null,
  "questions": [ { "...题目字段": "...", "optionMapping": null, "submitted": true, "userAnswer": ["A"], "correct": true, "correctAnswer": ["A"], "acceptedAnswers": null, "analysisHtml": "…" } ]
}
```

没提交的题不带答案字段（背题模式除外）。

### 其他练习接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/practice/sessions/{id}` | 取一次练习，只能取自己的 |
| GET | `/api/practice/sessions/current` | 最近一次未完成的非每日练习，没有时 data 为 `null`，用于首页“继续练习” |
| GET | `/api/practice/sessions?page=&size=` | 练习记录 `{id, mode, questionCount, answeredCount, correctCount, startedAt, completedAt}` |
| POST | `/api/practice/sessions/{id}/answers` | `{questionId, userAnswer, timeSpentSec}`。返回 `{questionId, correct, userAnswer, correctAnswer, acceptedAnswers, analysisHtml, alreadySubmitted, optionMapping}`。每题只能提交一次，重复提交返回第一次的结果且 `alreadySubmitted=true`。背题模式返回 409 `CONFLICT` |
| PUT | `/api/practice/sessions/{id}/progress` | `{currentIndex}`，保存看到第几题 |
| POST | `/api/practice/sessions/{id}/finish` | 结束，返回 `{durationSec, answeredCount, correctCount, correctRate, wrongQuestionIds}`。全部题都提交后自动完成；每日一练只有全部提交才写 `completedAt` |

提交答案时同步做：写作答记录；答错进错题本或累加错误次数；若这次练习是 `WRONG` 或 `RETRY` 模式且答对，`correctStreak + 1`，达到本人的 `wrongRemoveThreshold`（默认 3）移出错题本；任何模式答错都把 `correctStreak` 清零，已移出的题重新放回错题本。

## 5. 错题本和收藏

错题本和收藏的列表、分组、计数都默认只看本人目标考试下的题（没有目标考试时看全部），和首页、统计、考试列表口径一致。传 `categoryId`（任意层级）时只看该分类下的题。用 `WRONG`、`FAVORITE` 模式开练习时，不传 `categoryIds` 也按同一范围取题，保证“列表里看到的题”和“重练全部”拿到的题是同一批。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/wrong-questions?categoryId=&sort=RECENT\|MOST&page=&size=` | `{questionId, categoryId, categoryPath, type, stemPreview, wrongCount, correctStreak, lastWrongAnswer, lastWrongAt}` |
| GET | `/api/wrong-questions/groups` | 按章节分组计数 `[{categoryId, categoryPath, count}]` |
| DELETE | `/api/wrong-questions/{questionId}` | 手动移出，只改状态不删历史 |
| GET | `/api/favorites?categoryId=&page=&size=` | `{questionId, categoryPath, type, stemPreview, note, createdAt}` |
| PUT | `/api/favorites/{questionId}` | 收藏或修改笔记 `{note?}`（≤ 200 字） |
| DELETE | `/api/favorites/{questionId}` | 取消收藏，笔记一起删除 |

## 6. 模拟考试

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/exams?categoryId=` | 已上架试卷 `{paperId, title, categoryId, mode, questionCount, totalScore, passScore, durationMin, myAttempts, myBestScore, inProgressRecordId, shuffleOptions}`，默认按本人目标考试过滤；`shuffleOptions` 表示开考时是否打乱选项 |
| GET | `/api/exams/{paperId}` | 考前须知：上面字段 + `description` + `scoringRules`（判分规则说明文字） |
| POST | `/api/exams/{paperId}/start` | 开考。已有进行中的同卷考试则返回它（200），否则新建（201）。规则卷题不够返回 409 `POOL_INSUFFICIENT`，`data.gaps = [{categoryId, categoryPath, type, need, available}]` |
| GET | `/api/exam-records/{id}` | 进行中返回 `ExamSheet`，已结算返回 `ExamResult` |
| PUT | `/api/exam-records/{id}/answers/{questionId}` | `{userAnswer, version, marked}`。`version` 由客户端每题递增。返回 `{accepted, version}`：版本更大时保存并返回 `{accepted:true, version:<本次版本>}`；不更大时不保存，返回 `{accepted:false, version:<已存版本>}`。截止后返回 409 `EXAM_EXPIRED`，不保存 |
| POST | `/api/exam-records/{id}/submit` | 交卷并结算，可重复调用，只结算一次，返回 `ExamResult` |
| GET | `/api/exam-records?paperId=&page=&size=` | 考试记录 `{recordId, paperId, title, status, score, totalScore, passed, startedAt, submittedAt}` |

`ExamSheet`（不含任何答案和解析）：

```json
{
  "recordId": 5, "paperId": 2, "title": "数据结构模拟卷一", "status": "IN_PROGRESS", "shuffleOptions": false,
  "startedAt": "…", "deadlineAt": "…", "serverTime": "…",
  "durationMin": 60, "totalScore": 100, "passScore": 60,
  "questions": [ { "index": 1, "questionId": 101, "type": "SINGLE", "stemHtml": "…", "options": [], "blankCount": 0, "imageUrl": null, "score": 2, "optionMapping": null } ],
  "answers": [ { "questionId": 101, "userAnswer": ["A"], "version": 3, "marked": false, "savedAt": "…" } ]
}
```

`ExamResult`：

```json
{
  "recordId": 5, "paperId": 2, "title": "…", "status": "FINISHED",
  "score": 74, "totalScore": 100, "passScore": 60, "passed": true,
  "startedAt": "…", "submittedAt": "…", "durationSec": 2710,
  "correctCount": 30, "wrongCount": 8, "unansweredCount": 2,
  "byType": [ { "type": "SINGLE", "score": 40, "total": 50, "rate": 0.8 } ],
  "byCategory": [ { "categoryId": 12, "categoryPath": "…", "score": 10, "total": 14, "rate": 0.71 } ],
  "items": [ { "index": 1, "questionId": 101, "type": "SINGLE", "stemHtml": "…", "options": [], "blankCount": 0, "imageUrl": null, "userAnswer": ["B"], "correctAnswer": ["A"], "acceptedAnswers": null, "correct": false, "score": 0, "maxScore": 2, "analysisHtml": "…", "optionMapping": null } ]
}
```

- 开考时服务器把题目、标准答案、分值存成快照；之后改题不影响这场考试和成绩单。
- 截止时间 = 开始时间 + 时长。服务器每 30 秒结算一次已过截止时间的考试；访问记录时发现已过期也立即结算。只按截止前收到的答案算分，未答 0 分。
- 结算时：写作答记录（来源为考试）；答错的题进错题本；未答的题不进错题本。
- 进行中的考试，`GET /api/questions/{id}` 对其中的题返回 403。
- 试卷 `shuffleOptions` 为真时，开考时每道单选、多选题的选项顺序随机排一次，存进这场考试的快照；之后改试卷设置不影响已开考的场次。规则见第 6.1 节。

### 6.1 选项打乱（练习和考试通用）

练习的 `shuffleOptions` 或试卷的 `shuffleOptions` 为真时，这一场里的每道单选、多选题按随机顺序显示；判断题、填空题不打乱。

- “某一场里的题”都带 `optionMapping`（练习的 `questions[]` 和提交返回、`ExamSheet.questions[]`、`ExamResult.items[]`）：新标签 → 原标签，例如 `{"A":"C","B":"A","C":"D","D":"B"}`。没打乱的题（判断、填空，或这一场没开打乱）为 `null`。
- 打乱的题里，和选项字母有关的字段都用**新标签**：`options[].key` 依次是 A、B、C……，`text` / `html` 是对应原选项的内容；客户端提交、保存的 `userAnswer` 用新标签；返回的 `userAnswer`、`correctAnswer` 用新标签。
- `analysis` / `analysisHtml` **原文不变**，里面的字母是原标签；前端借 `optionMapping` 在选项旁注明“原选项 C”。
- 后端把新标签换回原标签再判分、存库，所以作答记录、错题本里都是原标签。错题本的 `lastWrongAnswer` 用原标签（错题本展示的是原题）。
- 题目详情、搜题、错题本、收藏、后台题目都是“题本身”，不打乱，没有 `optionMapping`。
- 练习用题目的当前内容：开练习后题目的选项个数被改了，这道题在这一场里按原顺序显示（`optionMapping` 为 `null`）。考试用快照，不受影响。
- 客户端传了不存在的新标签照常判错。

## 7. 统计

都按本人、默认按目标考试（可传 `categoryId` 指定一级分类）。“作答”指正式提交且已判分的作答，背题和浏览不算。

| 方法 | 路径 | 返回 data |
| --- | --- | --- |
| GET | `/api/stats/overview` | `{answerCount, correctCount, correctRate, studyDays, streakDays, todayAnswerCount, examCount}` |
| GET | `/api/stats/daily?days=30` | `[{date, answerCount, correctCount, correctRate}]`，没作答的日子也给出，`correctRate` 为 `null` |
| GET | `/api/stats/categories` | 每个章节 `[{categoryId, categoryPath, questionCount, doneCount, answerCount, correctRate}]` |
| GET | `/api/stats/weak?days=30&limit=5` | `[{categoryId, categoryPath, sampleCount, correctRate}]`：近 N 天每题只看最后一次作答，做过至少 5 道不同题的章节，按正确率升序 |
| GET | `/api/stats/exams?paperId=` | `[{recordId, paperId, title, score, totalScore, passScore, rate, submittedAt}]` |
| GET | `/api/stats/leaderboard?period=WEEK` | `{items: [{rank, nickname, avatarUrl, answerCount}], me: {rank, answerCount}}`，本周（周一 00:00 北京时间起）作答次数前 20 |

口径：
- `correctRate` = 全对条数 ÷ 作答条数，分母为 0 时为 `null`。
- `studyDays` = 有作答的日期个数。
- `streakDays` = 连续完成每日一练的天数；今天没完成时算到昨天为止；中断即归零重算。

## 8. App 版本

`GET /api/app/version?platform=android|ios`（不用登录）→ `{platform, latestVersion, minVersion, downloadUrl, notes}`，值来自配置 `app.release.<platform>.*`。

## 9. 管理后台（`/api/admin/**`）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/categories` | 全部分类树，节点带 `questionCount` |
| POST | `/api/admin/categories` | `{parentId, name, sort}`，`parentId` 为空即一级；最多 3 级 |
| PUT | `/api/admin/categories/{id}` | `{name, sort}` |
| DELETE | `/api/admin/categories/{id}` | 有子分类或有题时 409 `CONFLICT` |
| GET | `/api/admin/questions?categoryId=&type=&status=&keyword=&page=&size=` | 题目列表，带 `status`、`version`、`answerCount`、`correctRate` |
| GET | `/api/admin/questions/{id}` | 完整题目（含答案、解析、`source`、`status`、`version`） |
| POST | `/api/admin/questions` | 新建。`status=PUBLISHED` 时必须有正确答案和解析，否则 400 |
| PUT | `/api/admin/questions/{id}` | 修改，`version + 1` |
| DELETE | `/api/admin/questions/{id}` | 没人做过就真删；否则改成 `ARCHIVED` |
| POST | `/api/admin/questions/preview` | `{markdown}` → `{html}`，录题时预览 |
| GET | `/api/admin/questions/import/template` | 下载 Excel 模板 |
| POST | `/api/admin/questions/import` | multipart `file`（xlsx，≤ 5 MB）→ `{total, success, failed: [{row, reason}]}`；整表先校验，部分行错时只导入正确的行 |
| GET | `/api/admin/papers?categoryId=&status=&page=&size=` | 试卷列表 |
| GET | `/api/admin/papers/{id}` | 详情：固定卷带 `questions: [{questionId, score, sort, stemPreview, type}]`，规则卷带 `rules: [{categoryId, type, count, score}]` |
| POST | `/api/admin/papers` | `{title, categoryId, description, mode: "FIXED"|"RULE", durationMin, passScore, questions?, rules?, shuffleOptions?}`，`shuffleOptions` 默认 `false`；总分由题目或规则自动算出。`categoryId` 是一级分类（考试）。规则卷每条规则的 `categoryId` 可以是 1–3 级任意分类，但必须属于这份试卷的考试，抽题时包含它下面的全部章节；同一道题不会被两条规则重复抽到。固定卷的题也必须属于这份试卷的考试 |
| PUT | `/api/admin/papers/{id}` | 修改；有人考过的试卷只能改标题、描述、`shuffleOptions`（只影响之后开考的场次）、上下架 |
| PUT | `/api/admin/papers/{id}/status` | `{status: "ONLINE"|"OFFLINE"}`。上架时校验：固定卷至少 1 道题且 `passScore ≤ totalScore`；规则卷题量够，不够返回 409 带 `gaps` |
| DELETE | `/api/admin/papers/{id}` | 没人考过才能删 |
| GET | `/api/admin/users?keyword=&page=&size=` | 用户列表，带 `answerCount`、`lastActiveAt` |
| PUT | `/api/admin/users/{id}/status` | `{status: "ACTIVE"|"DISABLED"}`，不能禁用自己 |
| GET | `/api/admin/feedback?status=&page=&size=` | 纠错列表，带题目预览和提交人 |
| PUT | `/api/admin/feedback/{id}` | `{status: "RESOLVED"|"REJECTED", reply?}` |
| GET | `/api/admin/dashboard` | `{userCount, questionCount, paperCount, todayAnswerCount, pendingFeedbackCount, daily: [{date, answerCount, activeUsers}], papers: [{paperId, title, attempts, avgScore, passRate}]}`，daily 为近 14 天 |

管理员账号：启动时如果没有管理员，就用配置 `app.admin.username` / `app.admin.password` 创建一个。

### 9.1 管理端对象

分类节点 `AdminCategory`：`{id, parentId, name, level, sort, questionCount, children}`。新建、修改返回单个节点（`children` 为空数组）。

题目请求体（`POST` 和 `PUT /api/admin/questions`）：

```json
{
  "categoryId": 12,
  "type": "MULTIPLE",
  "stem": "markdown",
  "options": [ { "key": "A", "text": "markdown" }, { "key": "B", "text": "markdown" } ],
  "correctAnswer": ["A", "C"],
  "acceptedAnswers": null,
  "fillOptions": null,
  "analysis": "markdown",
  "difficulty": 3,
  "tags": ["链表"],
  "imageUrl": null,
  "source": "原创",
  "sort": 0,
  "status": "PUBLISHED"
}
```

- 单选、多选：`options` 2–6 项，`key` 必须按顺序是 A、B、C……；`correctAnswer` 单选 1 个、多选至少 2 个，都必须是已有的 key。
- 判断：`options` 不传或传空数组，后端固定生成 T/F；`correctAnswer` 为 `["T"]` 或 `["F"]`。
- 填空：`options` 不传；`acceptedAnswers` 每空一个非空数组；`fillOptions` 不传时默认 `{ignoreCase:false, ignoreSpaces:false}`；`blankCount` 由 `acceptedAnswers` 的长度决定。
- 不适用的答案字段传 `null` 或不传。`categoryId` 必须是三级分类。`status` 只能是 `DRAFT` 或 `PUBLISHED`；归档只能通过删除接口产生。

题目返回 `AdminQuestion`：请求体全部字段，加上 `id`、`categoryPath`、`stemHtml`、每个选项的 `html`、`analysisHtml`、`blankCount`、`version`、`answerCount`、`correctRate`、`createdAt`、`updatedAt`。列表接口返回同样结构，但不含 `stemHtml`、`analysisHtml`，另加 `stemPreview`。

题目删除返回 `{ "deleted": true, "archived": false }`：没人做过时真删（`deleted:true`），有人做过时改为归档（`deleted:false, archived:true`）。

试卷返回 `AdminPaper`：`{id, title, categoryId, categoryName, description, mode, durationMin, totalScore, passScore, status, shuffleOptions, questionCount, attempts, questions, rules, createdAt, updatedAt}`。固定卷 `questions` 为 `[{questionId, score, sort, type, stemPreview}]`、`rules` 为 `null`；规则卷反之，`rules` 为 `[{categoryId, categoryPath, type, count, score}]`。`totalScore` 由后端算出，请求里传了也忽略。新建、修改、改状态都返回 `AdminPaper`。

用户列表项 `AdminUser`：`{id, username, nickname, role, status, answerCount, lastActiveAt, createdAt}`。纠错列表项：`{id, questionId, stemPreview, categoryPath, type, content, status, reply, username, createdAt, handledAt}`。

## 10. Excel 导入模板

表头（第一行）：`分类路径 | 题型 | 题干 | 选项A | 选项B | 选项C | 选项D | 选项E | 选项F | 答案 | 解析 | 难度 | 标签 | 来源`

- 分类路径：`考试/科目/章节`，三级必须已存在。
- 题型：`单选 / 多选 / 判断 / 填空`。
- 答案：单选 `A`；多选 `AC`；判断 `对` 或 `错`；填空每空用 `||` 分隔，同一空多个可接受答案用 `|` 分隔，例如 `int|Integer||5`。
- 难度 1–5，默认 3；标签用逗号分隔。
