"""在服务器上跑：同 docs/performance.md 的 8 个接口，经 nginx（127.0.0.1:18090）打到后端。
用真实题库和一个新建的 perf_ 账号；每个接口预热 10 次、计时 50 次。
中位数 = 第 25、26 个的平均；95 分位 = 最近秩第 48 个。"""
import http.client, json, math, secrets, sys, time

HOST, PORT, BASE = "127.0.0.1", 18090, "/quiz/api"
ROOT_ID, RULE_PAPER = int(sys.argv[1]), int(sys.argv[2])
WARM, N = 10, 50
conn = http.client.HTTPConnection(HOST, PORT, timeout=30)
token = None


def call(method, path, body=None, ok=(200, 201)):
    global conn
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = json.dumps(body).encode() if body is not None else None
    t0 = time.perf_counter()
    try:
        conn.request(method, BASE + path, body=data, headers=headers)
        resp = conn.getresponse()
        raw = resp.read()
    except (http.client.HTTPException, ConnectionError):
        conn.close()
        conn = http.client.HTTPConnection(HOST, PORT, timeout=30)
        conn.request(method, BASE + path, body=data, headers=headers)
        resp = conn.getresponse()
        raw = resp.read()
    ms = (time.perf_counter() - t0) * 1000
    if resp.status not in ok:
        raise SystemExit(f"{method} {path} -> {resp.status} {raw[:300]!r}")
    return ms, json.loads(raw)["data"] if raw else None


def answer_for(q):
    t = q["type"]
    if t == "SINGLE":
        return ["A"]
    if t == "MULTIPLE":
        return ["A", "B"]
    if t == "JUDGE":
        return ["T"]
    return ["x"] * max(1, q.get("blankCount") or 1)


def stats(xs):
    s = sorted(xs[WARM:])
    assert len(s) == N
    return (s[24] + s[25]) / 2, s[math.ceil(0.95 * N) - 1], s[-1]


results = []
name = "perf_" + secrets.token_hex(4)
_, auth = call("POST", "/auth/register", {"username": name, "password": secrets.token_urlsafe(12)})
token = auth["token"]
call("PUT", "/me", {"targetCategoryId": ROOT_ID})

# 1 分类树
xs = [call("GET", f"/categories?rootId={ROOT_ID}")[0] for _ in range(WARM + N)]
results.append(("分类树（带本人进度）", "GET /api/categories?rootId=", stats(xs)))

# 2 开随机练习 20 题
xs = [call("POST", "/practice/sessions", {"mode": "RANDOM", "categoryIds": [ROOT_ID], "count": 20})[0]
      for _ in range(WARM + N)]
results.append(("开随机练习（20 题）", "POST /api/practice/sessions", stats(xs)))

# 3 提交答案：在 50 题的练习里逐题提交
xs = []
while len(xs) < WARM + N:
    _, s = call("POST", "/practice/sessions", {"mode": "RANDOM", "categoryIds": [ROOT_ID], "count": 50})
    for q in s["questions"]:
        if len(xs) >= WARM + N:
            break
        ms, _ = call("POST", f"/practice/sessions/{s['id']}/answers",
                     {"questionId": q["id"], "userAnswer": answer_for(q), "timeSpentSec": 5})
        xs.append(ms)
results.append(("提交答案（含判分、错题本）", "POST /api/practice/sessions/{id}/answers", stats(xs)))

# 4 开考 + 5 交卷：规则卷，每场先把题全部答完（不计时）再交卷
starts, submits, saved = [], [], 0
for _ in range(WARM + N):
    ms, sheet = call("POST", f"/exams/{RULE_PAPER}/start")
    starts.append(ms)
    for q in sheet["questions"]:
        call("PUT", f"/exam-records/{sheet['recordId']}/answers/{q['questionId']}",
             {"userAnswer": answer_for(q), "version": 1, "marked": False})
        saved += 1
    ms, result = call("POST", f"/exam-records/{sheet['recordId']}/submit")
    submits.append(ms)
qn = len(sheet["questions"])
results.append((f"开考（规则卷 {qn} 题抽题 + 快照）", "POST /api/exams/{paperId}/start", stats(starts)))
results.append((f"交卷结算（{qn} 题判分、作答记录、错题本）", "POST /api/exam-records/{id}/submit", stats(submits)))

# 6 统计：此时这个账号已有练习 + 考试的作答
_, ov = call("GET", "/stats/overview")
for label, path in [("统计总览", "/stats/overview"), ("统计按日（30 天）", "/stats/daily?days=30"),
                    ("薄弱点（30 天）", "/stats/weak?days=30")]:
    xs = [call("GET", path)[0] for _ in range(WARM + N)]
    results.append((label, "GET /api" + path, stats(xs)))

print(f"账号 {name}；统计时该账号作答 {ov['answerCount']} 条；考试 {WARM + N} 场，每场 {qn} 题")
print("| 接口 | 请求 | 中位数 (ms) | 95 分位 (ms) | 最大 (ms) | ≤ 500 ms |")
print("| --- | --- | ---: | ---: | ---: | --- |")
for label, req, (med, p95, mx) in results:
    print(f"| {label} | `{req}` | {med:.1f} | {p95:.1f} | {mx:.1f} | {'是' if p95 <= 500 else '否'} |")
