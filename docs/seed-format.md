# 示例题库文件格式

位置：`backend/src/main/resources/seed/`。后端启动时，如果 `app.seed.enabled=true` 且分类表为空，就按文件名顺序读入全部 `*.json`。

题目必须是自己原创的，不能复制任何商业题库或真题原文。

## 文件结构

每个文件是一个对象，三部分都可以省略：

```json
{
  "categories": [
    { "key": "cs408", "name": "计算机考研 408", "children": [
      { "key": "ds", "name": "数据结构", "children": [
        { "key": "ds-linear", "name": "线性表" }
      ] }
    ] }
  ],
  "questions": [
    {
      "category": "ds-linear",
      "type": "SINGLE",
      "stem": "在长度为 n 的顺序表中删除第 i 个元素，需要移动多少个元素？",
      "options": ["n - i", "n - i + 1", "i", "n"],
      "answer": ["A"],
      "analysis": "删除第 i 个元素后，第 i+1 到第 n 个元素各前移一位，共 n - i 个。",
      "difficulty": 2,
      "tags": ["顺序表"]
    },
    {
      "category": "java-basic",
      "type": "FILL",
      "stem": "下面代码的输出是 ____。\n\n```java\nint x = 5;\nSystem.out.println(x++ + ++x);\n```",
      "answer": [["12"]],
      "fillOptions": { "ignoreCase": false, "ignoreSpaces": true },
      "analysis": "x++ 先取 5 再自增到 6，++x 先自增到 7 再取 7，5 + 7 = 12。",
      "difficulty": 3,
      "tags": ["自增运算", "看代码写输出"]
    }
  ],
  "papers": [
    {
      "title": "数据结构模拟卷一",
      "category": "cs408",
      "mode": "RULE",
      "durationMin": 45,
      "passScore": 60,
      "description": "覆盖线性表、树、图、查找、排序。",
      "rules": [
        { "category": "ds", "type": "SINGLE", "count": 20, "score": 3 },
        { "category": "ds", "type": "MULTIPLE", "count": 5, "score": 4 },
        { "category": "ds", "type": "JUDGE", "count": 10, "score": 2 }
      ]
    }
  ]
}
```

## 规则

- `key` 全局唯一，只用小写字母、数字和连字符。题目和试卷用 `key` 引用分类。分类可以在一个文件里定义、在别的文件里引用，但定义的文件名要排在前面。
- 题目只能挂在三级分类（章节）上。
- `options`：单选、多选 2–6 项，按顺序对应 A、B、C……；判断题和填空题不写 `options`。
- `answer`：单选 `["B"]`；多选 `["A","C"]`，至少两个；判断 `["T"]` 或 `["F"]`；填空每空一个数组，列出所有可接受的写法。
- 题干、选项、解析都可以用 Markdown；代码用带语言的代码块（```java、```c、```python、```sql）。
- 看代码写输出的题，代码必须真的跑过，答案就是实际输出。
- 每道题都要有解析，说清为什么对、常见错在哪。
- 试卷 `mode` 为 `FIXED` 时用 `questions: [{"ref": "<题目 ref>", "score": 2}]`，题目要带 `"ref"` 字段；为 `RULE` 时用 `rules`。规则卷要求的题量不能超过对应分类里该题型的题数。
