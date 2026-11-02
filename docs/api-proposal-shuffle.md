# 提案：选项顺序打乱（第三批 · 后端部分）

状态：**已确认**（D2-OK，第 5 节 4 点均按建议），已写入 `docs/api.md` 第 4、6、6.1、9 节并实现。以 api.md 为准。

## 1. 目标

练习和考试里，单选题、多选题的选项可以按场次随机排一次，减少“记住 C 是答案”的情况。判断题、填空题不打乱。
打乱后客户端看到、提交的都是新标签 A、B、C……；后端换回原选项再判分；解析原文不改（里面写的还是原来的字母），
所以返回映射，前端在选项旁注明“原选项 C”。

## 2. 接口变化

### 2.1 开练习

`POST /api/practice/sessions` 请求体加：

| 字段 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `shuffleOptions` | boolean | `false` | 为真时，这一次练习里每道单选、多选题的选项随机排一次，整场固定 |

- 对所有模式都有效（含背题模式）。返回已有练习的情况（同章节未完成的 `SEQUENTIAL`、当天的 `DAILY`）沿用那一次创建时的设置，本次请求的值不起作用。
- `PracticeSession` 加 `shuffleOptions: boolean`，表示这一次练习是否打乱。

### 2.2 考试

- 试卷加字段 `shuffleOptions: boolean`（默认 `false`）：
  - `POST` / `PUT /api/admin/papers` 请求体可传；`AdminPaper` 返回它。
  - `GET /api/exams`、`GET /api/exams/{paperId}` 的试卷对象返回它，考前须知可以提示“选项顺序随机”。
- 开考时如果试卷 `shuffleOptions` 为真，每道单选、多选题的选项顺序随机排一次，存进这场考试的快照。之后改试卷设置不影响已开考的场次。
- `ExamSheet` 加 `shuffleOptions: boolean`。

### 2.3 题目对象里的映射

凡是“某一场里的题”都加一个字段（练习的 `questions[]`、`ExamSheet.questions[]`、`ExamResult.items[]`、练习提交的返回）：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `optionMapping` | object 或 `null` | 新标签 → 原标签，例如 `{"A":"C","B":"A","C":"D","D":"B"}`。没打乱的题（判断、填空，或这一场没开打乱）为 `null` |

打乱的题里，所有和选项字母有关的字段都用**新标签**：

| 字段 | 打乱后 |
| --- | --- |
| `options[].key` | 依次是 A、B、C……；`text` / `html` 是对应原选项的内容 |
| 提交 / 保存的 `userAnswer` | 客户端按新标签传 |
| 返回的 `userAnswer`、`correctAnswer` | 新标签 |
| `analysis` / `analysisHtml` | **原文不变**，其中的字母是原标签；前端借 `optionMapping` 注明 |

示例（原题选项 A=`n - i`、B=`n - i + 1`、C=`i`、D=`n`，正确答案原 A）：

```json
{
  "id": 101, "type": "SINGLE",
  "options": [
    { "key": "A", "text": "n", "html": "<p>n</p>" },
    { "key": "B", "text": "n - i", "html": "<p>n - i</p>" },
    { "key": "C", "text": "i", "html": "<p>i</p>" },
    { "key": "D", "text": "n - i + 1", "html": "<p>n - i + 1</p>" }
  ],
  "optionMapping": { "A": "D", "B": "A", "C": "C", "D": "B" },
  "submitted": true, "userAnswer": ["B"], "correct": true, "correctAnswer": ["B"],
  "analysisHtml": "<p>…共 n - i 个，所以选 A。</p>"
}
```

前端在 B 旁显示“原选项 A”，用户就能对上解析里的“选 A”。

### 2.4 不受影响的地方

- `GET /api/questions/{id}`（题目详情）、搜题、错题本、收藏、后台题目：都是“题本身”，不打乱，`optionMapping` 不出现。
- 错题本的 `lastWrongAnswer` 用**原标签**（错题本列表展示的是原题）。
- 统计不看选项字母，不受影响。
- `shuffleOptions` 为 `false` 时，所有返回和现在完全一样，只多一个值为 `null` 的 `optionMapping` 和值为 `false` 的 `shuffleOptions`。

## 3. 后端实现要点

- **库里只存原标签**：练习提交、考试保存答案时，先把客户端的新标签换回原标签再存（`answer_record.user_answer`、`exam_answer.user_answer`、错题本），判分也用原标签。返回给客户端时再换成新标签。这样同一道题不管在哪一场、打没打乱，库里的作答格式都一样。
- **顺序固定在场次上**：
  - 练习：新列 `practice_session.option_orders TEXT`（JSON，`{"<questionId>": ["C","A","D","B"]}`，数组是“按显示顺序排的原标签”），不打乱时为 `NULL`。
  - 考试：快照里每道题加 `optionOrder`（同上格式，不打乱时为 `null`），不加列。
  - 试卷：新列 `exam_paper.shuffle_options BOOLEAN NOT NULL DEFAULT FALSE`。
  - 迁移脚本 `V3__shuffle_options.sql`，只加列，H2 和 MySQL 都能跑。
- 练习用的是题目的当前内容：如果开练习后管理员改了这道题、选项个数变了，存下的顺序对不上，这道题在这一场里按原顺序显示（`optionMapping` 为 `null`），不报错。考试用快照，不会出现这种情况。
- 客户端传了不存在的新标签（比如只有 4 个选项却传 `E`）照常判错，和现在一样。
- 随机用 `ThreadLocalRandom` 均匀打乱；只有 1 种排法的情况不存在（选择题至少 2 个选项），但可能“打乱后正好和原顺序一样”，这是正常的。

## 4. 测试计划

单元测试（映射本身）：
- 新标签 ↔ 原标签双向换算正确；多选的集合换算正确；不存在的标签换成一个判不对的值。
- 同一场里反复取同一道题，顺序不变；两场之间顺序独立。
- 判分：用新标签提交正确答案判对，提交“原标签的正确字母”（打乱后它指向别的选项）判错。
- 判断、填空不打乱；`shuffleOptions=false` 时返回和改动前一致（现有测试全部原样通过）。

接口测试：
- 开练习 `shuffleOptions=true`：选项按新标签返回、`optionMapping` 和选项内容对得上；提交新标签判分正确；返回的 `correctAnswer` 是新标签；库里 `answer_record.user_answer` 是原标签；错题本 `lastWrongAnswer` 是原标签。
- 复用的顺序练习 / 每日一练沿用原设置。
- 开考：试卷 `shuffleOptions=true` 时答卷选项打乱、保存答案按新标签回显、结算正确、成绩单 `userAnswer` / `correctAnswer` 是新标签并带 `optionMapping`；试卷改回 `false` 后已开考的那场不变。
- 后台建卷 / 改卷可设 `shuffleOptions`，`AdminPaper` 返回它。

## 5. 请工头确认的点

1. 字段名 `shuffleOptions`、`optionMapping`（新标签 → 原标签）是否可以。
2. 错题本 `lastWrongAnswer` 用原标签（因为错题本展示的是原题）——前端需要知道这一点。
3. 有人考过的试卷，**是否允许改 `shuffleOptions`**？它不影响判分和总分，我建议允许（只影响之后开考的场次）；如果要严格按“只能改标题、描述、上下架”，我就在已有人考过时拒绝修改它（409）。
4. 示例题库的试卷要不要支持 `"shuffleOptions": true`（`docs/seed-format.md` 归产品经理 / 工头）。我的建议是导入时支持这个可选字段，不传为 `false`；格式文档由你们决定是否补。
